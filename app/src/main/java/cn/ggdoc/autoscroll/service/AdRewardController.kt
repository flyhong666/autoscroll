package cn.ggdoc.autoscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.util.Log
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.task.AdRewardTask
import cn.ggdoc.autoscroll.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 看广告得金币（激励视频）任务控制器。
 *
 * 从 [AutoScrollAccessibilityService] 抽离：
 *  - 周期性尝试（scheduleJob）
 *  - 进入激励视频后的观看期轮询（watchJob）
 *  - 风控：保护策略（低电量/非 Wi-Fi/时间窗口）生效时跳过本轮
 *
 * 调度由内部 [CoroutineScope]（主线程）驱动：schedule() 启动一个
 * `delay(interval) → tryEnter()` 协程；watchJob 用 `while(isActive)` 轮询。
 * stop() 取消全部协程。
 */
class AdRewardController(
    private val service: AccessibilityService,
    private val serviceProvider: ServiceFace
) {

    private val context: Context get() = service

    interface ServiceFace {
        val TAG: String get() = AutoScrollAccessibilityService.TAG
        val isScrolling: Boolean
        val adRewardEnabled: Boolean
        val adRewardMinutes: Int
        var adRewardCount: Int
        var isWatchingAdReward: Boolean
        fun isBlockedByPolicy(): Boolean
        fun tryAdBlockNow(): Int
        fun sendTaskEvent(type: String, msg: String)
        fun broadcastState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + AppLog.coroutineExceptionHandler)
    private var scheduleJob: Job? = null
    private var watchJob: Job? = null

    /** 进入观看期的时刻（elapsedRealtime），用于「到账确认」的最短观看保护 */
    private var watchStartElapsed = 0L

    /** 「到账确认」最短观看时长：小于此时长收到的到账 Toast 视为误判，不提前结束 */
    private companion object {
        const val MIN_WATCH_BEFORE_RECEIVED_MS = 15_000L

        /** 点掉「关闭/领取」按钮后到结束观看的宽限：给奖励结算留时间，避免提前恢复滚动 */
        const val REWARD_SETTLE_GRACE_MS = 5_000L
    }

    @Volatile
    private var watching = false

    fun isWatching(): Boolean = watching

    fun setWatching(value: Boolean) {
        watching = value
        // 同步 Service 的 companion 标志，保持 UI 显示一致
        serviceProvider.isWatchingAdReward = value
    }

    /** 开启激励任务周期调度（service.startScrolling 后调用） */
    fun schedule() {
        if (!serviceProvider.isScrolling || !serviceProvider.adRewardEnabled) return
        scheduleJob?.cancel()
        val intervalMs = serviceProvider.adRewardMinutes * 60_000L
        scheduleJob = scope.launch {
            delay(intervalMs)
            tryEnter()
        }
        Log.d(serviceProvider.TAG, "已安排激励任务，${serviceProvider.adRewardMinutes} 分钟后尝试")
    }

    fun stop() {
        scheduleJob?.cancel()
        scheduleJob = null
        watchJob?.cancel()
        watchJob = null
        setWatching(false)
    }

    /** 服务销毁时调用：取消整个协程作用域。 */
    fun dispose() {
        scope.cancel()
    }

    /** 配置发生变更时的重置（运行中重新排下一次） */
    fun onConfigChanged() {
        if (!serviceProvider.isScrolling) return
        if (serviceProvider.adRewardEnabled) {
            scheduleJob?.cancel()
            scheduleJob = null
            schedule()
        } else {
            // 功能被关闭（开关或风险确认被取消）：立即停止进行中的观看期，
            // 否则会一直看完当前广告并在超时后才恢复滚动
            stop()
        }
    }

    private fun tryEnter() {
        if (!serviceProvider.isScrolling || !serviceProvider.adRewardEnabled) return
        if (serviceProvider.isBlockedByPolicy()) {
            schedule()
            return
        }
        val label = AdRewardTask.clickRewardEntry(service)
        if (label == null) {
            Log.d(serviceProvider.TAG, "未找到激励入口，等待下个周期")
            schedule()
            return
        }

        serviceProvider.adRewardCount++
        setWatching(true)
        Log.i(serviceProvider.TAG, "已进入激励视频：$label（累计 ${serviceProvider.adRewardCount}）")
        serviceProvider.sendTaskEvent(
            AutoScrollAccessibilityService.EVENT_AD_REWARD,
            context.getString(R.string.toast_ad_reward, label)
        )
        serviceProvider.broadcastState()
        startWatch()
    }

    private fun startWatch() {
        watchJob?.cancel()
        watchStartElapsed = SystemClock.elapsedRealtime()
        val deadline = SystemClock.elapsedRealtime() + AdRewardTask.WATCH_TIMEOUT_MS
        watchJob = scope.launch {
            delay(AdRewardTask.FIRST_CLOSE_DELAY_MS)
            try {
                while (isActive && serviceProvider.isScrolling) {
                    // 观看期扫描「关闭/跳过/领取」按钮：**不受广告屏蔽开关限制**——
                    // 否则用户关闭广告屏蔽后，广告结束页无人点击，会永久卡在广告页。
                    // tryAdBlockNow 内部完成单次扫描 + 计数 + 广播并返回关闭数，
                    // 避免原先「先 scanAndClose 再 tryAdBlockNow」对同一帧重复扫描。
                    val closed = serviceProvider.tryAdBlockNow()
                    if (closed > 0) {
                        // 已点掉关闭/领取按钮：奖励结算需要一点时间，延迟片刻再结束观看，
                        // 避免「广告还没结算就恢复滚动」导致白看一次
                        delay(REWARD_SETTLE_GRACE_MS)
                        finishWatch()
                        return@launch
                    }
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        finishWatch()
                        return@launch
                    }
                    delay(AdRewardTask.CLOSE_POLL_MS)
                }
            } finally {
                if (watching) setWatching(false)
            }
        }
    }

    /**
     * Toast 监听「到账确认」回调：观看期收到「金币已到账」等 Toast 时提前结束观看，
     * 比固定超时更准、更省电。
     */
    fun onRewardReceived() {
        if (!watching) return
        // 最短观看保护：至少观看 MIN_WATCH_BEFORE_RECEIVED_MS，防止误判 Toast 打断广告
        if (SystemClock.elapsedRealtime() - watchStartElapsed < MIN_WATCH_BEFORE_RECEIVED_MS) return
        Log.d(serviceProvider.TAG, "激励视频：检测到账 Toast，结束观看")
        finishWatch()
    }

    private fun finishWatch() {
        setWatching(false)
        // 先取消正在运行的观看期轮询协程，再置空引用并重新排期。
        // 否则当 finishWatch 由外部路径（到账 Toast / 关闭按钮扫描）触发时，
        // 孤儿 watchJob 仍会继续轮询，可能再次命中关闭按钮并重复调用
        // finishWatch → schedule，导致激励间隔被双重安排、到账次数虚高。
        watchJob?.cancel()
        watchJob = null
        Log.d(serviceProvider.TAG, "激励视频观看结束，恢复滚动")
        serviceProvider.broadcastState()
        schedule()
    }
}

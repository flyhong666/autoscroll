package cn.ggdoc.autoscroll.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.task.AdBlocker
import cn.ggdoc.autoscroll.task.AdRewardTask
import cn.ggdoc.autoscroll.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val context: Context,
    private val serviceProvider: ServiceFace
) {

    interface ServiceFace {
        val TAG: String get() = AutoScrollAccessibilityService.TAG
        val isScrolling: Boolean
        val adRewardEnabled: Boolean
        val adRewardMinutes: Int
        val adBlockEnabled: Boolean
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
        scheduleJob?.cancel()
        scheduleJob = null
        if (serviceProvider.adRewardEnabled) schedule()
    }

    private fun tryEnter() {
        if (!serviceProvider.isScrolling || !serviceProvider.adRewardEnabled) return
        if (serviceProvider.isBlockedByPolicy()) {
            schedule()
            return
        }
        val label = AdRewardTask.clickRewardEntry(context)
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
        val deadline = SystemClock.elapsedRealtime() + AdRewardTask.WATCH_TIMEOUT_MS
        watchJob = scope.launch {
            delay(AdRewardTask.FIRST_CLOSE_DELAY_MS)
            try {
                while (isActive && serviceProvider.isScrolling) {
                    var closed = 0
                    if (serviceProvider.adBlockEnabled) {
                        val svc = context as? AutoScrollAccessibilityService
                        if (svc == null) return@launch
                        closed = AdBlocker.scanAndClose(svc)
                    }
                    if (closed > 0) serviceProvider.tryAdBlockNow()
                    if (closed > 0 || SystemClock.elapsedRealtime() >= deadline) {
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

    private fun finishWatch() {
        setWatching(false)
        watchJob = null
        Log.d(serviceProvider.TAG, "激励视频观看结束，恢复滚动")
        serviceProvider.broadcastState()
        schedule()
    }
}

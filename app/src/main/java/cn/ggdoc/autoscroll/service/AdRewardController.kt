package cn.ggdoc.autoscroll.service

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.task.AdBlocker
import cn.ggdoc.autoscroll.task.AdRewardTask

/**
 * 看广告得金币（激励视频）任务控制器。
 *
 * 从 [AutoScrollAccessibilityService] 抽离：
 *  - 周期性尝试（adRewardRunnable）
 *  - 进入激励视频后的观看期轮询（adRewardWatchRunnable）
 *  - 风控：保护策略（低电量/非 Wi-Fi/时间窗口）生效时跳过本轮
 */
class AdRewardController(
    private val context: Context,
    private val handler: Handler,
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
        fun scheduleNextAdReward()
    }

    private var adRewardRunnable: Runnable? = null
    private var adRewardWatchRunnable: Runnable? = null

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
        adRewardRunnable?.let { handler.removeCallbacks(it) }
        val task = Runnable { tryEnter() }
        adRewardRunnable = task
        handler.postDelayed(task, serviceProvider.adRewardMinutes * 60_000L)
        Log.d(serviceProvider.TAG, "已安排激励任务，${serviceProvider.adRewardMinutes} 分钟后尝试")
    }

    fun stop() {
        adRewardRunnable?.let { handler.removeCallbacks(it); adRewardRunnable = null }
        adRewardWatchRunnable?.let { handler.removeCallbacks(it); adRewardWatchRunnable = null }
        setWatching(false)
    }

    /** 配置发生变更时的重置（运行中重新排下一次） */
    fun onConfigChanged() {
        if (!serviceProvider.isScrolling) return
        adRewardRunnable?.let { handler.removeCallbacks(it); adRewardRunnable = null }
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
        adRewardWatchRunnable?.let { handler.removeCallbacks(it) }
        val deadline = SystemClock.elapsedRealtime() + AdRewardTask.WATCH_TIMEOUT_MS
        val poll = object : Runnable {
            override fun run() {
                if (!serviceProvider.isScrolling) {
                    setWatching(false)
                    return
                }
                var closed = 0
                if (serviceProvider.adBlockEnabled) {
                    closed = AdBlocker.scanAndClose(context as? AutoScrollAccessibilityService ?: return)
                }
                if (closed > 0) serviceProvider.tryAdBlockNow() // 走 Service 的计数/事件
                if (closed > 0 || SystemClock.elapsedRealtime() >= deadline) {
                    finishWatch()
                } else {
                    handler.postDelayed(this, AdRewardTask.CLOSE_POLL_MS)
                }
            }
        }
        adRewardWatchRunnable = poll
        handler.postDelayed(poll, AdRewardTask.FIRST_CLOSE_DELAY_MS)
    }

    private fun finishWatch() {
        setWatching(false)
        adRewardWatchRunnable?.let { handler.removeCallbacks(it); adRewardWatchRunnable = null }
        Log.d(serviceProvider.TAG, "激励视频观看结束，恢复滚动")
        serviceProvider.broadcastState()
        schedule()
    }
}

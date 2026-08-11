package cn.ggdoc.autoscroll.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.StatsStore
import cn.ggdoc.autoscroll.task.KeepAliveManager

/**
 * 每秒 tick 控制器 + 统计增量落盘 + WakeLock 续期。
 *
 * 从 [AutoScrollAccessibilityService] 抽离：
 *  - startTick() 每秒（或 5 秒）触发广播、KeepAlive 续期、每 30 tick 落盘
 *  - persistStatsDelta() 自上次基线以来的增量写入 [StatsStore]
 *  - resetBaseline() 每次 startScrolling 调用
 *
 * 依赖 Service 提供：
 *  - [serviceProvider]：取 runningSeconds / isScrolling / timedStop / remainingSeconds / broadcastState
 */
class StatsController(
    private val context: Context,
    private val handler: Handler,
    private val serviceProvider: ServiceFace
) {

    interface ServiceFace {
        val isScrolling: Boolean
        val runningSeconds: Long
        val timedStop: Boolean
        val timedStopMinutes: Int
        var remainingSeconds: Long
        val startTimestamp: Long
        fun broadcastState()
    }

    companion object {
        private const val STATS_PERSIST_TICKS = 30
    }

    private var tickRunnable: Runnable? = null
    private var tickCount = 0

    private var lastPersistedScrolls = 0
    private var lastPersistedLikes = 0
    private var lastPersistedAdBlocks = 0
    private var lastPersistedAdRewards = 0
    private var lastPersistedDetails = 0
    private var lastPersistSecondsMark = 0L

    fun resetBaseline() {
        lastPersistedScrolls = 0
        lastPersistedLikes = 0
        lastPersistedAdBlocks = 0
        lastPersistedAdRewards = 0
        lastPersistedDetails = 0
        lastPersistSecondsMark = 0L
    }

    fun startTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickCount = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!serviceProvider.isScrolling) return
                KeepAliveManager.refresh(context)
                if (serviceProvider.timedStop) {
                    val elapsed = (System.currentTimeMillis() - serviceProvider.startTimestamp) / 1000
                    val total = serviceProvider.timedStopMinutes.toLong() * 60
                    serviceProvider.remainingSeconds = (total - elapsed).coerceAtLeast(0)
                }
                tickCount++
                if (tickCount % STATS_PERSIST_TICKS == 0) {
                    persistDelta()
                }
                // 未开启定时停止时降频广播，减少耗电
                val interval = if (serviceProvider.timedStop) 1000L else 5000L
                serviceProvider.broadcastState()
                handler.postDelayed(this, interval)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    fun stop() {
        persistDelta()
        tickRunnable?.let { handler.removeCallbacks(it); tickRunnable = null }
    }

    /** 把当前内存计数器的增量写入 [StatsStore]。stopScrolling 先调这个再翻转 isScrolling。 */
    fun persistDelta() {
        val nowSeconds = serviceProvider.runningSeconds
        val svc = (context as? AutoScrollAccessibilityService) ?: return
        val delta = StatsStore.Stats(
            scrolls = svc.scrollCount - lastPersistedScrolls,
            likes = svc.likeCount - lastPersistedLikes,
            adBlocks = svc.adBlockCount - lastPersistedAdBlocks,
            adRewards = svc.adRewardCount - lastPersistedAdRewards,
            details = svc.detailCount - lastPersistedDetails,
            seconds = (nowSeconds - lastPersistSecondsMark).coerceAtLeast(0)
        )
        if (delta.isEmpty) return
        try {
            StatsStore.accumulate(context, delta)
            lastPersistedScrolls = svc.scrollCount
            lastPersistedLikes = svc.likeCount
            lastPersistedAdBlocks = svc.adBlockCount
            lastPersistedAdRewards = svc.adRewardCount
            lastPersistedDetails = svc.detailCount
            lastPersistSecondsMark = nowSeconds
        } catch (_: Exception) {
            // 上层 Service 已经打日志，这里避免重复
        }
    }

    // 公开代理：保持 Service 对外 API 不变
    fun getTodayStats(): StatsStore.Stats = StatsStore.today(context)
    fun getTotalStats(): StatsStore.Stats = StatsStore.total(context)
}

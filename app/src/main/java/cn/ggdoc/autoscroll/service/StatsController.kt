package cn.ggdoc.autoscroll.service

import android.content.Context
import android.util.Log
import cn.ggdoc.autoscroll.config.StatsStore
import cn.ggdoc.autoscroll.task.KeepAliveManager
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
 * 每秒 tick 控制器 + 统计增量落盘 + WakeLock 续期。
 *
 * 从 [AutoScrollAccessibilityService] 抽离：
 *  - startTick() 每秒（或 5 秒）触发广播、KeepAlive 续期、每 30 tick 落盘
 *  - persistDelta() 自上次基线以来的增量写入 [StatsStore]
 *  - resetBaseline() 每次 startScrolling 调用
 *
 * 调度由内部 [CoroutineScope]（主线程）驱动：startTick 启动一个
 * `while(isScrolling) { ...; delay(interval) }` 协程，stop() 取消该协程。
 */
class StatsController(
    private val context: Context,
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + AppLog.coroutineExceptionHandler)
    private var tickJob: Job? = null
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
        tickJob?.cancel()
        tickCount = 0
        tickJob = scope.launch {
            while (isActive && serviceProvider.isScrolling) {
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
                delay(interval)
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        persistDelta()
    }

    /** 服务销毁时调用：取消整个协程作用域。stop() 仅取消 tick 协程并落盘，dispose() 彻底关闭 scope。 */
    fun dispose() {
        scope.cancel()
    }

    /** 把当前内存计数器的增量写入 [StatsStore]。stopScrolling 先调这个再翻转 isScrolling。 */
    fun persistDelta() {
        val nowSeconds = serviceProvider.runningSeconds
        val delta = StatsStore.Stats(
            scrolls = AutoScrollAccessibilityService.scrollCount - lastPersistedScrolls,
            likes = AutoScrollAccessibilityService.likeCount - lastPersistedLikes,
            adBlocks = AutoScrollAccessibilityService.adBlockCount - lastPersistedAdBlocks,
            adRewards = AutoScrollAccessibilityService.adRewardCount - lastPersistedAdRewards,
            details = AutoScrollAccessibilityService.detailCount - lastPersistedDetails,
            seconds = (nowSeconds - lastPersistSecondsMark).coerceAtLeast(0)
        )
        if (delta.isEmpty) return
        try {
            StatsStore.accumulate(context, delta)
            lastPersistedScrolls = AutoScrollAccessibilityService.scrollCount
            lastPersistedLikes = AutoScrollAccessibilityService.likeCount
            lastPersistedAdBlocks = AutoScrollAccessibilityService.adBlockCount
            lastPersistedAdRewards = AutoScrollAccessibilityService.adRewardCount
            lastPersistedDetails = AutoScrollAccessibilityService.detailCount
            lastPersistSecondsMark = nowSeconds
        } catch (_: Exception) {
            // 上层 Service 已经打日志，这里避免重复
        }
    }

    // 公开代理：保持 Service 对外 API 不变
    fun getTodayStats(): StatsStore.Stats = StatsStore.today(context)
    fun getTotalStats(): StatsStore.Stats = StatsStore.total(context)
}

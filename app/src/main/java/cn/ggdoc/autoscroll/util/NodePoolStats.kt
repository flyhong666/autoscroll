package cn.ggdoc.autoscroll.util

import java.util.concurrent.atomic.AtomicLong

/**
 * 无障碍节点池（AccessibilityNodeInfo 固定大小池）运行统计。
 *
 * 背景：Android 13（API 33）以下，节点对象来自一个固定大小的池，
 * 遍历产生的节点若不及时 recycle，会导致 rootInActiveWindow 逐渐返回 null、
 * 滑动静默失效（表现为「跑一会儿就不动了」）。
 *
 * 本工具用于**可观测性**：在 [cn.ggdoc.autoscroll.service.NodeFinder]
 * 遍历 / 回收的关键路径上埋点，累计「遍历数 / 回收数 / 保留数 / 峰值」，
 * 配合 [cn.ggdoc.autoscroll.ui.LogActivity] 的「节点池统计」按钮，
 * 让这类隐蔽的节点泄漏问题从「不可见」变为「一眼可见」。
 *
 * 计数器均为 AtomicLong，可在无障碍服务线程与 UI 线程并发读写而无需加锁。
 */
object NodePoolStats {

    /** 累计遍历访问的节点数（BFS 出队次数之和） */
    private val totalTraversed = AtomicLong(0)

    /** 累计显式 recycle 的节点数（仅统计本工具发起的回收，不含 Android 内部回收） */
    private val totalRecycled = AtomicLong(0)

    /** 累计被保留给调用方、未回收的节点数（容器 + 列表项 / 滚动节点） */
    private val totalRetained = AtomicLong(0)

    /** 单次遍历中访问节点数的峰值，用于识别异常大的视图树 */
    private val peakPerTraversal = AtomicLong(0)

    /** 累计 findScrollable / collectListItems 的调用次数 */
    private val totalCalls = AtomicLong(0)

    fun recordTraversed(n: Int) {
        if (n <= 0) return
        totalTraversed.addAndGet(n.toLong())
        val cur = totalTraversed.get()
        // 仅当本次 n 本身超过已记录峰值时更新（n 为单次遍历的访问量）
        var prev: Long
        do {
            prev = peakPerTraversal.get()
            if (n.toLong() <= prev) break
        } while (!peakPerTraversal.compareAndSet(prev, n.toLong()))
    }

    fun recordRecycled(n: Int) {
        if (n > 0) totalRecycled.addAndGet(n.toLong())
    }

    fun recordRetained(n: Int) {
        if (n > 0) totalRetained.addAndGet(n.toLong())
    }

    fun recordCall() = totalCalls.incrementAndGet()

    /** 当前累计指标的不可变快照（供 UI 展示） */
    data class Snapshot(
        val traversed: Long,
        val recycled: Long,
        val retained: Long,
        val peakPerTraversal: Long,
        val calls: Long
    ) {
        /** 回收率（0~1）：回收 / 遍历，越接近 1 越健康 */
        val recycleRatio: Double
            get() = if (traversed == 0L) 0.0 else recycled.toDouble() / traversed

        /** 是否可能存在节点泄漏（回收率显著低于 1，且已有相当量级样本） */
        val mayLeak: Boolean
            get() = traversed > 5000 && recycleRatio < 0.95

        fun format(): String = buildString {
            appendLine("节点池统计（自启动累计）")
            appendLine("遍历节点总数：$traversed")
            appendLine("显式回收总数：$recycled")
            appendLine("保留给调用方：$retained")
            appendLine("单次遍历峰值：$peakPerTraversal")
            appendLine("查找调用次数：$calls")
            appendLine("回收率：${String.format("%.1f", recycleRatio * 100)}%")
            if (mayLeak) appendLine("⚠️ 回收率偏低，疑似节点泄漏，请检查 NodeFinder 回收逻辑")
        }
    }

    fun snapshot(): Snapshot = Snapshot(
        traversed = totalTraversed.get(),
        recycled = totalRecycled.get(),
        retained = totalRetained.get(),
        peakPerTraversal = peakPerTraversal.get(),
        calls = totalCalls.get()
    )

    fun reset() {
        totalTraversed.set(0)
        totalRecycled.set(0)
        totalRetained.set(0)
        peakPerTraversal.set(0)
        totalCalls.set(0)
    }
}

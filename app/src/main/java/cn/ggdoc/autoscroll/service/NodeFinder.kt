package cn.ggdoc.autoscroll.service

import cn.ggdoc.autoscroll.util.recycleCompat
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.util.NodePoolStats
import java.util.HashSet
import java.util.LinkedList

/**
 * 无障碍节点查找工具：可滚动容器识别 + 列表条目收集。
 * 供自动滚动（滑动/详情流）与广告屏蔽等逻辑复用。
 *
 * 重要：Android 13（API 33）以下节点对象来自一个固定大小的池，
 * 不复用（recycle）会导致 rootInActiveWindow 逐渐返回 null、滑动静默失效。
 * 因此本工具在遍历结束后回收所有「不被返回、不被外层持有」的中间节点，
 * 仅保留调用方需要继续使用的容器与列表项节点。
 */
object NodeFinder {

    private val SCROLLABLE_CLASSES = listOf(
        "androidx.recyclerview.widget.RecyclerView",
        "android.support.v7.widget.RecyclerView",
        "android.widget.ListView",
        "android.widget.ScrollView",
        "androidx.core.widget.NestedScrollView",
        "android.webkit.WebView",
        "android.support.v4.view.ViewPager",
        "androidx.viewpager.widget.ViewPager",
        "androidx.viewpager2.widget.ViewPager2"
    )

    /** 列表条目（含节点与屏幕坐标） */
    data class ListItem(
        val node: AccessibilityNodeInfo,
        val rect: Rect
    )

    /**
     * 广度优先寻找可滚动容器：
     * 类名命中常见滚动控件白名单，或节点自身 isScrollable。
     *
     * 优先匹配「真正的列表容器」（RecyclerView / ListView），这些通常才是内容列表；
     * 未命中则回退到任意可滚动容器（含 ScrollView / WebView / ViewPager 等）或 isScrollable 节点。
     *
     * 返回找到的容器节点（调用方持有，本方法不回收）；遍历产生的其余中间节点会被回收。
     *
     * 注意：早期的「两轮 BFS」实现有缺陷——第一轮（listOnly）已把整棵树遍历并回收，
     * 导致第二轮拿到的是空队列、永远返回 null，ScrollView/WebView/ViewPager 类应用
     * 永远命中不了节点级滚动，只能走全屏手势兜底（精度下降）。现改为单遍 BFS，
     * 在遍历过程中同时记录「列表容器」与「任意可滚动容器」两个候选，优先返回前者。
     */
    fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        NodePoolStats.recordCall()
        val visited = HashSet<AccessibilityNodeInfo>()
        visited.add(root)
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.offer(root)
        // 单遍 BFS：优先命中「真正的列表容器」（RecyclerView / ListView），
        // 未命中则回退到任意可滚动容器（ScrollView / WebView / ViewPager 等）或 isScrollable 节点。
        var keep: AccessibilityNodeInfo? = null
        var visitedCount = 0
        var traversed = 0
        while (queue.isNotEmpty() && visitedCount < 1500) {
            val current = queue.poll() ?: continue
            visitedCount++
            traversed++
            val className = current.className?.toString().orEmpty()
            val isList = className.endsWith("RecyclerView") || className.endsWith("ListView")
            val isScrollableClass = SCROLLABLE_CLASSES.any { className.contains(it, ignoreCase = true) }
            if (keep == null && isList) {
                keep = current
            }
            if (keep == null && (isScrollableClass || current.isScrollable)) {
                keep = current
            }
            for (i in 0 until current.childCount) {
                val child = current.getChild(i) ?: continue
                if (visited.add(child)) {
                    queue.offer(child)
                } else {
                    child.recycleCompat()
                    NodePoolStats.recordRecycled(1)
                }
            }
            // 处理完当前节点后回收（保留 root 与 keep；keep 可能为 null 直到命中）
            if (current !== root && current !== keep) {
                current.recycleCompat()
                NodePoolStats.recordRecycled(1)
            }
        }
        // 因达到遍历上限而残留在队列中、尚未处理的节点也要回收（保留 root 与 keep）
        var residualRecycled = 0
        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue
            if (node !== root && node !== keep) {
                runCatching { node.recycleCompat() }
                residualRecycled++
            }
        }
        NodePoolStats.recordRecycled(residualRecycled)
        NodePoolStats.recordTraversed(traversed)
        if (keep != null) NodePoolStats.recordRetained(1)
        return keep
    }

    /**
     * 在列表容器内收集「完整可见、尺寸合理」的可点击条目，按从上到下排序。
     *
     * 过滤规则：
     *  - 完全位于容器可视范围内（避免点半露的条目）
     *  - 高度在 [minItemHeight, 容器高*0.75] 之间（排除顶栏/小按钮/整屏卡片）
     *  - 宽度 >= 容器宽*0.5（排除侧边小图标）
     *  - 文案命中「广告/推广/赞助」的推广位跳过
     *  - 被其他候选条目完全包含的重复节点去重
     *
     * 遍历产生的中间节点在结束后回收，仅保留 [container] 与返回给调用方的列表项节点。
     */
    fun collectListItems(
        container: AccessibilityNodeInfo,
        containerRect: Rect,
        minItemHeight: Int
    ): List<ListItem> {
        NodePoolStats.recordCall()
        val out = mutableListOf<ListItem>()
        val maxH = (containerRect.height() * 0.75f).toInt()
        val minW = (containerRect.width() * 0.5f).toInt()
        val queue = LinkedList<Pair<AccessibilityNodeInfo, Int>>()
        val visited = HashSet<AccessibilityNodeInfo>()
        visited.add(container)
        queue.offer(container to 0)
        var traversed = 0
        while (queue.isNotEmpty() && out.size < 40) {
            val (node, depth) = queue.poll() ?: continue
            if (depth > 10) continue
            traversed++
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val fullyVisible = rect.width() > 0 && rect.height() > 0 &&
                    rect.top >= containerRect.top - 2 && rect.bottom <= containerRect.bottom + 2 &&
                    rect.left >= containerRect.left - 2 && rect.right <= containerRect.right + 2
            if (fullyVisible && node.isClickable &&
                rect.height() in minItemHeight..maxH &&
                rect.width() >= minW &&
                !looksLikeAd(node)
            ) {
                val key = rect.flattenToString()
                if (out.none { it.rect.flattenToString() == key }) {
                    out.add(ListItem(node, rect))
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (visited.add(child)) {
                    queue.offer(child to depth + 1)
                } else {
                    child.recycleCompat()
                    NodePoolStats.recordRecycled(1)
                }
            }
        }
        // 去掉被更大条目完全包含的重复项（父子都可点击时只保留外层）
        val dedup = out.filter { a ->
            out.none { b ->
                b !== a && b.rect != a.rect &&
                        b.rect.width() >= a.rect.width() &&
                        b.rect.height() >= a.rect.height() &&
                        b.rect.contains(a.rect)
            }
        }
        val keep = dedup.map { it.node }.toSet()
        // 回收所有遍历过的节点，保留 container 与 retained 列表项
        var recycledMid = 0
        visited.forEach { n ->
            if (n !== container && n !in keep) {
                n.recycleCompat()
                recycledMid++
            }
        }
        NodePoolStats.recordRecycled(recycledMid)
        NodePoolStats.recordTraversed(traversed)
        NodePoolStats.recordRetained(keep.size)
        return dedup.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
    }

    /** 列表内推广位的常见标记文案 */
    private fun looksLikeAd(node: AccessibilityNodeInfo): Boolean {
        val text = buildString {
            append(node.text?.toString().orEmpty())
            append(' ')
            append(node.contentDescription?.toString().orEmpty())
        }
        return text.contains("广告") || text.contains("推广") ||
                text.contains("赞助") || text.contains(" sponsored")
    }
}

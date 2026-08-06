package cn.ggdoc.autoscroll.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList

/**
 * 无障碍节点查找工具：可滚动容器识别 + 列表条目收集。
 * 供自动滚动（滑动/详情流）与广告屏蔽等逻辑复用。
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
     */
    fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.offer(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 1500) {
            val current = queue.poll() ?: continue
            visited++
            val className = current.className?.toString().orEmpty()
            if (SCROLLABLE_CLASSES.any { className.contains(it, ignoreCase = true) }) return current
            if (current.isScrollable) return current
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.offer(it) }
            }
        }
        return null
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
     */
    fun collectListItems(
        container: AccessibilityNodeInfo,
        containerRect: Rect,
        minItemHeight: Int
    ): List<ListItem> {
        val out = mutableListOf<ListItem>()
        val maxH = (containerRect.height() * 0.75f).toInt()
        val minW = (containerRect.width() * 0.5f).toInt()
        val queue = LinkedList<Pair<AccessibilityNodeInfo, Int>>()
        queue.offer(container to 0)
        val seen = HashSet<String>()
        while (queue.isNotEmpty() && out.size < 40) {
            val (node, depth) = queue.poll() ?: continue
            if (depth > 10) continue
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
                if (seen.add(key)) out.add(ListItem(node, rect))
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.offer(it to depth + 1) }
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

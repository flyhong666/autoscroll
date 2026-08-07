package cn.ggdoc.autoscroll.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.human.PageClassifier
import cn.ggdoc.autoscroll.human.StuckDetector
import java.util.HashSet
import java.util.LinkedList

/**
 * 屏幕快照采集：一次遍历同时产出
 *  1) 文本指纹（供 [StuckDetector] 判断「滑了但内容没变」）
 *  2) 页面信号（供 [PageClassifier] 多信号判定列表页 / 详情页）
 *
 * 设计要点：
 * - 单次 BFS 同时收集两类数据，避免为了两个用途遍历两遍（无障碍节点遍历开销大）
 * - 严格回收中间节点：API 33 以下节点来自固定大小的池，泄漏会导致
 *   rootInActiveWindow 逐渐返回 null，表现为「跑一会儿就不动了」
 * - 遍历有硬上限（节点数 / 深度），避免在超大页面上卡住主线程
 */
object ScreenSnapshot {

    /** 单次遍历的节点数上限，防止超大页面拖慢主线程 */
    private const val MAX_VISIT = 900

    /** 遍历深度上限 */
    private const val MAX_DEPTH = 14

    /** 参与指纹计算的文本条数上限（取靠前的可见文本即可代表当前屏内容） */
    private const val MAX_FINGERPRINT_TEXTS = 24

    /** 单条文本纳入指纹的最小长度，过滤「1」「•」这类噪声 */
    private const val MIN_TEXT_LENGTH = 2

    /** 判定「可能是返回入口」的控件描述关键词 */
    private val BACK_WORDS = listOf("返回", "back", "关闭", "close")

    /**
     * 一次快照的结果。
     *
     * @param texts        参与指纹的可见文本（已按遍历顺序截断）
     * @param fingerprint  文本指纹，[StuckDetector.NO_HASH] 表示本次未采到有效内容
     * @param signals      页面信号，可直接交给 [PageClassifier.classify]
     */
    data class Snapshot(
        val texts: List<String>,
        val fingerprint: Long,
        val signals: PageClassifier.Signals
    ) {
        /** 本次快照是否有效（采到了内容） */
        val isValid: Boolean get() = fingerprint != StuckDetector.NO_HASH

        /** 便捷方法：直接得到页面类型 */
        fun pageType(): PageClassifier.PageType = PageClassifier.classify(signals)
    }

    /** 空快照：root 为 null 或采集失败时返回 */
    val EMPTY = Snapshot(
        texts = emptyList(),
        fingerprint = StuckDetector.NO_HASH,
        signals = PageClassifier.Signals()
    )

    /**
     * 采集当前窗口快照。
     *
     * @param root          当前活动窗口根节点，调用方负责其生命周期（本方法不回收 root）
     * @param screenHeight  屏幕高度，用于估算「列表项」的高度门槛；<=0 时用固定门槛
     */
    fun capture(root: AccessibilityNodeInfo?, screenHeight: Int = 0): Snapshot {
        if (root == null) return EMPTY

        val texts = ArrayList<String>(MAX_FINGERPRINT_TEXTS)
        var isWebViewContainer = false
        var totalTextLength = 0
        var maxSingleTextLength = 0
        var hasDetailActionWords = false
        var hasBackAffordance = false
        var listItemCount = 0
        var clickableCount = 0

        // 列表项高度门槛：屏幕高度的 6%~55% 之间才算一条内容卡片
        val minItemH = if (screenHeight > 0) (screenHeight * 0.06f).toInt() else 60
        val maxItemH = if (screenHeight > 0) (screenHeight * 0.55f).toInt() else Int.MAX_VALUE

        val visited = HashSet<AccessibilityNodeInfo>()
        val queue = LinkedList<Pair<AccessibilityNodeInfo, Int>>()
        visited.add(root)
        queue.offer(root to 0)
        var visitCount = 0

        try {
            while (queue.isNotEmpty() && visitCount < MAX_VISIT) {
                val (node, depth) = queue.poll() ?: continue
                visitCount++

                val className = node.className?.toString().orEmpty()
                if (!isWebViewContainer && className.contains("WebView", ignoreCase = true)) {
                    isWebViewContainer = true
                }

                val nodeText = node.text?.toString()?.trim().orEmpty()
                val nodeDesc = node.contentDescription?.toString()?.trim().orEmpty()

                if (nodeText.length >= MIN_TEXT_LENGTH) {
                    totalTextLength += nodeText.length
                    if (nodeText.length > maxSingleTextLength) maxSingleTextLength = nodeText.length
                    if (texts.size < MAX_FINGERPRINT_TEXTS) texts.add(nodeText)
                    if (!hasDetailActionWords && PageClassifier.containsDetailAction(nodeText)) {
                        hasDetailActionWords = true
                    }
                }
                if (nodeDesc.isNotEmpty()) {
                    if (!hasDetailActionWords && PageClassifier.containsDetailAction(nodeDesc)) {
                        hasDetailActionWords = true
                    }
                    if (!hasBackAffordance && BACK_WORDS.any { nodeDesc.contains(it, true) }) {
                        hasBackAffordance = true
                    }
                }

                if (node.isClickable) {
                    clickableCount++
                    val rect = Rect().also { node.getBoundsInScreen(it) }
                    val h = rect.height()
                    // 有文字 + 高度像内容卡片 → 计为一条列表项
                    if (h in minItemH..maxItemH && rect.width() > 0 &&
                        (nodeText.length >= MIN_TEXT_LENGTH || nodeDesc.length >= MIN_TEXT_LENGTH)
                    ) {
                        listItemCount++
                    }
                }

                if (depth < MAX_DEPTH) {
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        if (visited.add(child)) {
                            queue.offer(child to depth + 1)
                        } else {
                            child.recycle()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 遍历期间窗口可能被销毁，节点访问抛异常；用已采到的部分数据继续
        } finally {
            // 回收所有遍历过的节点（root 归调用方所有）
            visited.forEach { n -> if (n !== root) runCatching { n.recycle() } }
            queue.clear()
        }

        val fingerprint = StuckDetector.fingerprint(texts)
        val signals = PageClassifier.Signals(
            isWebViewContainer = isWebViewContainer,
            totalTextLength = totalTextLength,
            maxSingleTextLength = maxSingleTextLength,
            hasDetailActionWords = hasDetailActionWords,
            hasBackAffordance = hasBackAffordance,
            listItemCount = listItemCount,
            clickableCount = clickableCount
        )
        return Snapshot(texts, fingerprint, signals)
    }
}

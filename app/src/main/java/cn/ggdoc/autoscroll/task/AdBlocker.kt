package cn.ggdoc.autoscroll.task

import cn.ggdoc.autoscroll.util.recycleCompat
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.config.AppConfig

/**
 * 广告弹窗自动关闭工具
 *
 * 工作原理：
 *  1) 每次滚动前遍历当前窗口节点
 *  2) 查找包含「跳过/关闭/知道了/确定」等关键词的可点击节点
 *  3) 优先点击文本最短且可点击的节点（避免误点正文）
 *  4) 单次最多点击 3 个，避免死循环
 */
object AdBlocker {

    private const val TAG = "AdBlocker"
    private const val MAX_CLICK_PER_SCAN = 3

    /**
     * 扫描并关闭广告弹窗
     * @return 关闭的弹窗数量
     */
    fun scanAndClose(service: AccessibilityService): Int {
        var closed = 0
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        // root 在 try 外声明，finally 才能回收（S3）；try 内部不再重复声明
        val root = service.rootInActiveWindow ?: return 0
        try {
            val keywords = AppConfig.getAdKeywords(service)
            if (keywords.isEmpty()) return 0

            collectClickableNodes(root, candidates)

            for (node in candidates) {
                if (closed >= MAX_CLICK_PER_SCAN) break
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val combined = "$text $desc"

                if (keywords.any { combined.contains(it) }) {
                    // 找到目标节点，逐级向上找可点击的自身或祖先，再执行点击
                    val clickTarget = AdNodeKit.clickableSelfOrAncestor(node) ?: node
                    val clicked = AdNodeKit.click(clickTarget)
                    if (clicked) {
                        closed++
                        Log.d(TAG, "关闭弹窗：text=$text desc=$desc")
                    }
                    // clickTarget 若来自祖先回溯（非 node 自身），用后回收，避免节点池泄漏
                    if (clickTarget !== node) runCatching { clickTarget.recycleCompat() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "广告屏蔽扫描失败", e)
        } finally {
            // 回收候选节点与（未进入候选列表的）root，避免 Android 13 以下节点池耗尽
            AdNodeKit.recycle(root, candidates)
        }
        return closed
    }

    /**
     * 递归收集可点击、且文案简短的节点（限制深度与数量避免 OOM）。
     *
     * 收紧点（避免误伤正文）：
     *  - 只收「真正可点击」的节点，去掉 isFocusable 这个过宽条件（正文文本块往往可聚焦但不可点）；
     *  - 文案过长（>16 字）的节点视为内容而非按钮，跳过，避免点掉正文段落。
     *
     * 回收策略（S3）：遍历过程中每个节点要么进入 [out]（由调用方回收），
     * 要么在递归返回后被其父节点回收。这样所有「非候选」中间节点都会被回收，
     * 不会占用 Android 13 以下有限的节点池。返回自身是否为候选，供父节点决策。
     */
    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ): Boolean {
        if (depth > 20 || out.size > 200) return false

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val hasText = text.isNotEmpty() || desc.isNotEmpty()
        val textLen = maxOf(text.length, desc.length)

        val isCandidate = hasText && node.isClickable && textLen <= 16
        if (isCandidate) {
            out.add(node) // 候选节点由 scanAndClose 的 finally 回收
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childCandidate = collectClickableNodes(child, out, depth + 1)
            // 非候选子节点在此处回收（候选子节点已在 out 中，由调用方回收）
            if (!childCandidate) runCatching { child.recycleCompat() }
        }
        return isCandidate
    }

}

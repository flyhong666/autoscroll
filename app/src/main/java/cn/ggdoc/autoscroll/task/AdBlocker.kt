package cn.ggdoc.autoscroll.task

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.config.SceneConfig

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
        try {
            val root = service.rootInActiveWindow ?: return 0
            val candidates = mutableListOf<AccessibilityNodeInfo>()

            collectClickableNodes(root, candidates)

            for (node in candidates) {
                if (closed >= MAX_CLICK_PER_SCAN) break
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val combined = "$text $desc"

                if (SceneConfig.AD_BLOCK_KEYWORDS.any { combined.contains(it) }) {
                    // 找到目标节点，逐级向上找可点击的祖先，再执行点击
                    val clickTarget = findClickableAncestor(node) ?: node
                    val clicked = performClick(clickTarget)
                    if (clicked) {
                        closed++
                        Log.d(TAG, "关闭弹窗：text=$text desc=$desc")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "广告屏蔽扫描失败", e)
        }
        return closed
    }

    /**
     * 递归收集所有可点击 / 有文本的节点（限制深度与数量避免 OOM）
     */
    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 20 || out.size > 200) return

        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val clickable = node.isClickable

        if (hasText && (clickable || node.isFocusable)) {
            out.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodes(child, out, depth + 1)
        }
    }

    /**
     * 找到自身或最近的可点击祖先
     */
    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * 执行点击：优先 performAction，失败则派发手势
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clicked) {
            // 尝试点击父节点
            val parent = node.parent
            if (parent != null) {
                clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return clicked
    }
}

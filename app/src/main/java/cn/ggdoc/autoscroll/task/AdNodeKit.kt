package cn.ggdoc.autoscroll.task

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 广告相关节点的共享工具。
 *
 * [AdBlocker] 与 [AdRewardTask] 都需要「取文案 / 找可点击自身或祖先 / 点击（含父节点兜底）/ 回收节点」，
 * 这些逻辑原本各写一份（约 80 行重复，且易漂移）。此处抽出唯一实现。
 */
object AdNodeKit {

    /** 取节点文案：优先 text，其次 contentDescription（裁剪空白） */
    fun labelOf(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
        return node.contentDescription?.toString()?.trim().orEmpty()
    }

    /**
     * 返回自身（若可点击）或最近的可点击祖先。
     *
     * 回溯途中途经的非可点击祖先会被逐级回收，避免 parent 链泄漏。
     * 返回的节点若来自祖先（非 [node] 自身），调用方用后需回收。
     */
    fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var current = try { node.parent } catch (_: Exception) { null }
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            val parent = try { current.parent } catch (_: Exception) { null }
            runCatching { current.recycle() }
            current = parent
            depth++
        }
        return null
    }

    /**
     * 对目标节点执行点击；失败则尝试点击父节点（父节点用后回收）。
     */
    fun click(node: AccessibilityNodeInfo): Boolean {
        if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)) {
            return true
        }
        val parent = try { node.parent } catch (_: Exception) { null }
        val clicked = parent != null &&
            runCatching { parent.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        runCatching { parent?.recycle() }
        return clicked
    }

    /** 回收候选节点与（未进入候选列表的）root，避免 Android 13 以下节点池泄漏 */
    fun recycle(root: AccessibilityNodeInfo?, candidates: List<AccessibilityNodeInfo>) {
        candidates.forEach { runCatching { it.recycle() } }
        if (root != null && root !in candidates) runCatching { root.recycle() }
    }
}

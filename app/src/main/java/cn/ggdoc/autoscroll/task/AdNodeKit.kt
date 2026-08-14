package cn.ggdoc.autoscroll.task

import cn.ggdoc.autoscroll.util.recycleCompat
import android.view.accessibility.AccessibilityNodeInfo
import java.util.HashSet
import java.util.LinkedList

/**
 * 广告相关节点的共享工具。
 *
 * [AdBlocker] 与 [AdRewardTask] 都需要「取文案 / 找可点击自身或祖先 / 点击（含父节点兜底）/ 回收节点」，
 * 这些逻辑原本各写一份（约 80 行重复，且易漂移）。此处抽出唯一实现。
 */
object AdNodeKit {

    /** 单次 BFS 遍历的节点上限，防止超大页面拖慢主线程 */
    private const val MAX_VISIT = 1500

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
            runCatching { current.recycleCompat() }
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
        runCatching { parent?.recycleCompat() }
        return clicked
    }

    /**
     * 在窗口树中查找「文案包含 [keyword] 的可点击控件」（BFS）。
     *
     * 收集所有命中并取「文案最短」的节点（最像按钮，避免 BFS 先命中外层
     * 大卡片），再经 [clickableSelfOrAncestor] 归一为可点击节点。
     * 返回节点由调用方用后回收（可能等于 [root] 本身）；未找到返回 null。
     * 遍历产生的其余中间节点全部回收，避免节点池泄漏；有遍历上限防超大页面。
     */
    fun findClickableByText(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        if (keyword.isBlank()) return null
        val visited = HashSet<AccessibilityNodeInfo>()
        val queue = LinkedList<AccessibilityNodeInfo>()
        visited.add(root)
        queue.offer(root)
        var best: AccessibilityNodeInfo? = null
        var bestSource: AccessibilityNodeInfo? = null
        var bestLen = Int.MAX_VALUE
        var visitedCount = 0
        while (queue.isNotEmpty() && visitedCount < MAX_VISIT) {
            val node = queue.poll() ?: continue
            visitedCount++
            val label = labelOf(node)
            if (label.contains(keyword)) {
                val target = if (node.isClickable) node else clickableSelfOrAncestor(node)
                if (target != null && label.length < bestLen) {
                    best = target
                    bestSource = node
                    bestLen = label.length
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (visited.add(child)) queue.offer(child)
                // 重复访问的节点不在此回收：可能仍在队列中待处理，统一在下方清理
            }
        }
        // 回收遍历过的所有节点：保留 root、best（调用方负责）与 bestSource（下方单独回收）
        visited.forEach { n ->
            if (n !== root && n !== best && n !== bestSource) runCatching { n.recycleCompat() }
        }
        if (bestSource != null && bestSource !== best) runCatching { bestSource.recycleCompat() }
        return best
    }

    /**
     * 在窗口树中查找「文案包含 [keyword]」的任意可见节点（不要求可点击）。
     *
     * 用于「条件分支」等只看文本是否出现的场景。返回命中节点或 null，调用方用后回收
     * （可能等于 [root] 本身）；遍历产生的其余中间节点全部回收。
     */
    fun findNodeByText(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        if (keyword.isBlank()) return null
        val visited = HashSet<AccessibilityNodeInfo>()
        val queue = LinkedList<AccessibilityNodeInfo>()
        visited.add(root)
        queue.offer(root)
        var found: AccessibilityNodeInfo? = null
        var visitedCount = 0
        while (queue.isNotEmpty() && found == null && visitedCount < MAX_VISIT) {
            val node = queue.poll() ?: continue
            visitedCount++
            if (labelOf(node).contains(keyword)) {
                found = node
                break
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (visited.add(child)) queue.offer(child)
            }
        }
        visited.forEach { n -> if (n !== root && n !== found) runCatching { n.recycleCompat() } }
        return found
    }

    /** 回收候选节点与 root，避免 Android 13 以下节点池泄漏 */
    fun recycle(root: AccessibilityNodeInfo?, candidates: List<AccessibilityNodeInfo>) {
        // 修复：root 自身可能是候选节点（可点击且有文案）——若用 `root !in candidates`
        // 判断会漏回收 root。这里保证 root 恰好回收一次，候选列表里跳过它即可。
        candidates.forEach { if (it !== root) runCatching { it.recycleCompat() } }
        root?.let { runCatching { it.recycleCompat() } }
    }
}

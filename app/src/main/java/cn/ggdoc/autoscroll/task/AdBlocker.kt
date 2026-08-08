package cn.ggdoc.autoscroll.task

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
                // 找到目标节点，逐级向上找可点击的祖先，再执行点击
                val clickTarget = findClickableAncestor(node) ?: node
                val clicked = performClick(clickTarget)
                if (clicked) {
                    closed++
                    Log.d(TAG, "关闭弹窗：text=$text desc=$desc")
                }
                // clickTarget 若来自祖先回溯（非 node 自身），用后回收，避免节点池泄漏
                if (clickTarget !== node) runCatching { clickTarget.recycle() }
            }
            }
        } catch (e: Exception) {
            Log.e(TAG, "广告屏蔽扫描失败", e)
        } finally {
            // 回收候选节点，避免 Android 13 以下节点池耗尽导致静默失效
            candidates.forEach { runCatching { it.recycle() } }
            // root 若未进入候选列表也要回收（collectClickableNodes 只回收非候选后代节点）
            if (root != null && root !in candidates) runCatching { root.recycle() }
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
            if (!childCandidate) runCatching { child.recycle() }
        }
        return isCandidate
    }

    /**
     * 找到最近的可点击祖先（不含自身；自身是否可点击由调用方判定）。
     *
     * S3 修复：回溯过程中途经的非可点击祖先会被逐级回收，避免 parent 链泄漏。
     * 返回的对象是 [node] 之外的独立节点（调用方用后需回收）。
     */
    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = try { node.parent } catch (_: Exception) { null }
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            val parent = try { current.parent } catch (_: Exception) { null }
            runCatching { current.recycle() } // 回收正在离开的祖先
            current = parent
            depth++
        }
        return null
    }

    /**
     * 执行点击：优先 performAction，失败则尝试点击父节点。
     *
     * S3 修复：内部取到的父节点用后回收，避免节点池泄漏。
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)) {
            return true
        }
        val parent = try { node.parent } catch (_: Exception) { null }
        val clicked = parent != null &&
            runCatching { parent.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        runCatching { parent?.recycle() }
        return clicked
    }
}

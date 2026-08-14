package cn.ggdoc.autoscroll.task

import cn.ggdoc.autoscroll.util.recycleCompat
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.config.AppConfig
import java.util.LinkedList

/**
 * 「看广告得金币」高风险任务。
 *
 * 工作原理：
 *  1) 周期性遍历当前窗口，寻找文案命中「看广告得金币 / 看视频领 / 领金币…」的可点击入口
 *  2) 命中后点击进入激励视频，随后进入「观看期」——期间暂停滚动，避免误触打断广告
 *  3) 观看期内周期性调用 [AdBlocker] 尝试点掉「关闭 / 跳过 / 领取」按钮回到原页面
 *
 * ⚠️ 风险提示：该行为属于典型的「广告激励作弊」特征，极易被风控识别，
 * 可能导致目标 APP 的账号被限权、清零金币甚至封号。仅在用户明确知情并同意后启用。
 */
object AdRewardTask {

    private const val TAG = "AdRewardTask"

    /** 单次观看的最长等待时长（ms），超时后强制回到滚动 */
    const val WATCH_TIMEOUT_MS = 45_000L

    /** 观看期内尝试关闭广告的轮询间隔（ms） */
    const val CLOSE_POLL_MS = 5_000L

    /** 点击入口后首次尝试关闭的延迟（ms），给广告足够的播放时间 */
    const val FIRST_CLOSE_DELAY_MS = 20_000L

    /** 命中文本的最大长度，避免把正文/标题误判成入口按钮 */
    private const val MAX_TEXT_LEN = 18

    /** 单次扫描收集的最大节点数 */
    private const val MAX_NODES = 260
    private const val MAX_DEPTH = 22

    /**
     * 扫描并点击「看广告得金币」入口。
     * @return 被点击的入口文案；未找到或点击失败时返回 null
     */
    fun clickRewardEntry(service: AccessibilityService): String? {
        val candidates = ArrayList<AccessibilityNodeInfo>()
        // root 在 try 外声明，finally 才能回收（S3）；且需在 return try 之前声明
        val root = service.rootInActiveWindow ?: return null
        return try {
            val keywords = AppConfig.getAdRewardKeywords(service)
            if (keywords.isEmpty()) return null

            collectNodes(root, candidates)

            // 命中文案越短越可能是按钮，优先点击
            val hit = candidates
                .mapNotNull { node ->
                    val label = AdNodeKit.labelOf(node)
                    if (label.isEmpty() || label.length > MAX_TEXT_LEN) null
                    else if (keywords.any { label.contains(it) }) node to label
                    else null
                }
                .minByOrNull { it.second.length }
                ?: return null

            val (node, label) = hit
            val target = AdNodeKit.clickableSelfOrAncestor(node) ?: node
            val clicked = AdNodeKit.click(target)
            // target 若来自祖先回溯（非 node 自身），用后回收，避免节点池泄漏
            if (target !== node) runCatching { target.recycleCompat() }
            if (clicked) {
                Log.i(TAG, "已点击激励入口：$label")
                label
            } else {
                Log.d(TAG, "入口点击失败：$label")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "激励入口扫描失败", e)
            null
        } finally {
            AdNodeKit.recycle(root, candidates)
        }
    }

    private fun collectNodes(root: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val visited = HashSet<AccessibilityNodeInfo>()
        visited.add(root)
        val queue = LinkedList<Pair<AccessibilityNodeInfo, Int>>()
        queue.offer(root to 0)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val (node, depth) = queue.poll() ?: continue
            if (depth > MAX_DEPTH) {
                if (node !== root) runCatching { node.recycleCompat() }
                continue
            }
            // 仅收集真正可点击的节点，减少误命中正文文本
            val match = AdNodeKit.labelOf(node).isNotEmpty() && node.isClickable
            if (match) out.add(node) // 候选由 clickRewardEntry 的 finally 回收
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (visited.add(child)) {
                    queue.offer(child to depth + 1)
                }
                // M2 修复：重复访问的节点不再在此处回收——它可能仍留在队列中待处理，
                // 提前 recycle 会导致出队后访问已回收节点抛 IllegalStateException。
                // 统一由「出队后未匹配即回收」与下方退出前清队回收兜底。
            }
            // 处理完当前节点后回收（保留 root 与候选）
            if (node !== root && !match) runCatching { node.recycleCompat() }
        }
        // M2 修复：达到 MAX_NODES 提前退出时，队列中残留的节点从未被处理，
        // 必须回收，否则 Android 13 以下节点池累积泄漏。
        while (queue.isNotEmpty()) {
            val (node, _) = queue.poll() ?: continue
            if (node !== root) runCatching { node.recycleCompat() }
        }
    }

}

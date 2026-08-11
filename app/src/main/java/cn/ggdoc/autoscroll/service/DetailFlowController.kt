package cn.ggdoc.autoscroll.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.human.HumanTiming
import cn.ggdoc.autoscroll.human.PageClassifier
import cn.ggdoc.autoscroll.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

/**
 * 详情流控制器：新闻 / 社交等「列表-详情」类 APP 的拟人浏览闭环。
 *
 * 一个周期 = 从上到下顺序点一条 → 详情页停留（按概率「读完」或「随机看一会」）→ 返回列表。
 * 可见条目点完后自动上滑一屏继续。全程由内部 [CoroutineScope] 的 suspend 步骤链驱动，
 * 可随时被 [cancel] 打断（停止滚动 / 服务销毁时调用）。
 *
 * 协程化要点：每个步骤是 suspend fun，步骤间用 `delay()` 衔接；
 * **节点回收必须在 delay 之前完成**（Android < 13 节点池有限），
 * 故所有 root/container/scrollable 的 recycle 放在 try-finally 内，
 * delay + 下一步骤调用放在 finally 之后。
 */
class DetailFlowController(private val service: AutoScrollAccessibilityService) {

    companion object {
        private const val TAG = "DetailFlow"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + AppLog.coroutineExceptionHandler)
    private var cycleJob: Job? = null

    @Volatile
    var isBusy = false
        private set

    /** 顺序点击游标：下一个要点的条目 top 坐标下限 */
    private var nextMinTopY = 0
    private var swipedThisCycle = false
    private var scrollsLeft = 0
    /** 单轮已点开条数，超过上限视为「未正确返回列表」主动收手 */
    private var cycleClicks = 0
    /** 单轮点击次数硬上限，防止返回失败后在详情页内乱点 */
    private val MAX_CYCLE_CLICKS = 12
    /** 详情页滚动方式：0=未探测 1=ACTION_SCROLL 可用 2=仅手势可用 */
    private var scrollMode = 0
    private var onDone: (() -> Unit)? = null

    /**
     * 执行一个完整浏览周期，结束后回调 [done]（用于安排下一轮间隔）。
     * 若正在执行中则直接回调，不重复进入。
     */
    fun startOneCycle(done: () -> Unit) {
        if (isBusy) {
            done()
            return
        }
        isBusy = true
        onDone = done
        swipedThisCycle = false
        scrollMode = 0
        cycleClicks = 0

        // 进列表前先清一下弹窗广告
        service.runAdBlockCheck()
        Log.d(TAG, "开始一轮详情浏览")
        cycleJob = scope.launch { stepPickAndClick() }
    }

    /** 打断当前周期（不会触发 done 回调） */
    fun cancel() {
        isBusy = false
        onDone = null
        cycleJob?.cancel()
        cycleJob = null
    }

    /** 服务销毁时调用：取消整个协程作用域。cancel() 仅取消当前周期，dispose() 彻底关闭 scope。 */
    fun dispose() {
        scope.cancel()
    }

    /** 重置顺序点击游标（每次新一轮滚动开始时调用） */
    fun resetCursor() {
        nextMinTopY = 0
        swipedThisCycle = false
        cycleClicks = 0
    }

    // ========== 内部步骤 ==========

    /** 保护策略 / 生效应用清单校验，不满足则直接结束本轮 */
    private fun stillAllowed(): Boolean = service.isFlowAllowedToAct()

    private fun finish() {
        isBusy = false
        cycleJob = null
        val cb = onDone
        onDone = null
        cb?.invoke()
    }

    /** delay 后若周期已被取消或服务已停止则不再推进 */
    private suspend fun proceedOrReturn(delayMs: Long): Boolean {
        delay(delayMs)
        return isBusy && AutoScrollAccessibilityService.isScrolling
    }

    /** 第 1 步：在列表里从上到下挑一条可见条目并点开 */
    private suspend fun stepPickAndClick() {
        if (!stillAllowed()) { finish(); return }
        val root = service.rootInActiveWindow ?: run { finish(); return }
        val container = NodeFinder.findScrollable(root) ?: root
        // 下一步骤信息：try-finally 回收节点后再 delay + 推进
        var nextDelay = 0L
        var nextStep: (suspend () -> Unit)? = null
        try {
            val containerRect = Rect().also { container.getBoundsInScreen(it) }
            if (containerRect.width() <= 0 || containerRect.height() <= 0) { finish(); return }

            // S1 修复：capture 会把 root 下所有遍历到的节点回收（除 root 外）。container 是
            // root 的后代，若不加 keep 会被回收，导致下方 collectListItems(container) 访问
            // 已回收节点（Android < 13 抛异常 / 返回脏数据）。故把 container 作为 keep 保活。
            val snapshot = ScreenSnapshot.capture(root, containerRect.height(), keep = container)
            service.submitFingerprintFromFlow(snapshot.fingerprint)

            val pageType = snapshot.pageType()
            if (pageType != PageClassifier.PageType.LIST) {
                Log.d(TAG, "页面判定为 $pageType（非列表页），疑似未返回列表，结束本轮")
                nextMinTopY = 0
                finish(); return
            }
            if (cycleClicks >= MAX_CYCLE_CLICKS) {
                Log.w(TAG, "本轮点击次数过多，疑似未返回列表，结束本轮")
                nextMinTopY = 0
                finish(); return
            }

            val density = service.resources.displayMetrics.density
            val minItemHeight = (80 * density).toInt()
            val items = NodeFinder.collectListItems(container, containerRect, minItemHeight)
            val pick = items.firstOrNull { it.rect.top >= nextMinTopY - 20 }

            if (pick == null) {
                if (swipedThisCycle) {
                    Log.d(TAG, "暂无可点条目，本轮结束")
                    finish(); return
                }
                swipedThisCycle = true
                nextMinTopY = 0
                service.swipeUpOnNodeOrScreen(container)
                nextDelay = Random.nextLong(900, 1400)
                nextStep = { stepPickAndClick() }
                return
            }

            // 释放本次未选中的其他候选条目节点（避免节点池占用）
            items.forEach { if (it !== pick) runCatching { it.node.recycle() } }
            nextMinTopY = pick.rect.bottom - 10
            cycleClicks++
            clickItem(pick)
            pick.node.recycle()
            service.countDetailBrowsed()
            Log.d(TAG, "点开条目 @(${pick.rect.centerX()}, ${pick.rect.centerY()})")
            nextDelay = Random.nextLong(1000, 1800)
            nextStep = { stepDwell() }
        } finally {
            // S3 修复：回收 root 与 container。Android 13 以下节点池有限，
            // 不回收会导致 rootInActiveWindow 逐渐返回 null、详情流静默失效。
            // 必须在 delay 之前回收，否则节点在整个 delay 期间被持有。
            runCatching { container.recycle() }
            if (container !== root) runCatching { root.recycle() }
        }
        // 节点已回收，安全进入 delay + 下一步骤
        if (nextStep != null && proceedOrReturn(nextDelay)) {
            nextStep!!()
        }
    }

    private fun clickItem(item: NodeFinder.ListItem) {
        var target: AccessibilityNodeInfo? = item.node
        var depth = 0
        while (target != null && depth < 4) {
            val node = target
            val clicked = node.isClickable &&
                runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
            val parent = try { node.parent } catch (_: Exception) { null }
            // 回收当前节点：item.node 归调用方（stepPickAndClick）回收，其余在此回收
            if (node !== item.node) runCatching { node.recycle() }
            if (clicked) {
                // 点击成功：刚取出的 parent 本应成为下一轮 target，这里必须一并回收，
                // 否则每次成功点击泄漏 1 个节点，Android 13 以下节点池会被耗尽
                // （表现为 rootInActiveWindow 逐渐返回 null、详情流静默失效）。
                if (parent != null && parent !== item.node) runCatching { parent.recycle() }
                return
            }
            target = parent
            depth++
        }
        if (target != null && target !== item.node) runCatching { target.recycle() }
        // 兜底：手势点按条目中心
        service.tapScreen(item.rect.centerX().toFloat(), item.rect.centerY().toFloat())
    }

    /** 第 2 步：详情页停留——按概率「读完」或「随机看一会」 */
    private suspend fun stepDwell() {
        if (!stillAllowed()) { finish(); return }
        service.runAdBlockCheck()

        // 社交场景：按点赞概率双击详情页（部分 APP 双击即点赞）
        // 场景解析走 service.resolvedScene：自动识别场景时按前台包名映射
        val scene = service.resolvedScene
        if (scene.supportAutoLike && AutoScrollAccessibilityService.autoLike &&
            Random.nextInt(100) < AutoScrollAccessibilityService.likeProbability
        ) {
            delay(Random.nextLong(500, 900))
            if (isBusy && AutoScrollAccessibilityService.isScrolling) service.performAutoLikeNow()
        }

        val readAll = Random.nextInt(100) < AutoScrollAccessibilityService.detailReadAllProbability
        if (readAll) {
            scrollsLeft = AutoScrollAccessibilityService.detailMaxScrolls
            if (proceedOrReturn(Random.nextLong(700, 1400))) stepScrollOnce()
        } else {
            // O4：详情页停留同样改用长尾分布 + 疲劳因子。
            val lo = AutoScrollAccessibilityService.detailDwellMin
            val hi = max(AutoScrollAccessibilityService.detailDwellMax, lo + 1)
            val dwellMs = HumanTiming.nextIntervalMs(
                minSec = lo,
                maxSec = hi,
                runningMinutes = AutoScrollAccessibilityService.runningMinutes.toFloat()
            )
            Log.d(TAG, "随机停留 ${dwellMs}ms 后返回")
            if (proceedOrReturn(dwellMs)) stepBack()
        }
    }

    /** 第 2a 步：逐屏读完详情页（能探测到底就提前返回） */
    private suspend fun stepScrollOnce() {
        if (!stillAllowed()) { finish(); return }
        if (scrollsLeft <= 0) {
            if (proceedOrReturn(Random.nextLong(1200, 3000))) stepBack()
            return
        }

        val root = service.rootInActiveWindow
        val scrollable = root?.let { NodeFinder.findScrollable(it) }
        var goBack = false
        try {
            if (scrollMode != 2) {
                val ok = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                if (ok) {
                    scrollMode = 1
                    scrollsLeft--
                } else if (scrollMode == 1) {
                    // 之前能滚、现在滚不动 => 已到底，读完了
                    Log.d(TAG, "详情页已读完（到底）")
                    goBack = true
                } else {
                    // ACTION_SCROLL 不可用（WebView 等），降级为手势翻页
                    scrollMode = 2
                }
            }
            if (scrollMode == 2 && !goBack) {
                service.swipeUpOnNodeOrScreen(scrollable)
                scrollsLeft--
            }
        } finally {
            // S3：回收本帧取到的 root 与 scrollable（必须在 delay 之前）
            runCatching { scrollable?.recycle() }
            if (scrollable !== root) runCatching { root?.recycle() }
        }
        if (goBack) {
            if (proceedOrReturn(Random.nextLong(1200, 3500))) stepBack()
        } else {
            if (proceedOrReturn(Random.nextLong(900, 1800))) stepScrollOnce()
        }
    }

    /** 第 3 步：返回列表 */
    private suspend fun stepBack() {
        if (!AutoScrollAccessibilityService.isScrolling) { finish(); return }
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.d(TAG, "返回列表")
        if (proceedOrReturn(Random.nextLong(700, 1300))) finish()
    }
}

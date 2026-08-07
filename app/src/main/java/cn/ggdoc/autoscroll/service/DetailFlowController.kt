package cn.ggdoc.autoscroll.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.human.HumanTiming
import cn.ggdoc.autoscroll.human.PageClassifier
import kotlin.math.max
import kotlin.random.Random

/**
 * 详情流控制器：新闻 / 社交等「列表-详情」类 APP 的拟人浏览闭环。
 *
 * 一个周期 = 从上到下顺序点一条 → 详情页停留（按概率「读完」或「随机看一会」）→ 返回列表。
 * 可见条目点完后自动上滑一屏继续。全程通过 Handler 延时链驱动，
 * 可随时被 [cancel] 打断（停止滚动 / 服务销毁时调用）。
 */
class DetailFlowController(private val service: AutoScrollAccessibilityService) {

    companion object {
        private const val TAG = "DetailFlow"
    }

    private val handler = Handler(Looper.getMainLooper())

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
        stepPickAndClick()
    }

    /** 打断当前周期（不会触发 done 回调） */
    fun cancel() {
        isBusy = false
        onDone = null
        handler.removeCallbacksAndMessages(null)
    }

    /** 重置顺序点击游标（每次新一轮滚动开始时调用） */
    fun resetCursor() {
        nextMinTopY = 0
        swipedThisCycle = false
        cycleClicks = 0
    }

    // ========== 内部步骤 ==========

    private fun post(delayMs: Long, block: () -> Unit) {
        if (!isBusy) return
        handler.postDelayed({
            if (isBusy && AutoScrollAccessibilityService.isScrolling) block()
        }, delayMs)
    }

    private fun finish() {
        isBusy = false
        handler.removeCallbacksAndMessages(null)
        val cb = onDone
        onDone = null
        cb?.invoke()
    }

    /** 保护策略 / 生效应用清单校验，不满足则直接结束本轮 */
    private fun stillAllowed(): Boolean = service.isFlowAllowedToAct()

    /** 第 1 步：在列表里从上到下挑一条可见条目并点开 */
    private fun stepPickAndClick() {
        if (!stillAllowed()) return finish()
        val root = service.rootInActiveWindow ?: return finish()
        val container = NodeFinder.findScrollable(root) ?: root
        val containerRect = Rect().also { container.getBoundsInScreen(it) }
        if (containerRect.width() <= 0 || containerRect.height() <= 0) return finish()

        // 列表页校验（O3）：改用多信号判定，不再只看 className 是否含 WebView。
        //
        // 只看 WebView 的老问题：今日头条、腾讯新闻、知乎的正文页是**原生 RecyclerView**
        // 渲染的，根本没有 WebView。结果详情页被当成列表页，接着在正文里
        // 「挑一条可点条目点开」——点到的是评论、关注、举报、相关推荐，行为彻底失控。
        //
        // 现在综合正文长度、长段落、详情动作词、可点条目数等信号联合判定，
        // 并且**只有明确判定为 LIST 才继续点**（UNKNOWN 一律保守收手）。
        val snapshot = ScreenSnapshot.capture(root, containerRect.height())
        // 顺带把指纹交给卡死检测：详情流走自己的节奏，不经过主循环
        service.submitFingerprintFromFlow(snapshot.fingerprint)

        val pageType = snapshot.pageType()
        if (pageType != PageClassifier.PageType.LIST) {
            Log.d(TAG, "页面判定为 $pageType（非列表页），疑似未返回列表，结束本轮")
            nextMinTopY = 0
            return finish()
        }
        // 单轮点击次数上限：超过说明大概率没有正确返回列表，主动收手以防乱点
        if (cycleClicks >= MAX_CYCLE_CLICKS) {
            Log.w(TAG, "本轮点击次数过多，疑似未返回列表，结束本轮")
            nextMinTopY = 0
            return finish()
        }

        val density = service.resources.displayMetrics.density
        val minItemHeight = (80 * density).toInt()
        val items = NodeFinder.collectListItems(container, containerRect, minItemHeight)
        val pick = items.firstOrNull { it.rect.top >= nextMinTopY - 20 }

        if (pick == null) {
            if (swipedThisCycle) {
                // 刚翻过一页仍没有新条目，结束本轮等待下次间隔
                Log.d(TAG, "暂无可点条目，本轮结束")
                return finish()
            }
            // 当前屏点完了：上滑一屏后重新收集
            swipedThisCycle = true
            nextMinTopY = 0
            service.swipeUpOnNodeOrScreen(container)
            post(Random.nextLong(900, 1400)) { stepPickAndClick() }
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
        post(Random.nextLong(1000, 1800)) { stepDwell() }
    }

    private fun clickItem(item: NodeFinder.ListItem) {
        var target: AccessibilityNodeInfo? = item.node
        var depth = 0
        while (target != null && depth < 4) {
            if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return
            }
            target = target.parent
            depth++
        }
        // 兜底：手势点按条目中心
        service.tapScreen(item.rect.centerX().toFloat(), item.rect.centerY().toFloat())
    }

    /** 第 2 步：详情页停留——按概率「读完」或「随机看一会」 */
    private fun stepDwell() {
        if (!stillAllowed()) return finish()
        service.runAdBlockCheck()

        // 社交场景：按点赞概率双击详情页（部分 APP 双击即点赞）
        val scene = SceneConfig.getScene(AutoScrollAccessibilityService.currentScene)
        if (scene.supportAutoLike && AutoScrollAccessibilityService.autoLike &&
            Random.nextInt(100) < AutoScrollAccessibilityService.likeProbability
        ) {
            post(Random.nextLong(500, 900)) { service.performAutoLikeNow() }
        }

        val readAll = Random.nextInt(100) < AutoScrollAccessibilityService.detailReadAllProbability
        if (readAll) {
            scrollsLeft = AutoScrollAccessibilityService.detailMaxScrolls
            post(Random.nextLong(700, 1400)) { stepScrollOnce() }
        } else {
            // O4：详情页停留同样改用长尾分布 + 疲劳因子。
            // 真人读文章的时长绝不是均匀分布——大部分扫两眼就走，
            // 偶尔碰到感兴趣的会读很久，且刷得越久越容易长时间发呆。
            val lo = AutoScrollAccessibilityService.detailDwellMin
            val hi = max(AutoScrollAccessibilityService.detailDwellMax, lo + 1)
            val dwellMs = HumanTiming.nextIntervalMs(
                minSec = lo,
                maxSec = hi,
                runningMinutes = AutoScrollAccessibilityService.runningMinutes
            )
            Log.d(TAG, "随机停留 ${dwellMs}ms 后返回")
            post(dwellMs) { stepBack() }
        }
    }

    /** 第 2a 步：逐屏读完详情页（能探测到底就提前返回） */
    private fun stepScrollOnce() {
        if (!stillAllowed()) return finish()
        if (scrollsLeft <= 0) {
            // 到达滚动上限，稍作停留后返回
            post(Random.nextLong(1200, 3000)) { stepBack() }
            return
        }

        val root = service.rootInActiveWindow
        val scrollable = root?.let { NodeFinder.findScrollable(it) }
        if (scrollMode != 2) {
            val ok = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
            if (ok) {
                scrollMode = 1
                scrollsLeft--
            } else if (scrollMode == 1) {
                // 之前能滚、现在滚不动 => 已到底，读完了
                Log.d(TAG, "详情页已读完（到底）")
                post(Random.nextLong(1200, 3500)) { stepBack() }
                return
            } else {
                // ACTION_SCROLL 不可用（WebView 等），降级为手势翻页
                scrollMode = 2
            }
        }
        if (scrollMode == 2) {
            service.swipeUpOnNodeOrScreen(scrollable)
            scrollsLeft--
        }
        post(Random.nextLong(900, 1800)) { stepScrollOnce() }
    }

    /** 第 3 步：返回列表 */
    private fun stepBack() {
        if (!AutoScrollAccessibilityService.isScrolling) return finish()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.d(TAG, "返回列表")
        post(Random.nextLong(700, 1300)) { finish() }
    }
}

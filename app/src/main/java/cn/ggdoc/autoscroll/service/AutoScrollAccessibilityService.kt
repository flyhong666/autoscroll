package cn.ggdoc.autoscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import android.widget.Toast
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.CustomGestureStep
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.task.AdBlocker
import cn.ggdoc.autoscroll.task.AdRewardTask
import cn.ggdoc.autoscroll.task.KeepAliveManager
import java.lang.ref.WeakReference
import java.util.Calendar
import kotlin.math.max
import kotlin.random.Random

/**
 * 无障碍服务 v3.0：全场景自动滚动 + 点赞 + 广告屏蔽 + 定时停止 + 多APP轮换
 */
class AutoScrollAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AutoScrollService"

        const val BROADCAST_STATE_CHANGED = "cn.ggdoc.autoscroll.STATE_CHANGED"
        const val BROADCAST_TASK_EVENT = "cn.ggdoc.autoscroll.TASK_EVENT"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_EVENT_MSG = "event_msg"
        const val EVENT_TIMED_STOP = "timed_stop"
        const val EVENT_APP_ROTATION = "app_rotation"
        const val EVENT_AD_BLOCK = "ad_block"
        const val EVENT_LIKE = "like"
        const val EVENT_AD_REWARD = "ad_reward"

        // 定时运行闹钟
        const val ACTION_SCHEDULE_START = "cn.ggdoc.autoscroll.SCHEDULE_START"
        const val ACTION_SCHEDULE_END = "cn.ggdoc.autoscroll.SCHEDULE_END"

        private var _instance: WeakReference<AutoScrollAccessibilityService>? = null
        val instance: AutoScrollAccessibilityService?
            get() = _instance?.get()

        @Volatile
        var isScrolling = false
            private set

        // 基础参数
        var minIntervalSeconds: Int = AppConfig.DEFAULT_MIN_INTERVAL
            private set
        var maxIntervalSeconds: Int = AppConfig.DEFAULT_MAX_INTERVAL
            private set
        var minDurationMs: Int = AppConfig.DEFAULT_MIN_DURATION
            private set
        var maxDurationMs: Int = AppConfig.DEFAULT_MAX_DURATION
            private set

        // 场景
        var currentScene: String = AppConfig.SCENE_SHORT_VIDEO
            private set

        // 扩展任务开关
        var autoLike: Boolean = AppConfig.DEFAULT_AUTO_LIKE
            private set
        var likeProbability: Int = AppConfig.DEFAULT_LIKE_PROBABILITY
            private set
        var adBlock: Boolean = AppConfig.DEFAULT_AD_BLOCK
            private set
        var timedStop: Boolean = AppConfig.DEFAULT_TIMED_STOP
            private set
        var timedStopMinutes: Int = AppConfig.DEFAULT_TIMED_STOP_MINUTES
            private set
        var appRotation: Boolean = AppConfig.DEFAULT_APP_ROTATION
            private set
        var rotationMinutes: Int = AppConfig.DEFAULT_ROTATION_MINUTES
            private set
        var keepScreenOn: Boolean = AppConfig.DEFAULT_KEEP_SCREEN_ON
            private set
        var allowedApps: Set<String> = emptySet()
            private set

        // 定时运行 / 保护
        var scheduleEnabled: Boolean = AppConfig.DEFAULT_SCHEDULE_ENABLED
            private set
        var scheduleStartMin: Int = AppConfig.DEFAULT_SCHEDULE_START_MIN
            private set
        var scheduleEndMin: Int = AppConfig.DEFAULT_SCHEDULE_END_MIN
            private set
        var batteryGuardEnabled: Boolean = AppConfig.DEFAULT_BATTERY_GUARD
            private set
        var batteryThreshold: Int = AppConfig.DEFAULT_BATTERY_THRESHOLD
            private set
        var wifiOnly: Boolean = AppConfig.DEFAULT_WIFI_ONLY
            private set

        // 看广告得金币（高风险）
        var adReward: Boolean = AppConfig.DEFAULT_AD_REWARD
            private set
        var adRewardMinutes: Int = AppConfig.DEFAULT_AD_REWARD_INTERVAL
            private set

        // 详情流（新闻 / 社交）
        var detailDwellMin: Int = AppConfig.DEFAULT_DETAIL_DWELL_MIN
            private set
        var detailDwellMax: Int = AppConfig.DEFAULT_DETAIL_DWELL_MAX
            private set
        var detailReadAllProbability: Int = AppConfig.DEFAULT_DETAIL_READ_ALL_PROBABILITY
            private set
        var detailMaxScrolls: Int = AppConfig.DEFAULT_DETAIL_MAX_SCROLLS
            private set

        // 自定义手势
        var customGestureType: String = AppConfig.DEFAULT_CUSTOM_GESTURE_TYPE
            private set
        var customTapX: Int = AppConfig.DEFAULT_CUSTOM_TAP_X
            private set
        var customTapY: Int = AppConfig.DEFAULT_CUSTOM_TAP_Y
            private set
        var customSwipeDistance: Int = AppConfig.DEFAULT_CUSTOM_SWIPE_DISTANCE
            private set

        // 自定义手势序列（可编排：手势 + 等待秒数，循环执行）
        var customGestureSequence: List<CustomGestureStep> = emptyList()
            private set

        /** 正处于激励视频观看期：此期间暂停滚动，避免打断广告 */
        @Volatile
        var isWatchingAdReward = false
            private set

        // 运行时统计
        var startTimestamp: Long = 0L
            private set
        var remainingSeconds: Long = 0L
            private set
        var scrollCount: Int = 0
            private set
        var likeCount: Int = 0
            private set
        var adBlockCount: Int = 0
            private set
        var adRewardCount: Int = 0
            private set

        /** 本次运行已持续的秒数（未运行时为 0） */
        val runningSeconds: Long
            get() = if (isScrolling && startTimestamp > 0)
                (System.currentTimeMillis() - startTimestamp) / 1000L else 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scrollTask: Runnable? = null
    private var timedStopRunnable: Runnable? = null
    private var rotationRunnable: Runnable? = null
    private var tickRunnable: Runnable? = null
    private var adRewardRunnable: Runnable? = null
    private var adRewardWatchRunnable: Runnable? = null

    @Volatile
    private var foregroundPackage: String? = null

    /** 当前场景的 APP 包名列表（用于轮换） */
    private val rotationList = mutableListOf<String>()
    private var rotationIndex = 0

    /** 详情流控制器（新闻 / 社交场景） */
    private val detailFlow = DetailFlowController(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance = WeakReference(this)
        Log.i(TAG, "无障碍服务已连接")
        loadConfigFromPrefs()
        if (scheduleEnabled) scheduleAlarms()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 操作记录中：把用户的点击 / 长按 / 滑动转发给记录器
        if (ActionRecorder.isRecording) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                    ActionRecorder.onAccessibilityEvent(this, event)
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != foregroundPackage) {
                foregroundPackage = pkg
                Log.d(TAG, "前台应用切换：$pkg")
                broadcastState()
            }
        }
        // 实时广告屏蔽（窗口内容变化时）
        if (adBlock && isScrolling &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            // 限频：避免每次内容变化都扫描
            if (SystemClock.elapsedRealtime() - lastAdScanTime > 2000) {
                lastAdScanTime = SystemClock.elapsedRealtime()
                handler.post { tryAdBlock() }
            }
        }
    }

    private var lastAdScanTime = 0L

    override fun onInterrupt() {
        stopScrolling()
    }

    // ========== 配置加载 ==========
    fun loadConfigFromPrefs() {
        minIntervalSeconds = AppConfig.getMinInterval(this)
        maxIntervalSeconds = AppConfig.getMaxInterval(this)
        minDurationMs = AppConfig.getMinDuration(this)
        maxDurationMs = AppConfig.getMaxDuration(this)
        currentScene = AppConfig.getCurrentScene(this)
        autoLike = AppConfig.isAutoLike(this)
        likeProbability = AppConfig.getLikeProbability(this)
        adBlock = AppConfig.isAdBlock(this)
        timedStop = AppConfig.isTimedStop(this)
        timedStopMinutes = AppConfig.getTimedStopMinutes(this)
        appRotation = AppConfig.isAppRotation(this)
        rotationMinutes = AppConfig.getRotationMinutes(this)
        keepScreenOn = AppConfig.isKeepScreenOn(this)
        allowedApps = AppConfig.getAllowedApps(this)
        scheduleEnabled = AppConfig.isScheduleEnabled(this)
        scheduleStartMin = AppConfig.getScheduleStartMin(this)
        scheduleEndMin = AppConfig.getScheduleEndMin(this)
        batteryGuardEnabled = AppConfig.isBatteryGuard(this)
        batteryThreshold = AppConfig.getBatteryThreshold(this)
        wifiOnly = AppConfig.isWifiOnly(this)
        adReward = AppConfig.isAdReward(this)
        adRewardMinutes = AppConfig.getAdRewardInterval(this)
        detailDwellMin = AppConfig.getDetailDwellMin(this)
        detailDwellMax = AppConfig.getDetailDwellMax(this)
        detailReadAllProbability = AppConfig.getDetailReadAllProbability(this)
        detailMaxScrolls = AppConfig.getDetailMaxScrolls(this)
        customGestureType = AppConfig.getCustomGestureType(this)
        customTapX = AppConfig.getCustomTapX(this)
        customTapY = AppConfig.getCustomTapY(this)
        customSwipeDistance = AppConfig.getCustomSwipeDistance(this)
        customGestureSequence = AppConfig.getCustomGestureSequence(this)

        // 重建轮换列表：若用户设置了生效应用清单，则在其范围内轮换
        rotationList.clear()
        rotationList.addAll(
            if (allowedApps.isNotEmpty()) allowedApps
            else SceneConfig.getScenePackages(currentScene)
        )
        rotationIndex = 0

        Log.d(
            TAG,
            "配置加载：scene=$currentScene, interval=${minIntervalSeconds}-${maxIntervalSeconds}s, " +
                    "like=$autoLike($likeProbability%), ad=$adBlock, " +
                    "timedStop=$timedStop(${timedStopMinutes}min), rotation=$appRotation(${rotationMinutes}min), " +
                    "adReward=$adReward(${adRewardMinutes}min)"
        )
    }

    // ========== 滚动控制 ==========
    fun startScrolling() {
        if (isScrolling) {
            Log.w(TAG, "已经在滚动中")
            return
        }
        loadConfigFromPrefs()

        val (valid, msg) = AppConfig.validate(
            minIntervalSeconds, maxIntervalSeconds,
            minDurationMs, maxDurationMs
        )
        if (!valid) {
            Log.e(TAG, "参数非法：$msg")
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        isScrolling = true
        startTimestamp = System.currentTimeMillis()
        scrollCount = 0
        likeCount = 0
        adBlockCount = 0
        adRewardCount = 0
        isWatchingAdReward = false
        detailFlow.resetCursor()
        Log.i(TAG, "开始自动滚动，场景=$currentScene")

        // 屏幕常亮
        if (keepScreenOn) {
            KeepAliveManager.acquire(this)
        }

        // 启动定时停止倒计时
        if (timedStop) {
            startTimedStopCountdown()
        }

        // 启动 APP 轮换
        if (appRotation && rotationList.isNotEmpty()) {
            startAppRotation()
        }

        // 启动「看广告得金币」周期任务（高风险，默认关闭）
        if (adReward) {
            scheduleAdReward()
        }

        // 启动每秒 tick（更新剩余时间）
        startTick()

        scheduleNextScroll(immediate = true)
        broadcastState()
    }

    fun stopScrolling() {
        if (!isScrolling) return
        isScrolling = false
        isWatchingAdReward = false
        detailFlow.cancel()
        scrollTask?.let { handler.removeCallbacks(it); scrollTask = null }
        timedStopRunnable?.let { handler.removeCallbacks(it); timedStopRunnable = null }
        rotationRunnable?.let { handler.removeCallbacks(it); rotationRunnable = null }
        tickRunnable?.let { handler.removeCallbacks(it); tickRunnable = null }
        adRewardRunnable?.let { handler.removeCallbacks(it); adRewardRunnable = null }
        adRewardWatchRunnable?.let { handler.removeCallbacks(it); adRewardWatchRunnable = null }
        customSeqRunnable?.let { handler.removeCallbacks(it); customSeqRunnable = null }

        KeepAliveManager.release()
        Log.i(
            TAG,
            "停止自动滚动（滚动=$scrollCount, 点赞=$likeCount, 广告屏蔽=$adBlockCount, 激励=$adRewardCount）"
        )
        broadcastState()
    }

    private fun scheduleNextScroll(immediate: Boolean = false) {
        if (!isScrolling) return
        scrollTask?.let { handler.removeCallbacks(it) }

        val delayMs = if (immediate) {
            0L
        } else {
            val lo = minIntervalSeconds
            val hi = maxIntervalSeconds
            val effectiveHi = if (hi > lo) hi else lo + 1
            var base = Random.nextInt(lo, effectiveHi).toLong() * 1000L
            // 自定义手势序列：外层节奏不得短于序列总时长，避免与序列内部循环抢拍
            if (currentScene == AppConfig.SCENE_CUSTOM && customGestureSequence.isNotEmpty()) {
                val seqTotal = customGestureSequence.sumOf { (it.waitSec.coerceAtLeast(0)).toLong() } * 1000L
                if (seqTotal > base) base = seqTotal
            }
            base
        }

        scrollTask = Runnable {
            val scene = SceneConfig.getScene(currentScene)
            // 新闻资讯：列表-详情拟人浏览（点开→浏览→返回），由详情流控制器接管本轮节奏
            if (scene.id == "news") {
                detailFlow.startOneCycle { scheduleNextScroll() }
            } else {
                doScrollAndTasks()
                scheduleNextScroll()
            }
        }
        handler.postDelayed(scrollTask!!, delayMs)
    }

    // ========== 单次滚动 + 任务执行 ==========
    private fun doScrollAndTasks() {
        if (!isScrolling) return

        // 激励视频观看期：暂停一切滑动 / 点赞，避免打断广告计时
        if (isWatchingAdReward) {
            Log.v(TAG, "激励视频观看中，跳过本次滚动")
            return
        }

        // 保护策略：时间窗口 / 低电量 / 仅 Wi-Fi
        if (isBlockedByPolicy()) {
            return
        }

        // 生效应用过滤：清单为空=不限制；否则仅允许清单内的应用
        if (allowedApps.isNotEmpty() && foregroundPackage != null &&
            !allowedApps.contains(foregroundPackage)
        ) {
            Log.v(TAG, "过滤：当前包名<$foregroundPackage>不在生效应用清单中，跳过")
            return
        }

        // 1. 广告屏蔽
        tryAdBlock()

        // 2. 根据场景执行对应手势（按手势模式分派，更细粒度）
        val scene = SceneConfig.getScene(currentScene)
        when (scene.mode) {
            SceneConfig.ScrollMode.IDLE -> {
                // 直播挂机：偶尔微互动防止被判定为僵尸号，完全不滑动
                performLiveKeepAlive()
            }
            SceneConfig.ScrollMode.PAGE -> {
                // 小说：翻页 + 偶尔小幅上滑兜底
                performPageTurn()
                scrollCount++
            }
            SceneConfig.ScrollMode.DOUBLE_COLUMN -> {
                // 社交动态：双列瀑布流交叉滑动（含点赞）
                performDoubleColumnScroll()
                scrollCount++
            }
            SceneConfig.ScrollMode.VERTICAL -> {
                if (currentScene == AppConfig.SCENE_CUSTOM && customGestureSequence.isNotEmpty()) {
                    // 自定义通用：按用户编排的手势序列逐步执行（含每步后的等待）
                    performCustomSequence()
                    scrollCount++
                } else {
                    // 短视频 / 新闻：整屏竖向滑动
                    performScroll()
                    scrollCount++
                }
            }
        }

        // 3. 自动点赞（直播 / 挂机场景除外）
        if (autoLike && scene.mode != SceneConfig.ScrollMode.IDLE) {
            tryAutoLike()
        }
    }

    private fun performScroll() {
        val scene = SceneConfig.getScene(currentScene)
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                performScreenGesture(scene)
                return
            }
            val scrollableNode = NodeFinder.findScrollable(rootNode)
            if (scrollableNode != null) {
                performGestureOnNode(scrollableNode, scene)
            } else {
                performScreenGesture(scene)
            }
            Log.d(TAG, "已执行滑动手势 (scene=${scene.id})")
        } catch (e: Exception) {
            Log.e(TAG, "滑动失败，回退屏幕手势", e)
            try { performScreenGesture(scene) } catch (e2: Exception) {
                Log.e(TAG, "屏幕手势也失败", e2)
            }
        }
    }

    /**
     * 社交动态：双列瀑布流交叉滑动。
     * 左右两列交替命中——偶数轮略偏左（命中右列），奇数轮略偏右（命中左列），
     * 配合场景中设定的上下滑动比例，模拟真人手指在信息流中上下扫动的手感。
     */
    private fun performDoubleColumnScroll() {
        val scene = SceneConfig.getScene(currentScene)
        try {
            val (w, h) = getScreenSize()
            if (w <= 0 || h <= 0) return
            val cross = scene.swipeCrossXRatio
            val centerX = if (doubleColumnToggle) {
                // 偶数轮：起始点略偏左，终点回到中线右侧 -> 命中右列卡片
                w * (0.5f - cross)
            } else {
                // 奇数轮：起始点略偏右，终点回到中线左侧 -> 命中左列卡片
                w * (0.5f + cross)
            }
            doubleColumnToggle = !doubleColumnToggle
            val startY = h * scene.swipeStartYRatio
            val endY = h * scene.swipeEndYRatio
            // 加入 ±6% 随机抖动，避免每次完全一致的轨迹
            val jitterX = centerX + (Random.nextFloat() - 0.5f) * w * 0.12f
            dispatchSwipe(
                jitterX, startY, jitterX, endY,
                randomDuration(minBias = 30L), "double_column"
            )
            Log.d(TAG, "社交双列滑动（${if (doubleColumnToggle) "左列" else "右列"}）")
        } catch (e: Exception) {
            Log.e(TAG, "双列滑动失败，回退普通滑动", e)
            performScroll()
        }
    }

    /** 双列交叉滑动的左右交替标志 */
    @Volatile
    private var doubleColumnToggle = false

    /**
     * 小说场景：点按屏幕翻页
     * - 85% 概率点右侧翻下一页（多数阅读器右侧为前进区）
     * - 10% 概率左滑翻页（兼容滑动翻页的阅读器）
     * - 5% 概率点左侧翻回上一页（模拟偶尔回看）
     */
    private fun performPageTurn() {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        val roll = Random.nextInt(100)
        when {
            roll < 85 -> {
                // 点右侧翻下一页
                val x = w * (0.72f + Random.nextFloat() * 0.18f)
                val y = h * (0.30f + Random.nextFloat() * 0.40f)
                tapScreen(x, y, 70L)
                Log.d(TAG, "小说场景：点右侧翻页")
            }
            roll < 95 -> {
                // 左滑翻页（兼容滑动翻页的阅读器）
                val centerX = w * 0.5f
                val startY = h * (0.35f + Random.nextFloat() * 0.30f)
                val startX = centerX + w * 0.25f
                val endX = centerX - w * 0.25f
                dispatchSwipe(startX, startY, endX, startY, randomDuration(), "novel_swipe")
                Log.d(TAG, "小说场景：左滑翻页")
            }
            else -> {
                // 点左侧翻回上一页
                val x = w * (0.05f + Random.nextFloat() * 0.20f)
                val y = h * (0.30f + Random.nextFloat() * 0.40f)
                tapScreen(x, y, 70L)
                Log.d(TAG, "小说场景：点左侧翻回")
            }
        }
    }

    /**
     * 直播挂机：偶尔进行微互动防止被判定为僵尸号
     * 80% 概率不操作，20% 概率在屏幕中上方做一次轻触
     */
    private fun performLiveKeepAlive() {
        if (Random.nextInt(100) >= 20) return
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        // 在屏幕上方 15%-25% 区域轻触，避免误触礼物/弹幕按钮
        val x = w * (0.30f + Random.nextFloat() * 0.40f)
        val y = h * (0.15f + Random.nextFloat() * 0.10f)
        tapScreen(x, y, 50L)
        Log.d(TAG, "直播挂机：微互动保活")
    }

    /**
     * 自定义场景：执行用户配置的手势
     * 支持：上滑/下滑/左滑/右滑/单击/双击
     */
    /**
     * 自定义通用场景：按用户编排的手势序列逐步执行。
     * 每一步执行后等待该步指定的秒数，再执行下一步；序列末尾自动从头循环。
     * 序列为空时降级为旧版单手势 / 整屏上滑。
     */
    private fun performCustomSequence() {
        val seq = customGestureSequence
        if (seq.isEmpty()) {
            performScroll()
            return
        }
        // 取消可能仍在进行中的上一次序列
        customSeqRunnable?.let { handler.removeCallbacks(it) }
        customSeqStep = 0
        runCustomStep(seq)
    }

    private var customSeqStep = 0
    private var customSeqRunnable: Runnable? = null

    private fun runCustomStep(seq: List<CustomGestureStep>) {
        if (customSeqStep >= seq.size) customSeqStep = 0 // 循环
        val step = seq[customSeqStep]
        customSeqStep++

        if (!step.isWaitOnly()) {
            executeSingleGesture(step)
        }

        val waitMs = (step.waitSec.coerceAtLeast(0) * 1000).toLong()
        if (customSeqStep < seq.size || true) {
            // 序列末尾仍等待后从头循环（由下一轮 doScrollAndTasks 重新触发，
            // 因此这里只等待本步间隔；最后一步的等待交给循环节律）
        }
        if (waitMs > 0) {
            customSeqRunnable = Runnable {
                // 仅当仍处于自定义场景且服务运行中才继续
                if (currentScene == AppConfig.SCENE_CUSTOM && isScrolling) {
                    runCustomStep(seq)
                }
            }
            handler.postDelayed(customSeqRunnable!!, waitMs)
        }
    }

    /** 执行序列中的单步手势 */
    private fun executeSingleGesture(step: CustomGestureStep) {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        val jitterX = (Random.nextFloat() - 0.5f) * 0.10f
        val jitterY = (Random.nextFloat() - 0.5f) * 0.10f
        val x = w * (step.xPct / 100f + jitterX).coerceIn(0.05f, 0.95f)
        val y = h * (step.yPct / 100f + jitterY).coerceIn(0.05f, 0.95f)
        val dist = step.distPct / 100f

        when (step.gesture) {
            CustomGestureStep.TYPE_TAP -> tapScreen(x, y, 60L)
            CustomGestureStep.TYPE_DOUBLE_TAP -> performDoubleClick(x, y)
            CustomGestureStep.TYPE_SWIPE_UP -> {
                val startY = (y + h * dist / 2f).coerceIn(0.10f * h, 0.95f * h)
                val endY = (startY - h * dist).coerceIn(0.05f * h, startY - 0.05f * h)
                dispatchSwipe(x, startY, x, endY, randomDuration(), "custom_swipe_up")
            }
            CustomGestureStep.TYPE_SWIPE_DOWN -> {
                val startY = (y - h * dist / 2f).coerceIn(0.05f * h, 0.90f * h)
                val endY = (startY + h * dist).coerceIn(startY + 0.05f * h, 0.95f * h)
                dispatchSwipe(x, startY, x, endY, randomDuration(), "custom_swipe_down")
            }
            CustomGestureStep.TYPE_SWIPE_LEFT -> {
                val startX = (x + w * dist / 2f).coerceIn(0.10f * w, 0.95f * w)
                val endX = (startX - w * dist).coerceIn(0.05f * w, startX - 0.05f * w)
                dispatchSwipe(startX, y, endX, y, randomDuration(), "custom_swipe_left")
            }
            CustomGestureStep.TYPE_SWIPE_RIGHT -> {
                val startX = (x - w * dist / 2f).coerceIn(0.05f * w, 0.90f * w)
                val endX = (startX + w * dist).coerceIn(startX + 0.05f * w, 0.95f * w)
                dispatchSwipe(startX, y, endX, y, randomDuration(), "custom_swipe_right")
            }
            else -> performScreenGesture(SceneConfig.getScene(currentScene))
        }
    }

    /**
     * 兼容旧版单手势入口（无序列时使用）。
     */
    private fun performCustomGesture() {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        val xPct = customTapX / 100f
        val yPct = customTapY / 100f
        val distPct = customSwipeDistance / 100f
        // 加入小幅随机偏移（±5%）使手势更自然
        val jitterX = (Random.nextFloat() - 0.5f) * 0.10f
        val jitterY = (Random.nextFloat() - 0.5f) * 0.10f

        when (customGestureType) {
            AppConfig.GESTURE_TAP -> {
                val x = w * (xPct + jitterX).coerceIn(0.05f, 0.95f)
                val y = h * (yPct + jitterY).coerceIn(0.05f, 0.95f)
                tapScreen(x, y, 60L)
                Log.d(TAG, "自定义手势：单击 (${"%.0f".format(xPct * 100)}%, ${"%.0f".format(yPct * 100)}%)")
            }
            AppConfig.GESTURE_DOUBLE_TAP -> {
                val x = w * (xPct + jitterX).coerceIn(0.05f, 0.95f)
                val y = h * (yPct + jitterY).coerceIn(0.05f, 0.95f)
                performDoubleClick(x, y)
                Log.d(TAG, "自定义手势：双击 (${"%.0f".format(xPct * 100)}%, ${"%.0f".format(yPct * 100)}%)")
            }
            AppConfig.GESTURE_SWIPE_UP -> {
                val cx = w * (xPct + jitterX).coerceIn(0.10f, 0.90f)
                val startY = h * ((yPct + distPct / 2f) + jitterY).coerceIn(0.10f, 0.95f)
                val endY = (startY - h * distPct).coerceIn(0.05f, startY - 0.05f * h)
                dispatchSwipe(cx, startY, cx, endY, randomDuration(), "custom_swipe_up")
                Log.d(TAG, "自定义手势：上滑")
            }
            AppConfig.GESTURE_SWIPE_DOWN -> {
                val cx = w * (xPct + jitterX).coerceIn(0.10f, 0.90f)
                val startY = h * ((yPct - distPct / 2f) + jitterY).coerceIn(0.05f, 0.90f)
                val endY = (startY + h * distPct).coerceIn(startY + 0.05f * h, 0.95f * h)
                dispatchSwipe(cx, startY, cx, endY, randomDuration(), "custom_swipe_down")
                Log.d(TAG, "自定义手势：下滑")
            }
            AppConfig.GESTURE_SWIPE_LEFT -> {
                val cy = h * (yPct + jitterY).coerceIn(0.10f, 0.90f)
                val startX = w * ((xPct + distPct / 2f) + jitterX).coerceIn(0.10f, 0.95f)
                val endX = (startX - w * distPct).coerceIn(0.05f, startX - 0.05f * w)
                dispatchSwipe(startX, cy, endX, cy, randomDuration(), "custom_swipe_left")
                Log.d(TAG, "自定义手势：左滑")
            }
            AppConfig.GESTURE_SWIPE_RIGHT -> {
                val cy = h * (yPct + jitterY).coerceIn(0.10f, 0.90f)
                val startX = w * ((xPct - distPct / 2f) + jitterX).coerceIn(0.05f, 0.90f)
                val endX = (startX + w * distPct).coerceIn(startX + 0.05f * w, 0.95f * w)
                dispatchSwipe(startX, cy, endX, cy, randomDuration(), "custom_swipe_right")
                Log.d(TAG, "自定义手势：右滑")
            }
            else -> {
                // 降级为上滑
                performScreenGesture(SceneConfig.getScene(currentScene))
            }
        }
    }

    private fun performGestureOnNode(node: AccessibilityNodeInfo, scene: SceneConfig.Scene) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) {
            performScreenGesture(scene)
            return
        }
        val centerX = (rect.left + rect.right) / 2.0f
        // 使用场景配置的上下滑动比例，让不同场景下滑动幅度有差异
        val startY = rect.top + rect.height() * scene.swipeStartYRatio
        val endY = rect.top + rect.height() * scene.swipeEndYRatio
        val randomX = centerX + Random.nextFloat() * rect.width() * 0.4f - rect.width() * 0.2f
        val randomStartY = startY + Random.nextFloat() * 50f - 25f
        val randomEndY = endY + Random.nextFloat() * 50f - 25f
        dispatchSwipe(randomX, randomStartY, randomX, randomEndY, randomDuration(), "node")
    }

    private fun performScreenGesture(scene: SceneConfig.Scene) {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        val centerX = w / 2f
        val startY = h * scene.swipeStartYRatio
        val endY = h * scene.swipeEndYRatio
        val randomX = centerX + Random.nextFloat() * w * 0.3f - w * 0.15f
        val randomStartY = startY + Random.nextFloat() * 100f - 50f
        val randomEndY = endY + Random.nextFloat() * 100f - 50f
        dispatchSwipe(randomX, randomStartY, randomX, randomEndY, randomDuration(minBias = 50L), "screen")
    }

    private fun dispatchSwipe(
        startX: Float, startY: Float, endX: Float, endY: Float,
        durationMs: Long, source: String
    ) {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                Log.v(TAG, "[$source] 手势完成 ${durationMs}ms")
            }
            override fun onCancelled(g: GestureDescription?) {
                Log.w(TAG, "[$source] 手势被取消")
                if (source != "screen") handler.post { performScreenGesture(SceneConfig.getScene(currentScene)) }
            }
        }
        if (!dispatchGesture(gesture, callback, handler)) {
            Log.e(TAG, "[$source] dispatchGesture 返回 false")
        }
    }

    private fun randomDuration(minBias: Long = 0L): Long {
        val lo = minDurationMs + minBias.toInt()
        val hi = max(maxDurationMs, lo + 1)
        return Random.nextInt(lo, hi).toLong()
    }

    // ========== 自动点赞（双击屏幕中央） ==========
    private fun tryAutoLike() {
        val scene = SceneConfig.getScene(currentScene)
        if (!scene.supportAutoLike) return

        // 概率判定
        if (Random.nextInt(100) >= likeProbability) return

        handler.postDelayed({ performAutoLikeNow() }, 500)
    }

    private fun performDoubleClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        // 双击：第一段 0-80ms，第二段 100-180ms（duration = 80ms）
        val stroke1 = GestureDescription.StrokeDescription(path, 0L, 80L)
        val stroke2 = GestureDescription.StrokeDescription(path, 100L, 80L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ========== 供详情流 / 翻页复用的手势接口 ==========

    /** 单指点按 */
    fun tapScreen(x: Float, y: Float, durationMs: Long = 60L) {
        dispatchSwipe(x, y, x, y, durationMs, "tap")
    }

    /** 在指定节点区域内上滑；节点为空或不可用时全屏上滑 */
    fun swipeUpOnNodeOrScreen(node: AccessibilityNodeInfo?, scene: SceneConfig.Scene = SceneConfig.getScene(currentScene)) {
        try {
            if (node != null) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    performGestureOnNode(node, scene)
                    return
                }
            }
            performScreenGesture(scene)
        } catch (e: Exception) {
            Log.e(TAG, "上滑手势失败", e)
        }
    }

    /** 立即执行一次点赞双击（概率由调用方判定） */
    fun performAutoLikeNow() {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        val centerX = w * (0.5f + Random.nextFloat() * 0.2f - 0.1f)
        val centerY = h * (0.55f + Random.nextFloat() * 0.2f - 0.1f)
        performDoubleClick(centerX, centerY)
        likeCount++
        Log.d(TAG, "已自动点赞（累计 $likeCount）")
        sendTaskEvent(EVENT_LIKE, getString(R.string.toast_liked))
    }

    /** 广告屏蔽入口（供详情流在周期节点调用） */
    fun runAdBlockCheck() = tryAdBlock()

    /** 保护策略 + 生效应用清单综合校验 */
    fun isFlowAllowedToAct(): Boolean {
        if (isBlockedByPolicy()) return false
        val pkg = foregroundPackage
        if (allowedApps.isNotEmpty() && pkg != null && !allowedApps.contains(pkg)) return false
        return true
    }

    /** 详情流每点开一条计入「滚动次数」统计 */
    fun countDetailBrowsed() {
        scrollCount++
    }

    // ========== 广告屏蔽 ==========
    private fun tryAdBlock() {
        if (!adBlock) return
        val closed = AdBlocker.scanAndClose(this)
        if (closed > 0) {
            adBlockCount += closed
            Log.d(TAG, "广告屏蔽：本次关闭 $closed 个（累计 $adBlockCount）")
            sendTaskEvent(EVENT_AD_BLOCK, getString(R.string.toast_ad_blocked))
        }
    }

    // ========== 看广告得金币（高风险，默认关闭） ==========

    /** 安排下一次「看广告得金币」尝试 */
    private fun scheduleAdReward() {
        if (!isScrolling || !adReward) return
        adRewardRunnable?.let { handler.removeCallbacks(it) }
        val task = Runnable { tryAdReward() }
        adRewardRunnable = task
        handler.postDelayed(task, adRewardMinutes * 60_000L)
        Log.d(TAG, "已安排激励任务，${adRewardMinutes} 分钟后尝试")
    }

    private fun tryAdReward() {
        if (!isScrolling || !adReward) return
        // 保护策略生效时（如低电量 / 非 Wi-Fi）不做激励任务
        if (isBlockedByPolicy()) {
            scheduleAdReward()
            return
        }
        // 生效应用过滤：不在清单内的应用不尝试
        if (allowedApps.isNotEmpty() && foregroundPackage != null &&
            !allowedApps.contains(foregroundPackage)
        ) {
            scheduleAdReward()
            return
        }

        val label = AdRewardTask.clickRewardEntry(this)
        if (label == null) {
            Log.d(TAG, "未找到激励入口，等待下个周期")
            scheduleAdReward()
            return
        }

        adRewardCount++
        isWatchingAdReward = true
        Log.i(TAG, "已进入激励视频：$label（累计 $adRewardCount）")
        sendTaskEvent(EVENT_AD_REWARD, getString(R.string.toast_ad_reward, label))
        broadcastState()
        startAdRewardWatch()
    }

    /** 观看期：暂停滚动，周期性尝试点掉「关闭 / 跳过」按钮回到原页面 */
    private fun startAdRewardWatch() {
        adRewardWatchRunnable?.let { handler.removeCallbacks(it) }
        val deadline = SystemClock.elapsedRealtime() + AdRewardTask.WATCH_TIMEOUT_MS
        val poll = object : Runnable {
            override fun run() {
                if (!isScrolling) {
                    isWatchingAdReward = false
                    return
                }
                val closed = AdBlocker.scanAndClose(this@AutoScrollAccessibilityService)
                if (closed > 0) adBlockCount += closed
                if (closed > 0 || SystemClock.elapsedRealtime() >= deadline) {
                    finishAdRewardWatch()
                } else {
                    handler.postDelayed(this, AdRewardTask.CLOSE_POLL_MS)
                }
            }
        }
        adRewardWatchRunnable = poll
        handler.postDelayed(poll, AdRewardTask.FIRST_CLOSE_DELAY_MS)
    }

    private fun finishAdRewardWatch() {
        isWatchingAdReward = false
        adRewardWatchRunnable?.let { handler.removeCallbacks(it); adRewardWatchRunnable = null }
        Log.d(TAG, "激励视频观看结束，恢复滚动")
        broadcastState()
        scheduleAdReward()
    }

    // ========== 定时停止 ==========
    private fun startTimedStopCountdown() {
        val totalMs = timedStopMinutes * 60 * 1000L
        timedStopRunnable?.let { handler.removeCallbacks(it) }
        timedStopRunnable = Runnable {
            Log.i(TAG, "定时停止触发")
            sendTaskEvent(EVENT_TIMED_STOP, getString(R.string.toast_timed_stop_triggered))
            stopScrolling()
            // 通知悬浮窗服务也停止
            sendBroadcast(Intent("cn.ggdoc.autoscroll.STOP_FROM_ACCESSIBILITY").setPackage(packageName))
        }
        handler.postDelayed(timedStopRunnable!!, totalMs)
    }

    // ========== 多 APP 轮换 ==========
    private fun startAppRotation() {
        val intervalMs = rotationMinutes * 60 * 1000L
        rotationRunnable?.let { handler.removeCallbacks(it) }
        rotationRunnable = object : Runnable {
            override fun run() {
                if (!isScrolling) return
                rotationIndex = (rotationIndex + 1) % rotationList.size
                val targetPkg = rotationList[rotationIndex]
                Log.d(TAG, "轮换切换到：$targetPkg")
                sendTaskEvent(EVENT_APP_ROTATION, getString(R.string.toast_app_rotation, targetPkg))
                // 通过 Intent 启动目标 APP
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动 $targetPkg 失败", e)
                }
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(rotationRunnable!!, intervalMs)
    }

    // ========== 每秒 tick：更新剩余时间 + 刷新统计看板 ==========
    private fun startTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = object : Runnable {
            override fun run() {
                if (!isScrolling) return
                if (timedStop) {
                    val elapsed = (System.currentTimeMillis() - startTimestamp) / 1000
                    val total = (timedStopMinutes * 60).toLong()
                    remainingSeconds = (total - elapsed).coerceAtLeast(0)
                }
                // 每秒广播一次，让统计看板与悬浮窗刷新（含运行时长）
                broadcastState()
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(tickRunnable!!)
    }

    // ========== 屏幕尺寸 ==========
    private fun getScreenSize(): Pair<Int, Int> {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                display?.getRealMetrics(dm)
                (dm?.widthPixels ?: 0) to (dm?.heightPixels ?: 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取屏幕尺寸失败", e)
            0 to 0
        }
    }

    // ========== 广播 ==========
    private fun broadcastState() {
        sendBroadcast(Intent(BROADCAST_STATE_CHANGED).setPackage(packageName))
    }

    private fun sendTaskEvent(type: String, msg: String) {
        val intent = Intent(BROADCAST_TASK_EVENT).setPackage(packageName).apply {
            putExtra(EXTRA_EVENT_TYPE, type)
            putExtra(EXTRA_EVENT_MSG, msg)
        }
        sendBroadcast(intent)
    }

    // ========== 定时运行 / 保护策略 ==========

    /** 任务页保存后调用：重新加载配置并安排/取消定时闹钟 */
    fun onScheduleConfigChanged() {
        loadConfigFromPrefs()
        if (scheduleEnabled) scheduleAlarms() else cancelAlarms()
    }

    /** 由 ScheduleReceiver 在「开始时间」触发：若在窗口内且未运行则自动开始 */
    fun autoStartBySchedule() {
        if (!scheduleEnabled) return
        if (isWithinWindow() && !isScrolling) {
            startScrolling()
            Toast.makeText(this, "定时开始：自动滚动已启动", Toast.LENGTH_SHORT).show()
        }
    }

    /** 由 ScheduleReceiver 在「结束时间」触发：自动停止 */
    fun autoStopBySchedule() {
        if (isScrolling) {
            stopScrolling()
            Toast.makeText(this, "定时结束：已自动停止", Toast.LENGTH_SHORT).show()
        }
    }

    /** 安排下一次开始/结束闹钟（每日精确触发） */
    private fun scheduleAlarms() {
        cancelAlarms()
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        // Android 12+ 用户可收回精确闹钟权限（Android 14 起新安装默认不授予），
        // 未授权时 setExactAndAllowWhileIdle 会抛 SecurityException，降级为非精确闹钟
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmMillis(scheduleStartMin),
                    makePendingIntent(ACTION_SCHEDULE_START)
                )
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmMillis(scheduleEndMin),
                    makePendingIntent(ACTION_SCHEDULE_END)
                )
            } else {
                Log.w(TAG, "无精确闹钟权限，降级为普通闹钟")
                am.set(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmMillis(scheduleStartMin),
                    makePendingIntent(ACTION_SCHEDULE_START)
                )
                am.set(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmMillis(scheduleEndMin),
                    makePendingIntent(ACTION_SCHEDULE_END)
                )
            }
            Log.i(TAG, "定时闹钟已安排：${formatMinute(scheduleStartMin)} ~ ${formatMinute(scheduleEndMin)}")
        } catch (e: Exception) {
            Log.e(TAG, "安排定时闹钟失败", e)
        }
    }

    private fun cancelAlarms() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        am.cancel(makePendingIntent(ACTION_SCHEDULE_START))
        am.cancel(makePendingIntent(ACTION_SCHEDULE_END))
    }

    private fun makePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, ScheduleReceiver::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val reqCode = if (action == ACTION_SCHEDULE_START) 1 else 2
        return PendingIntent.getBroadcast(this, reqCode, intent, flags)
    }

    /** 下一个目标分钟对应的触发时刻（今天若已过则顺延到明天） */
    private fun nextAlarmMillis(targetMin: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetMin / 60)
            set(Calendar.MINUTE, targetMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** 当前是否处于定时运行窗口内（支持跨午夜） */
    private fun isWithinWindow(): Boolean {
        if (!scheduleEnabled) return true
        val now = nowMinute()
        return if (scheduleStartMin <= scheduleEndMin) {
            now in scheduleStartMin..scheduleEndMin
        } else {
            now >= scheduleStartMin || now <= scheduleEndMin
        }
    }

    private fun nowMinute(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** 综合保护策略：任一不满足则暂停本次滚动 */
    private fun isBlockedByPolicy(): Boolean {
        if (scheduleEnabled && !isWithinWindow()) return true
        if (batteryGuardEnabled && !isBatteryOk()) return true
        if (wifiOnly && !isWifiConnected()) return true
        return false
    }

    private fun isBatteryOk(): Boolean {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return bm.isCharging || pct >= batteryThreshold
    }

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(net) ?: return false
            return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return info.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun formatMinute(min: Int): String =
        String.format("%02d:%02d", min / 60, min % 60)

    override fun onDestroy() {
        stopScrolling()
        _instance?.clear()
        if (_instance?.get() == null) _instance = null
        super.onDestroy()
    }
}

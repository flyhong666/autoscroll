package cn.ggdoc.autoscroll.service

import android.accessibilityservice.AccessibilityService
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
import cn.ggdoc.autoscroll.config.StatsStore
import cn.ggdoc.autoscroll.human.HumanGestureDispatcher
import cn.ggdoc.autoscroll.human.HumanTiming
import cn.ggdoc.autoscroll.human.StuckDetector
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.task.AdBlocker
import cn.ggdoc.autoscroll.task.AdNodeKit
import cn.ggdoc.autoscroll.task.KeepAliveManager
import cn.ggdoc.autoscroll.util.CrashMonitor
import cn.ggdoc.autoscroll.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
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

        /** 卡死自恢复动作（O2） */
        const val EVENT_STUCK_RECOVER = "stuck_recover"

        /** 手势派发后等多久再采集指纹：手势时长上限 + 渲染缓冲 */
        private const val STUCK_CHECK_BUFFER_MS = 900

        /** 桌面小部件刷新最小间隔：tick 广播频繁，widget 刷新降频省电（M9） */
        private const val WIDGET_REFRESH_MIN_MS = 30_000L

        /** 自定义序列 waitSec=0 步骤的最小间隔：防全 0 序列忙循环轰炸手势 */
        private const val MIN_CUSTOM_STEP_GAP_MS = 400L

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

        // 定时运行 / 保护
        var scheduleEnabled: Boolean = AppConfig.DEFAULT_SCHEDULE_ENABLED
            private set
        var scheduleWindows: List<Pair<Int, Int>> =
            listOf(AppConfig.DEFAULT_SCHEDULE_START_MIN to AppConfig.DEFAULT_SCHEDULE_END_MIN)
            private set
        var batteryGuardEnabled: Boolean = AppConfig.DEFAULT_BATTERY_GUARD
            private set
        var batteryThreshold: Int = AppConfig.DEFAULT_BATTERY_THRESHOLD
            private set
        var wifiOnly: Boolean = AppConfig.DEFAULT_WIFI_ONLY
            private set

        // 应用黑白名单
        var appFilterMode: String = AppConfig.DEFAULT_APP_FILTER_MODE
            private set
        var appFilterList: Set<String> = emptySet()
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

        /** 本次运行中「详情页浏览」次数（详情流场景） */
        var detailCount: Int = 0
            private set

        /** 本次运行已持续的秒数（未运行时为 0） */
        val runningSeconds: Long
            get() = if (isScrolling && startTimestamp > 0)
                (System.currentTimeMillis() - startTimestamp) / 1000L else 0L

        /** 本次运行已持续的分钟数，供疲劳曲线计算 */
        val runningMinutes: Long get() = runningSeconds / 60L

        /** 当前是否被卡死检测判定为「内容无变化」 */
        @Volatile
        var stuckSameCount: Int = 0
            private set
    }

    /** 手势派发专用 Handler：仅传给 HumanGestureDispatcher.dispatchSwipe/dispatchDoubleTap，
     *  用于 dispatchGesture 回调线程化。不用于其他延时调度（那些已改为协程）。 */
    private val gestureHandler = Handler(Looper.getMainLooper())

    /** Service 级协程作用域：所有延时调度（滚动/卡死检测/定时停止/自定义序列/广告扫描/点赞）
     *  均通过此 scope.launch + delay 驱动。stopScrolling 取消各 Job，onDestroy 取消整个 scope。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + AppLog.coroutineExceptionHandler)

    private var scrollJob: Job? = null
    private var timedStopJob: Job? = null
    private var autoLikeJob: Job? = null
    private var adBlockScanJob: Job? = null
    private var fallbackJob: Job? = null

    // ========== Service 拆分：4 个独立 Controller ==========
    // 通过 object 表达式实现各自的 ServiceFace 接口，避免 Service 类头膨胀。
    // 使用 lazy 保证上下文准备完毕后再初始化。

    private val statsFace = object : StatsController.ServiceFace {
        override val isScrolling: Boolean get() = AutoScrollAccessibilityService.isScrolling
        override val runningSeconds: Long get() = AutoScrollAccessibilityService.runningSeconds
        override val timedStop: Boolean get() = AutoScrollAccessibilityService.timedStop
        override val timedStopMinutes: Int get() = AutoScrollAccessibilityService.timedStopMinutes
        override var remainingSeconds: Long
            get() = AutoScrollAccessibilityService.remainingSeconds
            set(value) { AutoScrollAccessibilityService.remainingSeconds = value }
        override val startTimestamp: Long get() = AutoScrollAccessibilityService.startTimestamp
        override fun broadcastState() = this@AutoScrollAccessibilityService.broadcastState()
    }
    private val statsController: StatsController by lazy { StatsController(this, statsFace) }

    private val rotationFace = object : RotationController.ServiceFace {
        override val isScrolling: Boolean get() = AutoScrollAccessibilityService.isScrolling
        override val appRotationEnabled: Boolean get() = AutoScrollAccessibilityService.appRotation
        override val rotationMinutes: Int get() = AutoScrollAccessibilityService.rotationMinutes
        override val rotationList: List<String> get() = this@AutoScrollAccessibilityService.rotationList
        override val foregroundPackage: String? get() = this@AutoScrollAccessibilityService.foregroundPackage
        override val packageName: String get() = this@AutoScrollAccessibilityService.packageName
        override fun sendTaskEvent(type: String, msg: String) =
            this@AutoScrollAccessibilityService.sendTaskEvent(type, msg)
        override fun resetStuckDetector() {
            this@AutoScrollAccessibilityService.let {
                stuckDetector.reset()
                AutoScrollAccessibilityService.stuckSameCount = 0
            }
        }
    }
    private val rotationController: RotationController by lazy { RotationController(this, rotationFace) }

    private val adRewardFace = object : AdRewardController.ServiceFace {
        override val isScrolling: Boolean get() = AutoScrollAccessibilityService.isScrolling
        override val adRewardEnabled: Boolean get() = AutoScrollAccessibilityService.adReward
        override val adRewardMinutes: Int get() = AutoScrollAccessibilityService.adRewardMinutes
        override var adRewardCount: Int
            get() = AutoScrollAccessibilityService.adRewardCount
            set(value) { AutoScrollAccessibilityService.adRewardCount = value }
        override var isWatchingAdReward: Boolean
            get() = AutoScrollAccessibilityService.isWatchingAdReward
            set(value) { AutoScrollAccessibilityService.isWatchingAdReward = value }
        override fun isBlockedByPolicy(): Boolean = this@AutoScrollAccessibilityService.isBlockedByPolicy()
        override fun tryAdBlockNow(): Int {
            val closed = AdBlocker.scanAndClose(this@AutoScrollAccessibilityService)
            if (closed > 0) {
                AutoScrollAccessibilityService.adBlockCount += closed
                this@AutoScrollAccessibilityService.sendTaskEvent(EVENT_AD_BLOCK, getString(R.string.toast_ad_blocked))
            }
            return closed
        }
        override fun sendTaskEvent(type: String, msg: String) =
            this@AutoScrollAccessibilityService.sendTaskEvent(type, msg)
        override fun broadcastState() = this@AutoScrollAccessibilityService.broadcastState()
    }
    private val adRewardController: AdRewardController by lazy { AdRewardController(this, adRewardFace) }

    private val scheduleFace = object : ScheduleController.ServiceFace {
        override val scheduleEnabled: Boolean get() = AutoScrollAccessibilityService.scheduleEnabled
        override val scheduleWindows: List<Pair<Int, Int>> get() = AutoScrollAccessibilityService.scheduleWindows
        override val isScrolling: Boolean get() = AutoScrollAccessibilityService.isScrolling
        override fun sendBroadcast(intent: Intent) = this@AutoScrollAccessibilityService.sendBroadcast(intent)
        override fun startScrolling() = this@AutoScrollAccessibilityService.startScrolling()
        override fun stopScrolling() = this@AutoScrollAccessibilityService.stopScrolling()
    }
    private val scheduleController: ScheduleController by lazy { ScheduleController(this, scheduleFace) }

    @Volatile
    private var foregroundPackage: String? = null

    /**
     * 当前实际生效的场景模板：场景为「自动识别」时按前台包名映射，
     * 其他场景原样返回。所有手势/详情流/点赞逻辑都应使用本属性而非 getScene(currentScene)。
     */
    val resolvedScene: SceneConfig.Scene
        get() = SceneConfig.resolveScene(currentScene, foregroundPackage)

    /** 当前场景的 APP 包名列表（用于轮换） */
    private val rotationList = mutableListOf<String>()
    private var rotationIndex = 0

    /** 卡死检测：内容指纹连续无变化时分级自恢复 */
    private val stuckDetector = StuckDetector()

    // 统计增量基线状态已移至 StatsController（lastPersisted* 字段）。

    /** 详情流控制器（新闻 / 社交场景） */
    private val detailFlow = DetailFlowController(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance = WeakReference(this)
        CrashMonitor.install(applicationContext)
        Log.i(TAG, "无障碍服务已连接")
        loadConfigFromPrefs()
        scheduleController.scheduleAlarms()
        maybeRecoverFromKill()
    }

    /**
     * 进程恢复（#5）：无障碍服务被系统回收后重新连上时，
     * 若此前正在滚动，则延迟一小段时间后自动恢复滚动。
     * 尊重定时窗口：若开启了定时运行且当前不在任一窗口内，则不强行恢复，
     * 仍交由定时 START 闹钟在窗口开始后启动。
     */
    private fun maybeRecoverFromKill() {
        if (!AppConfig.isRecoverEnabled(this)) return
        if (!AppConfig.isRecoverRunning(this)) return
        if (isScrolling) return
        val within = !scheduleEnabled || scheduleController.isWithinWindow()
        if (!within) {
            Log.i(TAG, "进程恢复：当前不在定时窗口内，交由定时闹钟处理，暂不恢复")
            return
        }
        Log.i(TAG, "进程恢复：检测到此前正在滚动，准备自动恢复")
        scope.launch {
            delay(1500L)
            if (!AppConfig.isRecoverRunning(this@AutoScrollAccessibilityService)) return@launch
            if (isScrolling) return@launch
            AppLog.i(TAG, "进程恢复：已自动恢复滚动")
            Toast.makeText(
                this@AutoScrollAccessibilityService,
                R.string.toast_process_recover,
                Toast.LENGTH_SHORT
            ).show()
            startScrolling()
        }
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

        // Toast 监听（功能3）：瞬时文本事件即时响应，不等下一轮轮询
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            handleToastEvent(event)
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != foregroundPackage) {
                foregroundPackage = pkg
                Log.d(TAG, "前台应用切换：$pkg")
                // 换了 APP 等于换了内容源，卡死计数清零避免误判
                stuckDetector.reset()
                stuckSameCount = 0
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
                adBlockScanJob = scope.launch { tryAdBlock() }
            }
        }
    }

    private var lastAdScanTime = 0L

    override fun onInterrupt() {
        stopScrolling()
    }

    // ========== Toast 监听（功能3） ==========

    /** 网络/加载失败关键词：命中后立即重置卡死检测并安排快速重试 */
    private val networkFailWords = listOf(
        "加载失败", "网络错误", "网络异常", "连接失败", "网络不给力", "请检查网络"
    )

    /** 激励视频「到账」关键词：命中后提前结束观看期恢复滚动 */
    private val rewardDoneWords = listOf(
        "已到账", "领取成功", "奖励已发放", "已获得", "金币已"
    )

    /**
     * 处理 Toast/通知文本事件，做即时响应（而非等待下一轮轮询）。
     *
     * - 网络/加载失败：重置卡死检测 + 2 秒后快速重试一次，缩短「卡在失败页」的时间；
     * - 激励视频到账：通知 AdRewardController 提前结束观看期（带最短观看保护）。
     */
    private fun handleToastEvent(event: AccessibilityEvent) {
        if (!isScrolling) return
        val text = event.text?.joinToString(" ")?.trim().orEmpty()
        if (text.isEmpty()) return
        Log.d(TAG, "Toast 监听：$text")

        // 网络/加载失败：立刻复位卡死状态并安排快速重试
        if (networkFailWords.any { text.contains(it) }) {
            Log.w(TAG, "检测到网络异常提示，重置卡死检测并快速重试")
            stuckDetector.reset()
            stuckSameCount = 0
            scope.launch {
                delay(2000L)
                if (isScrolling) doScrollAndTasks()
            }
            return
        }

        // 激励视频「到账」确认：提前结束观看期（controller 内部有最短观看保护）
        if (isWatchingAdReward && rewardDoneWords.any { text.contains(it) }) {
            adRewardController.onRewardReceived()
        }
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
        scheduleEnabled = AppConfig.isScheduleEnabled(this)
        scheduleWindows = AppConfig.getScheduleWindows(this)
        batteryGuardEnabled = AppConfig.isBatteryGuard(this)
        batteryThreshold = AppConfig.getBatteryThreshold(this)
        wifiOnly = AppConfig.isWifiOnly(this)
        appFilterMode = AppConfig.getAppFilterMode(this)
        appFilterList = AppConfig.getAppFilterList(this)
        adReward = AppConfig.isAdReward(this)
        adRewardMinutes = AppConfig.getAdRewardInterval(this)
        detailDwellMin = AppConfig.getDetailDwellMin(this)
        detailDwellMax = AppConfig.getDetailDwellMax(this)
        detailReadAllProbability = AppConfig.getDetailReadAllProbability(this)
        detailMaxScrolls = AppConfig.getDetailMaxScrolls(this)
        customGestureSequence = AppConfig.getCustomGestureSequence(this)

        // 轮换列表：由用户在「多 APP 轮换」里从已安装应用自选的包名池；
        // 场景本身仍只由用户在 UI 手动选择，工具对前台任何 App 都按当前场景参数来刷。
        rotationList.clear()
        rotationList.addAll(AppConfig.getRotationApps(this))
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
        detailCount = 0
        stuckSameCount = 0
        statsController.resetBaseline()
        stuckDetector.reset()
        isWatchingAdReward = false
        detailFlow.resetCursor()
        Log.i(TAG, "开始自动滚动，场景=$currentScene")

        // 进程恢复：记录「正在运行」，被系统回收后可由 onServiceConnected 自动恢复
        AppConfig.setRecoverRunning(this, true)

        if (keepScreenOn) KeepAliveManager.acquire(this)
        if (timedStop) startTimedStopCountdown()
        rotationController.start()
        adRewardController.schedule()
        statsController.startTick()

        scheduleNextScroll(immediate = true)
        // M9：开始是状态突变，立即刷新小部件
        broadcastState(forceWidget = true)
    }

    fun stopScrolling() {
        if (!isScrolling) return
        // 先落盘再翻转标志：runningSeconds 依赖 isScrolling，顺序反了会丢掉本次时长
        statsController.stop()
        rotationController.stop()
        adRewardController.stop()
        isScrolling = false
        isWatchingAdReward = false
        // 进程恢复：停止后清除「正在运行」标记，避免被回收后误恢复
        AppConfig.setRecoverRunning(this, false)
        detailFlow.cancel()
        scrollJob?.cancel(); scrollJob = null
        stuckCheckJob?.cancel(); stuckCheckJob = null
        timedStopJob?.cancel(); timedStopJob = null
        customSeqJob?.cancel(); customSeqJob = null
        autoLikeJob?.cancel(); autoLikeJob = null
        adBlockScanJob?.cancel(); adBlockScanJob = null
        fallbackJob?.cancel(); fallbackJob = null

        KeepAliveManager.release()
        remainingSeconds = 0
        Log.i(
            TAG,
            "停止自动滚动（滚动=$scrollCount, 点赞=$likeCount, 广告屏蔽=$adBlockCount, 激励=$adRewardCount）"
        )
        // M9：停止是状态突变，立即刷新小部件
        broadcastState(forceWidget = true)
    }

    private fun scheduleNextScroll(immediate: Boolean = false) {
        if (!isScrolling) return
        scrollJob?.cancel()

        val delayMs = if (immediate) {
            0L
        } else {
            // O4：间隔改用「对数正态 + 疲劳曲线 + 偶发长驻留」三层叠加。
            var base = HumanTiming.nextIntervalMs(
                minSec = minIntervalSeconds,
                maxSec = maxIntervalSeconds,
                runningMinutes = runningMinutes.toFloat()
            )
            // 自定义手势序列：外层节奏不得短于序列总时长，避免与序列内部循环抢拍
            if (currentScene == AppConfig.SCENE_CUSTOM && customGestureSequence.isNotEmpty()) {
                val seqTotal = customGestureSequence.sumOf { (it.waitSec.coerceAtLeast(0)).toLong() } * 1000L
                if (seqTotal > base) base = seqTotal
            }
            base
        }

        scrollJob = scope.launch {
            delay(delayMs)
            if (!isScrolling) return@launch
            val scene = resolvedScene
            // 新闻 / 社交：列表-详情拟人浏览（点开→浏览→返回），由详情流控制器接管本轮节奏；
            // 详情流开关关闭时退化为纯滑动（任务页可配置）
            if (scene.useDetailFlow && AppConfig.isDetailFlowEnabled(this@AutoScrollAccessibilityService)) {
                detailFlow.startOneCycle { scheduleNextScroll() }
            } else {
                doScrollAndTasks()
                scheduleNextScroll()
            }
        }
    }

    // ========== 单次滚动 + 任务执行 ==========
    private fun doScrollAndTasks() {
        if (!isScrolling) return

        // 激励视频观看期：暂停一切滑动 / 点赞，避免打断广告计时
        if (isWatchingAdReward) {
            Log.v(TAG, "激励视频观看中，跳过本次滚动")
            return
        }

        // 应用黑白名单：当前前台 APP 不在允许范围内则跳过本次滚动
        if (isBlockedByAppFilter()) {
            return
        }

        // 保护策略：时间窗口 / 低电量 / 仅 Wi-Fi
        if (isBlockedByPolicy()) {
            return
        }

        // 1. 广告屏蔽
        tryAdBlock()

        // 2. 根据场景执行对应手势（按手势模式分派，更细粒度）
        val scene = resolvedScene
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
            SceneConfig.ScrollMode.VERTICAL -> {
                // 自定义通用：按用户编排的手势序列逐步执行（含每步后的等待）
                val isCustomSequence = currentScene == AppConfig.SCENE_CUSTOM &&
                    customGestureSequence.isNotEmpty()
                if (isCustomSequence) {
                    performCustomSequence()
                    scrollCount++
                } else {
                    // 短视频 / 新闻：整屏竖向滑动
                    performScroll()
                    scrollCount++
                }
            }
        }

        // 3. 自动点赞（直播 / 挂机场景除外；自定义序列由用户精确编排，
        //    叠加自动点赞会干扰序列意图，如序列刚点了「下载」又弹一个双击，故跳过）
        val isCustomSequence = currentScene == AppConfig.SCENE_CUSTOM &&
            customGestureSequence.isNotEmpty()
        if (autoLike && scene.mode != SceneConfig.ScrollMode.IDLE && !isCustomSequence) {
            tryAutoLike()
        }

        // 4. 卡死检测（O2）：手势派发后延时取指纹，判断内容到底有没有变
        if (scene.mode != SceneConfig.ScrollMode.IDLE) {
            scheduleStuckCheck()
        }
    }

    // ========== O2：内容指纹 + 无变化自恢复 ==========

    private var stuckCheckJob: Job? = null

    /**
     * 手势后延时采集屏幕指纹。
     *
     * 为什么要延时：手势派发是异步的，立刻取快照拿到的还是滑动前的画面，
     * 会把「正常滑动」误判成卡死。这里等手势时长上限 + 渲染缓冲后再采。
     */
    private fun scheduleStuckCheck() {
        if (!AppConfig.isAutoRecover(this)) return
        stuckCheckJob?.cancel()
        val delay = (maxDurationMs + STUCK_CHECK_BUFFER_MS).toLong()
        stuckCheckJob = scope.launch {
            delay(delay)
            runStuckCheck()
        }
    }

    private fun runStuckCheck() {
        if (!isScrolling || isWatchingAdReward) return
        val root = try { rootInActiveWindow } catch (e: Exception) { null }
        val snapshot = try {
            ScreenSnapshot.capture(root, getScreenSize().second)
        } finally {
            runCatching { root?.recycle() }
        }
        if (!snapshot.isValid) return

        val action = stuckDetector.submit(snapshot.fingerprint)
        stuckSameCount = stuckDetector.consecutiveSame
        if (action == StuckDetector.Action.NONE) return

        Log.w(TAG, "内容连续 ${stuckDetector.consecutiveSame} 次无变化，执行恢复：$action")
        when (action) {
            StuckDetector.Action.CLOSE_POPUP -> {
                // 最轻的干预：多半是弹窗/浮层把滑动手势吃掉了
                val closed = AdBlocker.scanAndClose(this)
                if (closed > 0) {
                    adBlockCount += closed
                    sendTaskEvent(EVENT_AD_BLOCK, getString(R.string.toast_ad_blocked))
                }
                sendTaskEvent(EVENT_STUCK_RECOVER, getString(R.string.toast_stuck_close_popup))
            }
            StuckDetector.Action.PRESS_BACK -> {
                // 可能误入了详情页 / 设置页，退回上一层
                performGlobalAction(GLOBAL_ACTION_BACK)
                sendTaskEvent(EVENT_STUCK_RECOVER, getString(R.string.toast_stuck_press_back))
            }
            StuckDetector.Action.RESTART_APP -> {
                // APP 可能已被系统回收，或彻底卡死，重新拉起
                restartForegroundApp()
                sendTaskEvent(EVENT_STUCK_RECOVER, getString(R.string.toast_stuck_restart_app))
            }
            StuckDetector.Action.NONE -> Unit
        }
        stuckDetector.onRecoveryAttempted()
    }

    /** 重新拉起当前目标 APP（卡死恢复的最后手段） */
    private fun restartForegroundApp() {
        val pkg = foregroundPackage ?: rotationList.getOrNull(rotationIndex) ?: return
        if (pkg == packageName) return
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            Log.i(TAG, "卡死恢复：已重新拉起 $pkg")
        } catch (e: Exception) {
            Log.e(TAG, "卡死恢复：重启 $pkg 失败", e)
        }
    }

    private fun performScroll() {
        val scene = resolvedScene
        val rootNode = try { rootInActiveWindow } catch (e: Exception) { null } ?: run {
            // 取不到 root：退化为全屏手势，且无需回收节点
            try { performScreenGesture(scene) } catch (e2: Exception) {
                Log.e(TAG, "屏幕手势也失败", e2)
            }
            return
        }
        // scrollableNode 由 findScrollable 返回且本方法持有，用后必须回收；
        // 早期版本既不回收 rootNode 也不回收 scrollableNode，节点池耗尽后
        // rootInActiveWindow 会返回 null（表现为「跑一会儿就不动了」）。
        val scrollableNode = NodeFinder.findScrollable(rootNode)
        try {
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
        } finally {
            // S3：回收本帧取到的节点（Android < 13 节点池有限）。
            // scrollableNode 可能是 rootNode 自身或其后裔，避免重复回收。
            runCatching { scrollableNode?.recycle() }
            if (scrollableNode !== rootNode) runCatching { rootNode.recycle() }
        }
    }

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
        // 在屏幕左上区域（高度 10%-18%、宽度 12%-37%）轻触保活，
        // 远离顶部居中的主播头像/关注按钮，也避开底部礼物/弹幕输入区。
        val x = w * (0.12f + Random.nextFloat() * 0.25f)
        val y = h * (0.10f + Random.nextFloat() * 0.08f)
        tapScreen(x, y, 50L)
        Log.d(TAG, "直播挂机：微互动保活")
    }

    /**
     * 自定义通用场景：按用户编排的手势序列逐步执行。
     * 每一步执行后等待该步指定的秒数，再执行下一步；序列末尾自动从头循环。
     * 序列为空时降级为整屏上滑。
     */
    private fun performCustomSequence() {
        val seq = customGestureSequence
        if (seq.isEmpty()) {
            performScroll()
            return
        }
        // S6 修复：自定义序列由 runCustomStep 的协程链自驱循环（每步 waitSec 后
        // 推进，到末尾 customSeqStep 归零循环）。早期实现每次外层 tick 都 cancel +
        // 重置游标再启动，内部循环被反复打断重来，等于双重调度且意图混乱。
        // 因此：若序列已在运行（customSeqJob 非 null），直接交给内部循环驱动，不重复启动；
        // 仅当序列尚未启动（或已因切场景/停止而自然终止）时才从头启动。
        if (customSeqJob != null) return
        customSeqStep = 0
        runCustomStep(seq)
    }

    private var customSeqStep = 0
    private var customSeqJob: Job? = null

    private fun runCustomStep(seq: List<CustomGestureStep>) {
        if (customSeqStep >= seq.size) customSeqStep = 0 // 循环
        val step = seq[customSeqStep]
        customSeqStep++

        if (!step.isWaitOnly()) {
            executeSingleGesture(step)
        }

        // 等待本步指定的秒数。waitSec=0 用最小手势间隔兜底：
        // 全部 waitSec=0 的序列（如 [点击, 上滑] 意图「紧接执行」）若逐条 1ms 推进，
        // 会退化成毫秒级忙循环轰炸 dispatchGesture，被系统限流且拖垮主线程。
        val waitMs = (step.waitSec.coerceAtLeast(0) * 1000).toLong()
        val delay = if (waitMs > 0) waitMs else MIN_CUSTOM_STEP_GAP_MS
        customSeqJob = scope.launch {
            delay(delay)
            // 仅当仍处于自定义场景且服务运行中才继续；否则终止循环并清理，
            // 便于下次重新进入自定义场景时由 performCustomSequence 正常重启。
            if (currentScene == AppConfig.SCENE_CUSTOM && isScrolling) {
                runCustomStep(seq)
            } else {
                customSeqJob = null
            }
        }
    }

    /**
     * 自定义序列配置变更后调用：取消正在运行的序列循环，下一 tick 用新序列重启。
     * 否则用户改完序列，运行中的循环仍按旧 seq 引用继续跑。
     */
    fun restartCustomSequenceIfRunning() {
        if (isScrolling && currentScene == AppConfig.SCENE_CUSTOM) {
            customSeqJob?.cancel()
            customSeqJob = null
        }
    }

    /** 执行序列中的单步手势 */
    private fun executeSingleGesture(step: CustomGestureStep) {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        // 「点击文本」走控件查找，不需要坐标计算
        if (step.isTapText()) {
            performTapText(step.textKeyword)
            return
        }
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
            else -> performScreenGesture(resolvedScene)
        }
    }

    /**
     * 点击屏幕上文案包含 [keyword] 的控件（自定义手势「点击文本」步骤）。
     *
     * 找不到匹配控件时跳过本步（记日志），不影响序列推进；
     * 节点生命周期严格管理：root 由本方法回收，findClickableByText 返回的
     * 目标节点可能是 root 自身，需避免双重回收。
     */
    private fun performTapText(keyword: String) {
        if (keyword.isBlank()) return
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        val target = try {
            AdNodeKit.findClickableByText(root, keyword)
        } catch (e: Exception) {
            Log.e(TAG, "点击文本：扫描失败", e)
            null
        } finally {
            runCatching { root.recycle() }
        }
        if (target == null) {
            Log.d(TAG, "点击文本：未找到「$keyword」，跳过本步")
            return
        }
        try {
            val rect = Rect()
            target.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                tapScreen(rect.centerX().toFloat(), rect.centerY().toFloat(), 70L)
                Log.d(TAG, "点击文本：命中「$keyword」@(${rect.centerX()}, ${rect.centerY()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "点击文本：执行失败", e)
        } finally {
            if (target !== root) runCatching { target.recycle() }
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

    /**
     * 派发滑动手势（O1：贝塞尔曲线 + 分段变速）。
     *
     * 原实现是 `moveTo → lineTo` 的一条**完全笔直、匀速**的直线，
     * 这是最容易被风控识别的特征——真人的手指做不到匀速直线。
     * 现在交给 [HumanGestureDispatcher]：
     * - 三次贝塞尔生成带弧度的轨迹 + 像素级微抖动（模拟生理震颤）
     * - API 26+ 用 continueStroke 分段拼接，实现「快起慢收」的变速
     * - 低版本自动降级为单段贝塞尔折线，仍保留弧度（不弹窗打扰用户）
     */
    private fun dispatchSwipe(
        startX: Float, startY: Float, endX: Float, endY: Float,
        durationMs: Long, source: String
    ) {
        HumanGestureDispatcher.dispatchSwipe(
            service = this,
            startX = startX, startY = startY,
            endX = endX, endY = endY,
            durationMs = durationMs,
            handler = gestureHandler
        ) { completed ->
            if (completed) {
                Log.v(TAG, "[$source] 手势完成 ${durationMs}ms")
            } else {
                Log.w(TAG, "[$source] 手势被取消")
                // 仅在「节点滚动手势」被系统取消时，回退一次全屏上滑兜底。
                // tap / 双击 / 自定义手势被取消不应退化成全屏上滑，否则会与详情流的
                // 协程延时链抢拍，破坏拟人节奏。
                if (source == "node") {
                    fallbackJob = scope.launch { performScreenGesture(resolvedScene) }
                }
            }
        }
    }

    /**
     * 单次手势时长（O4）：对数正态分布取样，而非均匀随机。
     * 真人滑动时长集中在一个「舒适值」附近，偶尔出现明显更慢的一次，
     * 均匀分布则是每个值等概率——统计上一眼假。
     */
    private fun randomDuration(minBias: Long = 0L): Long {
        val lo = minDurationMs + minBias.toInt()
        return HumanTiming.nextDurationMs(lo, maxDurationMs)
    }

    // ========== 自动点赞（双击屏幕中央） ==========
    private fun tryAutoLike() {
        val scene = resolvedScene
        if (!scene.supportAutoLike) return

        // 概率判定
        if (Random.nextInt(100) >= likeProbability) return

        // 取消上一次未触发的点赞延时：滑动间隔短于 500ms 时（概率命中相邻两轮），
        // 若不取消会连排两个 500ms 点赞造成连击，likeCount 统计也会虚高
        autoLikeJob?.cancel()
        autoLikeJob = scope.launch {
            delay(500)
            performAutoLikeNow()
        }
    }

    /**
     * 双击（O1）：两次落点不再是同一个像素，间隔也不再是固定 100ms。
     * 真人双击的两次触点总会有几像素偏差、间隔在 80~160ms 之间浮动。
     */
    private fun performDoubleClick(x: Float, y: Float) {
        HumanGestureDispatcher.dispatchDoubleTap(this, x, y, gestureHandler)
    }

    // ========== 供详情流 / 翻页复用的手势接口 ==========

    /** 单指点按 */
    fun tapScreen(x: Float, y: Float, durationMs: Long = 60L) {
        dispatchSwipe(x, y, x, y, durationMs, "tap")
    }

    /** 在指定节点区域内上滑；节点为空或不可用时全屏上滑 */
    fun swipeUpOnNodeOrScreen(node: AccessibilityNodeInfo?, scene: SceneConfig.Scene = resolvedScene) {
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

    /** 保护策略 + 看广告期综合校验 */
    fun isFlowAllowedToAct(): Boolean {
        if (isWatchingAdReward) return false
        if (isBlockedByPolicy()) return false
        return true
    }

    /** 详情流每点开一条计入「滚动次数」与独立的「详情浏览」统计 */
    fun countDetailBrowsed() {
        scrollCount++
        detailCount++
    }

    /** 供详情流复用：提交一次屏幕指纹给卡死检测（详情流有自己的节奏，不走主循环） */
    fun submitFingerprintFromFlow(fingerprint: Long) {
        if (!AppConfig.isAutoRecover(this)) return
        if (fingerprint == StuckDetector.NO_HASH) return
        val action = stuckDetector.submit(fingerprint)
        stuckSameCount = stuckDetector.consecutiveSame
        if (action != StuckDetector.Action.NONE) {
            Log.w(TAG, "详情流：内容无变化，执行恢复 $action")
            when (action) {
                StuckDetector.Action.CLOSE_POPUP -> runAdBlockCheck()
                StuckDetector.Action.PRESS_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                StuckDetector.Action.RESTART_APP -> restartForegroundApp()
                StuckDetector.Action.NONE -> Unit
            }
            stuckDetector.onRecoveryAttempted()
        }
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

    // 看广告得金币（激励视频）逻辑已整体移至 AdRewardController。

    // ========== 定时停止 ==========
    private fun startTimedStopCountdown() {
        val totalMs = timedStopMinutes.toLong() * 60 * 1000
        timedStopJob?.cancel()
        timedStopJob = scope.launch {
            delay(totalMs)
            Log.i(TAG, "定时停止触发")
            sendTaskEvent(EVENT_TIMED_STOP, getString(R.string.toast_timed_stop_triggered))
            stopScrolling()
            sendBroadcast(Intent("cn.ggdoc.autoscroll.STOP_FROM_ACCESSIBILITY").setPackage(packageName))
        }
    }

    // 多 APP 轮换逻辑已整体移至 RotationController。

    // 每秒 tick / 统计增量落盘 / 基线重置逻辑已整体移至 StatsController。

    /** 今日累计统计（供 UI 展示） */
    fun getTodayStats(): StatsStore.Stats = statsController.getTodayStats()

    /** 历史总计统计（供 UI 展示） */
    fun getTotalStats(): StatsStore.Stats = statsController.getTotalStats()

    // ========== 屏幕尺寸 ==========
    private fun getScreenSize(): Pair<Int, Int> {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                // Android 11（API 30）以下：用 WindowManager.defaultDisplay 取真实屏幕尺寸。
                // 注意：Context.getDisplay() 是 API 30 才有的方法，低版本调用会抛 NoSuchMethodError，
                // 因此这里必须用 defaultDisplay，不能用 display?.
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                dm.widthPixels to dm.heightPixels
            }
        } catch (t: Throwable) {
            // 兜底用 Throwable：低版本若仍有意外（如 NoSuchMethodError 继承自 Error 而非 Exception），
            // catch(Exception) 接不住，会导致无障碍服务进程级崩溃。
            Log.e(TAG, "获取屏幕尺寸失败", t)
            0 to 0
        }
    }

    // ========== 广播 ==========
    private fun broadcastState(forceWidget: Boolean = false) {
        sendBroadcast(Intent(BROADCAST_STATE_CHANGED).setPackage(packageName))
        // M9 修复：broadcastState 每 1~5 秒被 tick 调用，每次都全量刷新桌面小部件
        // （Binder 调用 × 已放置数量）会明显耗电。这里降频到至少 30 秒一次；
        // 开始/停止等关键状态变化时由调用方传 forceWidget=true 立即刷新。
        if (!forceWidget) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastWidgetUpdateAt < WIDGET_REFRESH_MIN_MS) return
            lastWidgetUpdateAt = now
        }
        // 同步刷新桌面小部件（清单 #11）：状态变化即时推送到所有已放置的小部件
        runCatching { cn.ggdoc.autoscroll.widget.AutoScrollWidgetProvider.updateAll(this) }
    }

    private var lastWidgetUpdateAt = 0L

    private fun sendTaskEvent(type: String, msg: String) {
        val intent = Intent(BROADCAST_TASK_EVENT).setPackage(packageName).apply {
            putExtra(EXTRA_EVENT_TYPE, type)
            putExtra(EXTRA_EVENT_MSG, msg)
        }
        sendBroadcast(intent)
    }

    // ========== 定时运行 / 保护策略 ==========

    /** 任务页保存后调用：重新加载配置并安排/取消定时闹钟；同时让运行中的轮换/激励按新参数重启 */
    fun onScheduleConfigChanged() {
        loadConfigFromPrefs()
        scheduleController.onScheduleConfigChanged()
        if (isScrolling) {
            rotationController.onRotationConfigChanged()
            adRewardController.onConfigChanged()
        }
    }

    /** 由 ScheduleReceiver 在「开始时间」触发：若在窗口内且未运行则自动开始 */
    fun autoStartBySchedule() = scheduleController.autoStartBySchedule()

    /** 由 ScheduleReceiver 在「结束时间」触发：自动停止 */
    fun autoStopBySchedule() = scheduleController.autoStopBySchedule()

    // 定时闹钟（安排 / 取消 / PendingIntent 构造）逻辑已整体移至 ScheduleController。

    // 下一个目标分钟触发时刻已由 ScheduleController 计算。

    /**
     * 应用黑白名单：当前前台 APP 是否应被跳过（不滚动）。
     *
     * - off：不过滤，任何 APP 都滚
     * - whitelist：仅当 [foregroundPackage] 在 [appFilterList] 中才滚，其余跳过
     * - blacklist：仅当 [foregroundPackage] 在 [appFilterList] 中才跳过，其余滚
     *
     * 自身包名（工具本体）也按规则判定；列表为空时白名单模式视为"无允许项"而跳过，
     * 黑名单模式视为"无禁止项"而放行。
     */
    private fun isBlockedByAppFilter(): Boolean {
        if (appFilterMode == AppConfig.FILTER_OFF) return false
        val pkg = foregroundPackage ?: return false
        val inList = appFilterList.contains(pkg)
        return when (appFilterMode) {
            AppConfig.FILTER_WHITELIST -> !inList
            AppConfig.FILTER_BLACKLIST -> inList
            else -> false
        }
    }

    /** 综合保护策略：任一不满足则暂停本次滚动 */
    private fun isBlockedByPolicy(): Boolean {
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

    override fun onDestroy() {
        stopScrolling()
        disposeControllers()
        scope.cancel()
        _instance?.clear()
        if (_instance?.get() == null) _instance = null
        super.onDestroy()
    }

    /**
     * 统一销毁 4 个 Controller 的协程作用域。
     *
     * 仅在服务真正销毁时调用，**不**在 stopScrolling 调用——
     * 因为 stopScrolling 后可能再次 startScrolling 复用同一批 Controller，
     * 若在此取消 scope，下次 start 将无法再 launch 任何协程。
     */
    private fun disposeControllers() {
        statsController.dispose()
        rotationController.dispose()
        adRewardController.dispose()
        detailFlow.dispose()
    }
}

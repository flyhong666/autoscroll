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
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.task.AdBlocker
import cn.ggdoc.autoscroll.task.AdRewardTask
import cn.ggdoc.autoscroll.task.KeepAliveManager
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.LinkedList
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
        scrollTask?.let { handler.removeCallbacks(it); scrollTask = null }
        timedStopRunnable?.let { handler.removeCallbacks(it); timedStopRunnable = null }
        rotationRunnable?.let { handler.removeCallbacks(it); rotationRunnable = null }
        tickRunnable?.let { handler.removeCallbacks(it); tickRunnable = null }
        adRewardRunnable?.let { handler.removeCallbacks(it); adRewardRunnable = null }
        adRewardWatchRunnable?.let { handler.removeCallbacks(it); adRewardWatchRunnable = null }

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
            Random.nextInt(lo, effectiveHi).toLong() * 1000L
        }

        scrollTask = Runnable {
            doScrollAndTasks()
            scheduleNextScroll()
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

        // 2. 滑动（直播场景不滑动，只挂机）
        if (currentScene != AppConfig.SCENE_LIVE) {
            performScroll()
            scrollCount++
        }

        // 3. 自动点赞
        if (autoLike && currentScene != AppConfig.SCENE_LIVE) {
            tryAutoLike()
        }
    }

    private fun performScroll() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                performScreenGesture()
                return
            }
            val scrollableNode = findScrollableNode(rootNode)
            if (scrollableNode != null) {
                performGestureOnNode(scrollableNode)
            } else {
                performScreenGesture()
            }
            Log.d(TAG, "已执行滑动手势")
        } catch (e: Exception) {
            Log.e(TAG, "滑动失败，回退屏幕手势", e)
            try { performScreenGesture() } catch (e2: Exception) {
                Log.e(TAG, "屏幕手势也失败", e2)
            }
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val classes = listOf(
            "androidx.recyclerview.widget.RecyclerView",
            "android.support.v7.widget.RecyclerView",
            "android.widget.ListView",
            "android.widget.ScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.webkit.WebView",
            "android.support.v4.view.ViewPager",
            "androidx.viewpager.widget.ViewPager",
            "androidx.viewpager2.widget.ViewPager2"
        )
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.offer(node)
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val className = current.className?.toString().orEmpty()
            if (classes.any { className.contains(it, ignoreCase = true) }) return current
            if (current.isScrollable) return current
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.offer(it) }
            }
        }
        return null
    }

    private fun performGestureOnNode(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) {
            performScreenGesture()
            return
        }
        val centerX = (rect.left + rect.right) / 2.0f
        val startY = (rect.top + rect.bottom) * 0.85f
        val endY = (rect.top + rect.bottom) * 0.15f
        val randomX = centerX + Random.nextFloat() * rect.width() * 0.4f - rect.width() * 0.2f
        val randomStartY = startY + Random.nextFloat() * 50f - 25f
        val randomEndY = endY + Random.nextFloat() * 50f - 25f
        dispatchSwipe(randomX, randomStartY, randomX, randomEndY, randomDuration(), "node")
    }

    private fun performScreenGesture() {
        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return
        val centerX = w / 2f
        val startY = h * 0.85f
        val endY = h * 0.15f
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
                if (source != "screen") handler.post { performScreenGesture() }
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

        val (w, h) = getScreenSize()
        if (w <= 0 || h <= 0) return

        // 双击屏幕中央偏右下（模拟点赞位置）
        val centerX = w * (0.5f + Random.nextFloat() * 0.2f - 0.1f)
        val centerY = h * (0.55f + Random.nextFloat() * 0.2f - 0.1f)

        handler.postDelayed({
            performDoubleClick(centerX, centerY)
            likeCount++
            Log.d(TAG, "已自动点赞（累计 $likeCount）")
            sendTaskEvent(EVENT_LIKE, getString(R.string.toast_liked))
        }, 500)
    }

    private fun performDoubleClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        // 双击：第一段 0-80ms，第二段 100-180ms
        val stroke1 = GestureDescription.StrokeDescription(path, 0L, 80L)
        val stroke2 = GestureDescription.StrokeDescription(path, 100L, 180L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
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
        Log.i(TAG, "定时闹钟已安排：${formatMinute(scheduleStartMin)} ~ ${formatMinute(scheduleEndMin)}")
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

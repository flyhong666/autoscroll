package cn.ggdoc.autoscroll.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import cn.ggdoc.autoscroll.MainActivity
import cn.ggdoc.autoscroll.R
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 悬浮窗服务 v3.0：
 *  - 单击切换、长按拖动、边缘吸附、屏幕边界限制
 *  - 倒计时显示（定时停止启用时）
 *  - 接收无障碍服务的任务事件（点赞/广告/轮换提示）Toast
 *  - 接收无障碍服务「定时停止」广播，自动 stopSelf
 */
class FloatingWindowService : Service() {

    companion object {
        const val TAG = "FloatingWindowService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "floating_window_channel"

        const val ACTION_STOP_SERVICE = "cn.ggdoc.autoscroll.STOP_SERVICE"
        const val BROADCAST_STATE_CHANGED = "cn.ggdoc.autoscroll.STATE_CHANGED"
        const val ACTION_STOP_FROM_ACCESSIBILITY = "cn.ggdoc.autoscroll.STOP_FROM_ACCESSIBILITY"

        private var _instance: WeakReference<FloatingWindowService>? = null

        fun isRunning(): Boolean = _instance?.get() != null

        fun notifyStateChanged(context: Context) {
            context.sendBroadcast(Intent(BROADCAST_STATE_CHANGED).setPackage(context.packageName))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var controlButton: ImageView
    private lateinit var tvCountdown: TextView
    private var isAdded = false

    private lateinit var params: WindowManager.LayoutParams
    private var x = 0
    private var y = 100

    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var longPressTriggered = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        isDragging = true
        try {
            val vb = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vb?.vibrate(android.os.VibrationEffect.createOneShot(20L, 30))
            } else {
                @Suppress("DEPRECATION")
                vb?.vibrate(20L)
            }
        } catch (_: Exception) {
        }
    }

    /** 倒计时刷新（每秒） */
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000L)
        }
    }

    /** 接收无障碍服务的状态变化与任务事件广播 */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED -> {
                    // 无障碍服务状态变化（如定时停止）→ 刷新按钮
                    updateButtonState(showToast = false)
                }
                AutoScrollAccessibilityService.BROADCAST_TASK_EVENT -> {
                    val msg = intent.getStringExtra(AutoScrollAccessibilityService.EXTRA_EVENT_MSG)
                    val type = intent.getStringExtra(AutoScrollAccessibilityService.EXTRA_EVENT_TYPE)
                    if (!msg.isNullOrBlank()) {
                        Toast.makeText(this@FloatingWindowService, msg, Toast.LENGTH_SHORT).show()
                    }
                    if (type == AutoScrollAccessibilityService.EVENT_TIMED_STOP) {
                        // 定时停止：自动停止悬浮窗服务
                        handler.postDelayed({ stopSelf() }, 500)
                    }
                }
                ACTION_STOP_FROM_ACCESSIBILITY -> {
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _instance = WeakReference(this)
        Log.d(TAG, "悬浮窗服务创建")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingView()

        val filter = IntentFilter().apply {
            addAction(AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED)
            addAction(AutoScrollAccessibilityService.BROADCAST_TASK_EVENT)
            addAction(ACTION_STOP_FROM_ACCESSIBILITY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // 启动倒计时刷新
        handler.post(countdownRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateButtonState(showToast = false)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val scrolling = AutoScrollAccessibilityService.isScrolling
        val contentText = if (scrolling) {
            val remaining = AutoScrollAccessibilityService.remainingSeconds
            if (remaining > 0) {
                getString(R.string.notif_content_running, formatTime(remaining))
            } else {
                getString(R.string.notif_content)
            }
        } else {
            getString(R.string.notif_content)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)
        controlButton = floatingView.findViewById(R.id.controlButton)
        tvCountdown = floatingView.findViewById(R.id.tvCountdown)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = this@FloatingWindowService.x
            y = this@FloatingWindowService.y
        }

        controlButton.setOnTouchListener { _, event -> handleTouch(event) }

        updateButtonState(showToast = false)

        try {
            windowManager.addView(floatingView, params)
            isAdded = true
            Log.d(TAG, "悬浮窗已添加")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮窗失败", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = x
                initialY = y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                longPressTriggered = false
                handler.postDelayed(longPressRunnable, 250L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val moved = abs(dx) > 8f || abs(dy) > 8f

                if (moved && !longPressTriggered) {
                    handler.removeCallbacks(longPressRunnable)
                    isDragging = true
                }

                if (isDragging || longPressTriggered) {
                    updatePosition(initialX + dx.toInt(), initialY + dy.toInt())
                    return true
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                val wasDragging = isDragging || longPressTriggered
                isDragging = false
                longPressTriggered = false

                if (!wasDragging) {
                    toggleScrolling()
                } else {
                    snapToEdge()
                }
                return true
            }
        }
        return false
    }

    private fun updatePosition(newX: Int, newY: Int) {
        val (screenW, screenH) = getScreenSize()
        val btnW = floatingView.width.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.floating_button_size)
        val btnH = floatingView.height.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.floating_button_size)

        val margin = resources.getDimensionPixelSize(R.dimen.floating_button_margin)
        val maxX = max(0, screenW - btnW - margin)
        val maxY = max(0, screenH - btnH - margin * 2)

        x = min(max(margin, newX), maxX)
        y = min(max(margin, newY), maxY)

        params.x = x
        params.y = y
        if (isAdded) {
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (_: Exception) {
            }
        }
    }

    private fun snapToEdge() {
        val (screenW, _) = getScreenSize()
        val btnW = floatingView.width.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.floating_button_size)
        val center = x + btnW / 2
        val targetX = if (center < screenW / 2) {
            resources.getDimensionPixelSize(R.dimen.floating_button_margin)
        } else {
            max(
                resources.getDimensionPixelSize(R.dimen.floating_button_margin),
                screenW - btnW - resources.getDimensionPixelSize(R.dimen.floating_button_margin)
            )
        }
        val stepX = if (targetX == x) 0 else (targetX - x) / 8
        for (i in 1..8) {
            handler.postDelayed({ updatePosition(x + stepX, y) }, i * 12L)
        }
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return try {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        } catch (e: Exception) {
            Log.e(TAG, "获取屏幕尺寸失败", e)
            1080 to 2400
        }
    }

    private fun toggleScrolling() {
        val svc = AutoScrollAccessibilityService.instance
        if (svc == null) {
            Log.w(TAG, "无障碍服务未连接")
            Toast.makeText(this, R.string.toast_accessibility_disconnected, Toast.LENGTH_SHORT).show()
            return
        }
        if (AutoScrollAccessibilityService.isScrolling) {
            svc.stopScrolling()
        } else {
            svc.startScrolling()
        }
        updateButtonState(showToast = true)
    }

    private fun updateButtonState(showToast: Boolean) {
        val scrolling = AutoScrollAccessibilityService.isScrolling
        controlButton.setImageResource(
            if (scrolling) R.drawable.ic_stop else R.drawable.ic_play
        )
        if (showToast) {
            val msg =
                if (scrolling) R.string.toast_scroll_started else R.string.toast_scroll_stopped
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        // 更新通知
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, createNotification())
        } catch (_: Exception) {
        }
        notifyStateChanged(this)
    }

    /**
     * 更新悬浮窗上的倒计时显示
     */
    private fun updateCountdown() {
        val scrolling = AutoScrollAccessibilityService.isScrolling
        val remaining = AutoScrollAccessibilityService.remainingSeconds
        if (scrolling && remaining > 0) {
            tvCountdown.visibility = View.VISIBLE
            tvCountdown.text = formatTime(remaining)
        } else {
            tvCountdown.visibility = View.GONE
        }
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(countdownRunnable)

        AutoScrollAccessibilityService.instance?.stopScrolling()

        if (isAdded) {
            try {
                windowManager.removeView(floatingView)
                Log.d(TAG, "悬浮窗已移除")
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮窗失败", e)
            }
            isAdded = false
        }
        _instance?.clear()
        if (_instance?.get() == null) _instance = null
        notifyStateChanged(this)
    }
}

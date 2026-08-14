package cn.ggdoc.autoscroll.service

import cn.ggdoc.autoscroll.util.registerReceiverSafe
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
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.recorder.ScriptPlayer
import cn.ggdoc.autoscroll.ui.ScriptActivity
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 录制 / 回放悬浮控制条。
 *
 * - 录制模式：显示已捕获步数，点按结束录制并保存脚本
 * - 回放模式：显示进度（步数 / 循环），点按终止回放
 * - 长按可拖动，松手自动吸边
 */
class RecorderOverlayService : Service() {

    companion object {
        private const val TAG = "RecorderOverlay"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "recorder_overlay_channel"

        const val EXTRA_MODE = "mode"
        const val MODE_RECORD = "record"
        const val MODE_PLAY = "play"

        private var _instance: WeakReference<RecorderOverlayService>? = null

        fun isRunning(): Boolean = _instance?.get() != null

        fun start(context: Context, mode: String) {
            val intent = Intent(context, RecorderOverlayService::class.java)
                .putExtra(EXTRA_MODE, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var tvStatus: TextView
    private lateinit var tvAction: TextView
    private lateinit var dotView: View
    private lateinit var params: WindowManager.LayoutParams

    private var isAdded = false
    private var mode: String = MODE_RECORD

    private var x = 0
    private var y = 220
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var longPressTriggered = false

    /** 用户主动点按停止（用于区分「自然播完」与「手动终止」的提示文案） */
    private var manualStop = false

    /** 回放模式下由本服务获取的 WakeLock 标记，销毁时只释放自己获取的 */
    private var playWakeLockAcquired = false

    private val handler = Handler(Looper.getMainLooper())

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        isDragging = true
    }

    /** 录制模式下红点闪烁 */
    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (mode == MODE_RECORD) {
                dotView.alpha = if (dotView.alpha > 0.5f) 0.25f else 1f
            } else {
                dotView.alpha = 1f
            }
            handler.postDelayed(this, 550L)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ActionRecorder.BROADCAST_RECORDER_CHANGED -> {
                    refreshStatus()
                    if (mode == MODE_RECORD && !ActionRecorder.isRecording) stopSelf()
                }

                ScriptPlayer.BROADCAST_PLAYER_CHANGED -> {
                    refreshStatus()
                    if (mode == MODE_PLAY && !ScriptPlayer.isPlaying) {
                        if (!manualStop) {
                            Toast.makeText(
                                this@RecorderOverlayService,
                                R.string.toast_script_play_finished,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _instance = WeakReference(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlay()

        val filter = IntentFilter().apply {
            addAction(ActionRecorder.BROADCAST_RECORDER_CHANGED)
            addAction(ScriptPlayer.BROADCAST_PLAYER_CHANGED)
        }
        registerReceiverSafe(receiver, filter)
        handler.post(blinkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD
        if (mode == MODE_PLAY) enableKeepScreenForPlay()
        refreshStatus()
        return START_NOT_STICKY
    }

    /** 回放期间用户不触屏，需主动保持屏幕常亮 + CPU 不休眠，否则手势会中断 */
    private fun enableKeepScreenForPlay() {
        if (::params.isInitialized && isAdded) {
            if ((params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                try {
                    windowManager.updateViewLayout(overlayView, params)
                } catch (_: Exception) {
                }
            }
        }
        if (!cn.ggdoc.autoscroll.task.KeepAliveManager.isHeld()) {
            cn.ggdoc.autoscroll.task.KeepAliveManager.acquire(this)
            playWakeLockAcquired = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun legacyOverlayType(): Int = WindowManager.LayoutParams.TYPE_PHONE

    // ---------- 悬浮视图 ----------

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_recorder, null)
        tvStatus = overlayView.findViewById(R.id.tvRecorderStatus)
        tvAction = overlayView.findViewById(R.id.tvRecorderAction)
        dotView = overlayView.findViewById(R.id.recorderDot)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                legacyOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = this@RecorderOverlayService.x
            y = this@RecorderOverlayService.y
        }

        overlayView.setOnTouchListener { _, event -> handleTouch(event) }

        try {
            windowManager.addView(overlayView, params)
            isAdded = true
        } catch (e: Exception) {
            Log.e(TAG, "添加录制悬浮条失败", e)
            stopSelf()
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
                if ((abs(dx) > 8f || abs(dy) > 8f) && !longPressTriggered) {
                    handler.removeCallbacks(longPressRunnable)
                    isDragging = true
                }
                if (isDragging || longPressTriggered) {
                    updatePosition(initialX + dx.toInt(), initialY + dy.toInt())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val wasDragging = isDragging || longPressTriggered
                isDragging = false
                longPressTriggered = false
                if (!wasDragging) onTapped() else snapToEdge()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // M1 修复：系统抢占触摸（通知栏下拉、来电、其他窗口抢焦点）会收到 CANCEL，
                // 此时绝不能当「点击」处理，否则录制会被意外结束保存 / 回放被意外终止。
                // 只做清理：移除长按回调、复位状态。
                handler.removeCallbacks(longPressRunnable)
                isDragging = false
                longPressTriggered = false
                return true
            }
        }
        return false
    }

    private fun updatePosition(newX: Int, newY: Int) {
        val (screenW, screenH) = getScreenSize()
        val w = overlayView.width.takeIf { it > 0 } ?: 220
        val h = overlayView.height.takeIf { it > 0 } ?: 60
        val margin = resources.getDimensionPixelSize(R.dimen.floating_button_margin)
        x = min(max(margin, newX), max(margin, screenW - w - margin))
        y = min(max(margin, newY), max(margin, screenH - h - margin * 2))
        params.x = x
        params.y = y
        if (isAdded) {
            try {
                windowManager.updateViewLayout(overlayView, params)
            } catch (_: Exception) {
            }
        }
    }

    private fun snapToEdge() {
        val (screenW, _) = getScreenSize()
        val w = overlayView.width.takeIf { it > 0 } ?: 220
        val margin = resources.getDimensionPixelSize(R.dimen.floating_button_margin)
        val target = if (x + w / 2 < screenW / 2) margin else max(margin, screenW - w - margin)
        val step = (target - x) / 8
        for (i in 1..8) handler.postDelayed({ updatePosition(x + step, y) }, i * 12L)
    }

    private fun getScreenSize(): Pair<Int, Int> = try {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        dm.widthPixels to dm.heightPixels
    } catch (e: Exception) {
        1080 to 2400
    }

    // ---------- 交互 ----------

    private fun onTapped() {
        if (mode == MODE_PLAY) {
            manualStop = true
            ScriptPlayer.stop()
            Toast.makeText(this, R.string.toast_script_play_stopped, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        // 录制模式：结束并保存
        val result = ActionRecorder.stopAndSave(this)
        if (result == null) {
            Toast.makeText(this, R.string.toast_record_empty, Toast.LENGTH_LONG).show()
        } else {
            val (_, script) = result
            Toast.makeText(
                this,
                getString(R.string.toast_record_saved, script.name, script.actions.size),
                Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(
                    Intent(this, ScriptActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: Exception) {
                Log.e(TAG, "打开脚本页失败", e)
            }
        }
        stopSelf()
    }

    private fun refreshStatus() {
        if (!::tvStatus.isInitialized) return
        if (mode == MODE_PLAY) {
            tvStatus.text = getString(
                R.string.recorder_status_playing,
                ScriptPlayer.stepIndex + 1,
                ScriptPlayer.stepTotal,
                ScriptPlayer.loopIndex,
                ScriptPlayer.loopTotal
            )
            tvAction.setText(R.string.recorder_stop_play)
        } else {
            tvStatus.text = getString(R.string.recorder_status_recording, ActionRecorder.actionCount)
            tvAction.setText(R.string.recorder_stop)
        }
    }

    // ---------- 通知 ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_recorder_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, ScriptActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_recorder_content))
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(blinkRunnable)
        // 回放期间获取的 WakeLock 在此释放（只释放自己获取的，避免误释放滚动任务的锁）
        if (playWakeLockAcquired) {
            cn.ggdoc.autoscroll.task.KeepAliveManager.release()
            playWakeLockAcquired = false
        }
        // 服务被系统杀掉时保证不残留录制 / 回放状态
        if (ActionRecorder.isRecording) ActionRecorder.cancel(this)
        if (ScriptPlayer.isPlaying) ScriptPlayer.stop()
        if (isAdded) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "移除录制悬浮条失败", e)
            }
            isAdded = false
        }
        _instance?.clear()
        if (_instance?.get() == null) _instance = null
    }
}

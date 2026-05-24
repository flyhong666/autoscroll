package cn.ggdoc.autoscroll.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.ggdoc.autoscroll.MainActivity
import cn.ggdoc.autoscroll.R

class FloatingWindowService : Service() {

    companion object {
        const val TAG = "FloatingWindowService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "floating_window_channel"
        
        private var instance: FloatingWindowService? = null
        
        fun isRunning(): Boolean {
            return instance != null
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var controlButton: ImageView
    private var isAdded = false

    // 记录触摸事件的位置
    private var x = 0
    private var y = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "悬浮窗服务创建")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        createFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示悬浮窗控制按钮"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动刷视频")
            .setContentText("点击控制按钮开始/停止自动滚动")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)
        controlButton = floatingView.findViewById(R.id.controlButton)

        // 设置悬浮窗参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        // 设置按钮点击事件
        controlButton.setOnClickListener {
            toggleScrolling()
        }

        // 设置触摸监听，实现拖拽功能
        controlButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var clickStartTime = 0L

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event?.let {
                    when (it.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = x
                            initialY = y
                            initialTouchX = it.rawX
                            initialTouchY = it.rawY
                            clickStartTime = System.currentTimeMillis()
                            return@onTouch true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // 更新悬浮窗位置
                            x = initialX + (it.rawX - initialTouchX).toInt()
                            y = initialY + (it.rawY - initialTouchY).toInt()
                            
                            params.x = x
                            params.y = y
                            
                            windowManager.updateViewLayout(floatingView, params)
                            return@onTouch true
                        }
                        MotionEvent.ACTION_UP -> {
                            val clickDuration = System.currentTimeMillis() - clickStartTime
                            val deltaX = kotlin.math.abs(it.rawX - initialTouchX)
                            val deltaY = kotlin.math.abs(it.rawY - initialTouchY)
                            
                            // 如果是点击（不是拖拽）
                            if (clickDuration < 200 && deltaX < 10 && deltaY < 10) {
                                toggleScrolling()
                            }
                            return@onTouch true
                        }
                        else -> {
                            return@onTouch false
                        }
                    }
                }
                return false
            }
        })

        // 更新按钮状态
        updateButtonState()

        // 添加视图到窗口
        try {
            windowManager.addView(floatingView, params)
            isAdded = true
            Log.d(TAG, "悬浮窗已添加")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮窗失败", e)
        }
    }

    private fun toggleScrolling() {
        val accessibilityService = AutoScrollAccessibilityService.instance
        
        if (accessibilityService == null) {
            Log.e(TAG, "无障碍服务未启用")
            return
        }

        if (AutoScrollAccessibilityService.isScrolling) {
            accessibilityService.stopScrolling()
        } else {
            accessibilityService.startScrolling()
        }

        updateButtonState()
    }

    private fun updateButtonState() {
        val isScrolling = AutoScrollAccessibilityService.isScrolling
        
        // 根据状态切换按钮图标
        val drawableRes = if (isScrolling) {
            R.drawable.ic_stop  // 停止图标
        } else {
            R.drawable.ic_play   // 开始图标
        }
        
        controlButton.setImageResource(drawableRes)
        
        // 添加提示
        val toastMessage = if (isScrolling) {
            "已开始自动刷视频"
        } else {
            "已停止自动刷视频"
        }
        
        android.widget.Toast.makeText(this, toastMessage, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        if (isAdded) {
            try {
                windowManager.removeView(floatingView)
                Log.d(TAG, "悬浮窗已移除")
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮窗失败", e)
            }
        }
    }
}

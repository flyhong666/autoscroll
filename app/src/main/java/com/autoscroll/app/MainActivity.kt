package cn.ggdoc.autoscroll

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.FloatingWindowService

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var tvStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnStartService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnStartService = findViewById(R.id.btnStartService)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupClickListeners() {
        // 无障碍服务按钮
        btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 悬浮窗权限按钮
        btnOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        // 启动/停止服务按钮
        btnStartService.setOnClickListener {
            if (FloatingWindowService.isRunning()) {
                stopFloatingService()
            } else {
                startFloatingService()
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "请找到\"自动刷视频\"并开启服务",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "打开无障碍设置失败", e)
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "请求悬浮窗权限失败", e)
                    Toast.makeText(this, "无法请求悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            updateUI()
        }
    }

    private fun startFloatingService() {
        // 检查权限
        if (!checkPermissions()) {
            Toast.makeText(this, "请先授予所有必要权限", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        updateUI()
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        intent.action = "STOP_SERVICE"
        stopService(intent)
        
        // 停止无障碍服务
        AutoScrollAccessibilityService.instance?.stopScrolling()
        
        updateUI()
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions(): Boolean {
        // 检查悬浮窗权限
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (!hasOverlayPermission) {
            Toast.makeText(this, "缺少悬浮窗权限", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun updateUI() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        val isServiceRunning = FloatingWindowService.isRunning()
        val isScrolling = AutoScrollAccessibilityService.isScrolling

        // 更新状态文本
        val statusText = buildString {
            appendLine("无障碍服务：${if (isAccessibilityEnabled) "已启用" else "未启用"}")
            appendLine("悬浮窗权限：${if (hasOverlayPermission) "已授予" else "未授予"}")
            appendLine("悬浮窗服务：${if (isServiceRunning) "运行中" else "已停止"}")
            appendLine("自动滚动：${if (isScrolling) "运行中" else "已停止"}")
        }
        tvStatus.text = statusText

        // 更新按钮状态
        btnAccessibility.text = if (isAccessibilityEnabled) "无障碍服务已启用" else "开启无障碍服务"
        btnOverlay.text = if (hasOverlayPermission) "悬浮窗权限已授予" else "授予悬浮窗权限"
        btnStartService.text = if (isServiceRunning) "停止服务" else "启动服务"

        // 按钮可用性
        btnStartService.isEnabled = isAccessibilityEnabled && hasOverlayPermission
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val serviceName = "${packageName}/cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            enabledServices.contains(serviceName, ignoreCase = true) ||
            (enabledServices.isNotEmpty() && 
             AutoScrollAccessibilityService.instance != null)
        } catch (e: Exception) {
            false
        }
    }
}

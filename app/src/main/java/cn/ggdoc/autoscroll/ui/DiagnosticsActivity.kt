package cn.ggdoc.autoscroll.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.FloatingWindowService
import com.google.android.material.button.MaterialButton

/**
 * 健康诊断页：一键检查无障碍服务 / 悬浮窗 / 通知权限 / 服务状态，
 * 并给出「健康结论」与一键跳转修复入口。
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var tvOsVersion: TextView
    private lateinit var tvScreen: TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var tvOverlay: TextView
    private lateinit var tvNotification: TextView
    private lateinit var tvFloatingService: TextView
    private lateinit var tvScrolling: TextView
    private lateinit var tvHealthVerdict: TextView

    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnNotification: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        tvOsVersion = findViewById(R.id.tvOsVersion)
        tvScreen = findViewById(R.id.tvScreen)
        tvAccessibility = findViewById(R.id.tvAccessibility)
        tvOverlay = findViewById(R.id.tvOverlay)
        tvNotification = findViewById(R.id.tvNotification)
        tvFloatingService = findViewById(R.id.tvFloatingService)
        tvScrolling = findViewById(R.id.tvScrolling)
        tvHealthVerdict = findViewById(R.id.tvHealthVerdict)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotification = findViewById(R.id.btnNotification)

        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { requestOverlayPermission() }
        btnNotification.setOnClickListener { openNotificationSettings() }
        findViewById<MaterialButton>(R.id.btnOpenLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后刷新（权限可能已变化）
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val ctx = this
        tvOsVersion.text = getString(R.string.diagnostics_os_version, Build.VERSION.RELEASE)
        tvScreen.text = getString(R.string.diagnostics_screen, screenWidth(), screenHeight())

        val accEnabled = isAccessibilityEnabled()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else true
        val hasNotification = hasNotificationPermission()
        val svcRunning = FloatingWindowService.isRunning()
        val scrolling = AutoScrollAccessibilityService.isScrolling

        setStatus(tvAccessibility, accEnabled,
            R.string.status_accessibility_enabled, R.string.status_accessibility_disabled)
        setStatus(tvOverlay, hasOverlay,
            R.string.status_overlay_enabled, R.string.status_overlay_disabled)
        setStatus(tvNotification, hasNotification,
            R.string.status_notification_enabled, R.string.status_notification_disabled)
        setStatus(tvFloatingService, svcRunning,
            R.string.status_service_running, R.string.status_service_stopped)
        setStatus(tvScrolling, scrolling,
            R.string.status_scroll_running, R.string.status_scroll_stopped)

        btnAccessibility.text = getString(
            if (accEnabled) R.string.status_accessibility_enabled else R.string.btn_accessibility_off)
        btnOverlay.text = getString(
            if (hasOverlay) R.string.status_overlay_enabled else R.string.btn_overlay_off)
        btnNotification.text = getString(
            if (hasNotification) R.string.status_notification_enabled else R.string.diagnostics_notification_open)

        // 健康结论
        val missing = mutableListOf<String>()
        if (!accEnabled) missing.add(getString(R.string.diagnostics_missing_accessibility))
        if (!hasOverlay) missing.add(getString(R.string.diagnostics_missing_overlay))
        if (!hasNotification) missing.add(getString(R.string.diagnostics_missing_notification))
        tvHealthVerdict.text = if (missing.isEmpty()) {
            getString(R.string.diagnostics_health_ok)
        } else {
            getString(R.string.diagnostics_health_issue, missing.joinToString("、"))
        }
    }

    private fun setStatus(tv: TextView, ok: Boolean, onRes: Int, offRes: Int) {
        tv.text = getString(if (ok) onRes else offRes)
        tv.setTextColor(ContextCompat.getColor(this, if (ok) R.color.success else R.color.text_hint))
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val expected = "${packageName}/cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService"
            val enabledStr = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledStr.split(":").any { it.equals(expected, ignoreCase = true) } ||
                (AutoScrollAccessibilityService.instance != null)
        } catch (e: Exception) {
            AutoScrollAccessibilityService.instance != null
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure {
                Toast.makeText(this, R.string.toast_accessibility_open_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }.onFailure {
                Toast.makeText(this, R.string.toast_overlay_request_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }.onFailure {
                Toast.makeText(this, R.string.toast_notification_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun screenWidth(): Int = try {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        dm.widthPixels
    } catch (e: Exception) { 0 }

    private fun screenHeight(): Int = try {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        dm.heightPixels
    } catch (e: Exception) { 0 }
}

package cn.ggdoc.autoscroll.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.MainActivity
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.FloatingWindowService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 控制页：状态展示 + 设置入口（基础参数在底部弹窗配置）+ 权限引导 + 服务启停
 */
class ControlFragment : Fragment() {

    companion object { private const val TAG = "ControlFragment" }

    private lateinit var tvStatusAccessibility: TextView
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvStatusService: TextView
    private lateinit var tvStatusScrolling: TextView
    private lateinit var tvStatusScene: TextView
    private lateinit var tvStatusRemaining: TextView
    private lateinit var tvParamSummary: TextView

    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnStartService: MaterialButton
    private lateinit var cardSettingsEntry: MaterialCardView
    private lateinit var cardRecorderEntry: MaterialCardView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingWindowService.BROADCAST_STATE_CHANGED ||
                intent?.action == AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED
            ) {
                refreshUI()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupClickListeners()
        refreshUI()
        refreshParamSummary()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
        refreshUI()
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun bindViews(v: View) {
        tvStatusAccessibility = v.findViewById(R.id.tvStatusAccessibility)
        tvStatusOverlay = v.findViewById(R.id.tvStatusOverlay)
        tvStatusService = v.findViewById(R.id.tvStatusService)
        tvStatusScrolling = v.findViewById(R.id.tvStatusScrolling)
        tvStatusScene = v.findViewById(R.id.tvStatusScene)
        tvStatusRemaining = v.findViewById(R.id.tvStatusRemaining)
        tvParamSummary = v.findViewById(R.id.tvParamSummary)

        btnAccessibility = v.findViewById(R.id.btnAccessibility)
        btnOverlay = v.findViewById(R.id.btnOverlay)
        btnStartService = v.findViewById(R.id.btnStartService)
        cardSettingsEntry = v.findViewById(R.id.cardSettingsEntry)
        cardRecorderEntry = v.findViewById(R.id.cardRecorderEntry)
    }

    private fun setupClickListeners() {
        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { requestOverlayPermission() }
        btnStartService.setOnClickListener {
            if (FloatingWindowService.isRunning()) stopFloatingService()
            else startFloatingService()
        }
        cardSettingsEntry.setOnClickListener { openSettingsSheet() }
        cardRecorderEntry.setOnClickListener {
            (requireActivity() as? MainActivity)?.selectRecorderTab()
        }
    }

    private fun openSettingsSheet() {
        val sheet = SettingsBottomSheet()
        sheet.onSaved = { refreshParamSummary() }
        sheet.show(childFragmentManager, "SettingsBottomSheet")
    }

    private fun refreshParamSummary() {
        val ctx = requireContext()
        val minI = AppConfig.getMinInterval(ctx)
        val maxI = AppConfig.getMaxInterval(ctx)
        val minD = AppConfig.getMinDuration(ctx)
        val maxD = AppConfig.getMaxDuration(ctx)
        tvParamSummary.text = getString(
            R.string.settings_summary, minI, maxI, minD, maxD
        )
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(requireContext(), R.string.toast_accessibility_guide, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "打开无障碍设置失败", e)
            Toast.makeText(requireContext(), R.string.toast_accessibility_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")),
                    1001
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.toast_overlay_request_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingService() {
        val ctx = requireContext()
        if (!checkPermissions()) {
            Toast.makeText(ctx, R.string.toast_permission_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(ctx, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        Toast.makeText(ctx, R.string.toast_floating_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val ctx = requireContext()
        val intent = Intent(ctx, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_STOP_SERVICE
        }
        ctx.stopService(intent)
        AutoScrollAccessibilityService.instance?.stopScrolling()
        Toast.makeText(ctx, R.string.toast_service_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions(): Boolean {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else true
        if (!hasOverlay) {
            Toast.makeText(requireContext(), R.string.toast_overlay_permission_missing, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(FloatingWindowService.BROADCAST_STATE_CHANGED)
            addAction(AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(receiver, filter)
        }
    }

    private fun refreshUI() {
        val ctx = requireContext()
        val accEnabled = isAccessibilityServiceEnabled()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else true
        val svcRunning = FloatingWindowService.isRunning()
        val scrolling = AutoScrollAccessibilityService.isScrolling
        val sceneName = getString(SceneConfig.getScene(AppConfig.getCurrentScene(ctx)).nameRes)
        val remaining = AutoScrollAccessibilityService.remainingSeconds

        updateStatusText(tvStatusAccessibility, accEnabled,
            getString(R.string.status_accessibility_enabled),
            getString(R.string.status_accessibility_disabled))
        updateStatusText(tvStatusOverlay, hasOverlay,
            getString(R.string.status_overlay_enabled),
            getString(R.string.status_overlay_disabled))
        updateStatusText(tvStatusService, svcRunning,
            getString(R.string.status_service_running),
            getString(R.string.status_service_stopped))
        updateStatusText(tvStatusScrolling, scrolling,
            getString(R.string.status_scroll_running),
            getString(R.string.status_scroll_stopped))

        tvStatusScene.visibility = View.VISIBLE
        tvStatusScene.text = getString(R.string.status_scene, sceneName)
        tvStatusScene.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))

        if (scrolling && remaining > 0) {
            tvStatusRemaining.visibility = View.VISIBLE
            val m = remaining / 60
            val s = remaining % 60
            tvStatusRemaining.text = getString(R.string.status_remaining_time, String.format("%02d:%02d", m, s))
            tvStatusRemaining.setTextColor(ContextCompat.getColor(ctx, R.color.warn))
        } else {
            tvStatusRemaining.visibility = View.GONE
        }

        btnAccessibility.text = getString(
            if (accEnabled) R.string.btn_accessibility_on else R.string.btn_accessibility_off)
        btnOverlay.text = getString(
            if (hasOverlay) R.string.btn_overlay_on else R.string.btn_overlay_off)
        btnStartService.text = getString(
            if (svcRunning) R.string.stop_service else R.string.start_service)
        btnStartService.isEnabled = accEnabled && hasOverlay
    }

    private fun updateStatusText(tv: TextView, ok: Boolean, on: String, off: String) {
        tv.text = if (ok) on else off
        tv.setTextColor(ContextCompat.getColor(requireContext(),
            if (ok) R.color.success else R.color.text_hint))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val expected = "${requireContext().packageName}/cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService"
            val enabledStr = Settings.Secure.getString(
                requireContext().contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledStr.split(":").any { it.equals(expected, ignoreCase = true) } ||
                (AutoScrollAccessibilityService.instance != null)
        } catch (e: Exception) {
            AutoScrollAccessibilityService.instance != null
        }
    }
}

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
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.FloatingWindowService
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 控制页：状态展示 + 基础参数 + 权限引导 + 服务启停
 */
class ControlFragment : Fragment() {

    companion object { private const val TAG = "ControlFragment" }

    private lateinit var tvStatusAccessibility: TextView
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvStatusService: TextView
    private lateinit var tvStatusScrolling: TextView
    private lateinit var tvStatusScene: TextView
    private lateinit var tvStatusRemaining: TextView

    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnStartService: MaterialButton
    private lateinit var btnApplyRecommend: MaterialButton
    private lateinit var btnSaveSettings: MaterialButton

    private lateinit var sliderMinInterval: Slider
    private lateinit var sliderMaxInterval: Slider
    private lateinit var sliderMinDuration: Slider
    private lateinit var sliderMaxDuration: Slider
    private lateinit var tvMinInterval: TextView
    private lateinit var tvMaxInterval: TextView
    private lateinit var tvMinDuration: TextView
    private lateinit var tvMaxDuration: TextView
    private lateinit var switchFilterApp: SwitchMaterial

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
        setupSliders()
        setupClickListeners()
        loadSettingsToUI()
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

        btnAccessibility = v.findViewById(R.id.btnAccessibility)
        btnOverlay = v.findViewById(R.id.btnOverlay)
        btnStartService = v.findViewById(R.id.btnStartService)
        btnApplyRecommend = v.findViewById(R.id.btnApplyRecommend)
        btnSaveSettings = v.findViewById(R.id.btnSaveSettings)

        sliderMinInterval = v.findViewById(R.id.sliderMinInterval)
        sliderMaxInterval = v.findViewById(R.id.sliderMaxInterval)
        sliderMinDuration = v.findViewById(R.id.sliderMinDuration)
        sliderMaxDuration = v.findViewById(R.id.sliderMaxDuration)
        tvMinInterval = v.findViewById(R.id.tvMinInterval)
        tvMaxInterval = v.findViewById(R.id.tvMaxInterval)
        tvMinDuration = v.findViewById(R.id.tvMinDuration)
        tvMaxDuration = v.findViewById(R.id.tvMaxDuration)
        switchFilterApp = v.findViewById(R.id.switchFilterApp)
    }

    private fun setupSliders() {
        sliderMinInterval.addOnChangeListener { _, value, _ ->
            tvMinInterval.text = "${value.toInt()}s"
        }
        sliderMaxInterval.addOnChangeListener { _, value, _ ->
            tvMaxInterval.text = "${value.toInt()}s"
        }
        sliderMinDuration.addOnChangeListener { _, value, _ ->
            tvMinDuration.text = "${value.toInt()}ms"
        }
        sliderMaxDuration.addOnChangeListener { _, value, _ ->
            tvMaxDuration.text = "${value.toInt()}ms"
        }
    }

    private fun loadSettingsToUI() {
        val ctx = requireContext()
        sliderMinInterval.value = AppConfig.getMinInterval(ctx).toFloat()
            .coerceIn(sliderMinInterval.valueFrom, sliderMinInterval.valueTo)
        sliderMaxInterval.value = AppConfig.getMaxInterval(ctx).toFloat()
            .coerceIn(sliderMaxInterval.valueFrom, sliderMaxInterval.valueTo)
        sliderMinDuration.value = AppConfig.getMinDuration(ctx).toFloat()
            .coerceIn(sliderMinDuration.valueFrom, sliderMinDuration.valueTo)
        sliderMaxDuration.value = AppConfig.getMaxDuration(ctx).toFloat()
            .coerceIn(sliderMaxDuration.valueFrom, sliderMaxDuration.valueTo)
        tvMinInterval.text = "${sliderMinInterval.value.toInt()}s"
        tvMaxInterval.text = "${sliderMaxInterval.value.toInt()}s"
        tvMinDuration.text = "${sliderMinDuration.value.toInt()}ms"
        tvMaxDuration.text = "${sliderMaxDuration.value.toInt()}ms"
        switchFilterApp.isChecked = AppConfig.isFilterShortVideoApp(ctx)
    }

    private fun setupClickListeners() {
        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { requestOverlayPermission() }
        btnStartService.setOnClickListener {
            if (FloatingWindowService.isRunning()) stopFloatingService()
            else startFloatingService()
        }
        btnSaveSettings.setOnClickListener { saveSettings() }
        btnApplyRecommend.setOnClickListener { applyRecommendParams() }
    }

    private fun applyRecommendParams() {
        val sceneId = AppConfig.getCurrentScene(requireContext())
        val scene = SceneConfig.getScene(sceneId)
        sliderMinInterval.value = scene.recommendMinInterval.toFloat()
            .coerceIn(sliderMinInterval.valueFrom, sliderMinInterval.valueTo)
        sliderMaxInterval.value = scene.recommendMaxInterval.toFloat()
            .coerceIn(sliderMaxInterval.valueFrom, sliderMaxInterval.valueTo)
        sliderMinDuration.value = scene.recommendMinDuration.toFloat()
            .coerceIn(sliderMinDuration.valueFrom, sliderMinDuration.valueTo)
        sliderMaxDuration.value = scene.recommendMaxDuration.toFloat()
            .coerceIn(sliderMaxDuration.valueFrom, sliderMaxDuration.valueTo)
        tvMinInterval.text = "${sliderMinInterval.value.toInt()}s"
        tvMaxInterval.text = "${sliderMaxInterval.value.toInt()}s"
        tvMinDuration.text = "${sliderMinDuration.value.toInt()}ms"
        tvMaxDuration.text = "${sliderMaxDuration.value.toInt()}ms"
        Toast.makeText(requireContext(), R.string.params_saved, Toast.LENGTH_SHORT).show()
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

    private fun saveSettings() {
        val ctx = requireContext()
        var minI = sliderMinInterval.value.toInt()
        var maxI = sliderMaxInterval.value.toInt()
        var minD = sliderMinDuration.value.toInt()
        var maxD = sliderMaxDuration.value.toInt()
        if (minI >= maxI) maxI = minI + 1
        if (minD >= maxD) maxD = minD + 50
        val (valid, msg) = AppConfig.validate(minI, maxI, minD, maxD)
        if (!valid) {
            Toast.makeText(ctx, "参数错误：$msg", Toast.LENGTH_SHORT).show()
            return
        }
        AppConfig.setMinInterval(ctx, minI)
        AppConfig.setMaxInterval(ctx, maxI)
        AppConfig.setMinDuration(ctx, minD)
        AppConfig.setMaxDuration(ctx, maxD)
        AppConfig.setFilterShortVideoApp(ctx, switchFilterApp.isChecked)
        AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
        Toast.makeText(ctx, R.string.params_saved, Toast.LENGTH_SHORT).show()
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

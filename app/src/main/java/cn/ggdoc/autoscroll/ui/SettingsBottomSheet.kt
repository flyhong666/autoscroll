package cn.ggdoc.autoscroll.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

/**
 * 基础参数设置底部弹窗：滑动间隔 / 时长 + 生效应用（用户自选已安装 APP）
 * 从控制页的「设置」入口打开，避免控制页过长需要滚动。
 */
class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onSaved: (() -> Unit)? = null

    /** 场景切换时回调（通知控制页刷新状态展示） */
    var onSceneChanged: (() -> Unit)? = null

    private lateinit var sliderMinInterval: Slider
    private lateinit var sliderMaxInterval: Slider
    private lateinit var sliderMinDuration: Slider
    private lateinit var sliderMaxDuration: Slider
    private lateinit var tvMinInterval: TextView
    private lateinit var tvMaxInterval: TextView
    private lateinit var tvMinDuration: TextView
    private lateinit var tvMaxDuration: TextView
    private lateinit var cardSceneEntry: MaterialCardView
    private lateinit var tvSceneSummary: TextView
    private lateinit var cardAllowedApps: MaterialCardView
    private lateinit var tvAllowedAppsSummary: TextView
    private lateinit var etAdKeywords: TextInputEditText
    private lateinit var btnApplyRecommend: MaterialButton
    private lateinit var btnSaveSettings: MaterialButton

    /** 当前选中的生效应用包名集合（未保存前仅存于内存） */
    private var selectedApps: MutableSet<String> = mutableSetOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupSliders()
        loadSettingsToUI()

        cardSceneEntry.setOnClickListener { openScenePicker() }
        cardAllowedApps.setOnClickListener { openAppPicker() }
        btnApplyRecommend.setOnClickListener { applyRecommendParams() }
        btnSaveSettings.setOnClickListener { saveSettings() }
    }

    private fun bindViews(v: View) {
        sliderMinInterval = v.findViewById(R.id.sliderMinInterval)
        sliderMaxInterval = v.findViewById(R.id.sliderMaxInterval)
        sliderMinDuration = v.findViewById(R.id.sliderMinDuration)
        sliderMaxDuration = v.findViewById(R.id.sliderMaxDuration)
        tvMinInterval = v.findViewById(R.id.tvMinInterval)
        tvMaxInterval = v.findViewById(R.id.tvMaxInterval)
        tvMinDuration = v.findViewById(R.id.tvMinDuration)
        tvMaxDuration = v.findViewById(R.id.tvMaxDuration)
        cardSceneEntry = v.findViewById(R.id.cardSceneEntry)
        tvSceneSummary = v.findViewById(R.id.tvSceneSummary)
        cardAllowedApps = v.findViewById(R.id.cardAllowedApps)
        tvAllowedAppsSummary = v.findViewById(R.id.tvAllowedAppsSummary)
        etAdKeywords = v.findViewById(R.id.etAdKeywords)
        btnApplyRecommend = v.findViewById(R.id.btnApplyRecommend)
        btnSaveSettings = v.findViewById(R.id.btnSaveSettings)
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

        selectedApps = AppConfig.getAllowedApps(ctx).toMutableSet()
        updateAllowedAppsSummary()
        updateSceneSummary()

        etAdKeywords.setText(AppConfig.getAdKeywords(ctx).joinToString(","))
    }

    private fun updateSceneSummary() {
        val ctx = requireContext()
        val sceneName = getString(SceneConfig.getScene(AppConfig.getCurrentScene(ctx)).nameRes)
        tvSceneSummary.text = getString(R.string.scene_entry_summary, sceneName)
    }

    private fun openScenePicker() {
        val dialog = ScenePickerDialogFragment()
        dialog.onSceneChanged = {
            updateSceneSummary()
            onSceneChanged?.invoke()
        }
        dialog.show(childFragmentManager, "scene_picker")
    }

    private fun updateAllowedAppsSummary() {
        tvAllowedAppsSummary.text = if (selectedApps.isEmpty()) {
            getString(R.string.allowed_apps_empty)
        } else {
            getString(R.string.allowed_apps_count, selectedApps.size)
        }
    }

    private fun openAppPicker() {
        val dialog = AppPickerDialogFragment()
        dialog.initialSelection = selectedApps.toSet()
        dialog.onConfirm = { set ->
            selectedApps = set.toMutableSet()
            updateAllowedAppsSummary()
        }
        dialog.show(childFragmentManager, "app_picker")
    }

    private fun applyRecommendParams() {
        val ctx = requireContext()
        val scene = SceneConfig.getScene(AppConfig.getCurrentScene(ctx))
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
        Toast.makeText(ctx, R.string.apply_recommend_done, Toast.LENGTH_SHORT).show()
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
        AppConfig.setAllowedApps(ctx, selectedApps)
        AppConfig.setAdKeywords(ctx, AppConfig.parseKeywords(etAdKeywords.text.toString()))
        AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
        Toast.makeText(ctx, R.string.params_saved, Toast.LENGTH_SHORT).show()
        onSaved?.invoke()
        dismiss()
    }
}

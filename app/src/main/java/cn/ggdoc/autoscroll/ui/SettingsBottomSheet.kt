package cn.ggdoc.autoscroll.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import android.widget.Spinner

/**
 * 统一设置面板（底部弹窗）：基础参数（间隔/时长/场景/广告关键词）+ 原「任务页」全部行为设置。
 * 从控制页的「设置」入口打开。所有设置在此一处完成，保存后实时同步到无障碍服务。
 *
 * 应用选择器（选择轮换应用 / 选择过滤应用）通过 Fragment Result API 经本弹窗的
 * childFragmentManager 回传结果，避免旧式 setTargetFragment 导致的崩溃。
 */
class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onSaved: (() -> Unit)? = null

    /** 场景切换时回调（通知控制页刷新状态展示） */
    var onSceneChanged: (() -> Unit)? = null

    // ---- 基础参数 ----
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
    private lateinit var etAdKeywords: TextInputEditText
    private lateinit var btnApplyRecommend: MaterialButton
    private lateinit var btnSaveSettings: MaterialButton

    // ---- 行为设置（原任务页） ----
    private lateinit var switchAutoLike: SwitchMaterial
    private lateinit var switchAdBlock: SwitchMaterial
    private lateinit var switchTimedStop: SwitchMaterial
    private lateinit var switchAppRotation: SwitchMaterial
    private lateinit var switchKeepScreenOn: SwitchMaterial
    private lateinit var switchSchedule: SwitchMaterial
    private lateinit var switchRecover: SwitchMaterial
    private lateinit var switchBatteryGuard: SwitchMaterial
    private lateinit var switchWifiOnly: SwitchMaterial
    private lateinit var switchAdReward: SwitchMaterial
    private lateinit var checkAdRewardAck: MaterialCheckBox
    private lateinit var switchDetailFlow: SwitchMaterial

    private lateinit var sliderLikeProbability: Slider
    private lateinit var sliderTimedStopMinutes: Slider
    private lateinit var sliderRotationMinutes: Slider
    private lateinit var sliderBatteryThreshold: Slider
    private lateinit var sliderAdRewardInterval: Slider

    private lateinit var sliderDetailDwellMin: Slider
    private lateinit var sliderDetailDwellMax: Slider
    private lateinit var sliderDetailReadAll: Slider
    private lateinit var sliderDetailMaxScrolls: Slider
    private lateinit var tvDetailDwellMin: TextView
    private lateinit var tvDetailDwellMax: TextView
    private lateinit var tvDetailReadAll: TextView
    private lateinit var tvDetailMaxScrolls: TextView

    private lateinit var tvLikeProbability: TextView
    private lateinit var tvTimedStopMinutes: TextView
    private lateinit var tvRotationMinutes: TextView
    private lateinit var tvBatteryThreshold: TextView
    private lateinit var tvAdRewardInterval: TextView
    private lateinit var tvAdRewardAckWarn: TextView

    private lateinit var rvScheduleWindows: RecyclerView
    private lateinit var tvScheduleEmpty: TextView
    private lateinit var btnAddWindow: MaterialButton

    private lateinit var spinnerFilterMode: Spinner
    private lateinit var tvFilterSummary: TextView
    private lateinit var btnFilterApps: MaterialButton

    private lateinit var likeProbabilityContainer: View
    private lateinit var timedStopContainer: View
    private lateinit var rotationContainer: View
    private lateinit var rotationAppsRow: View
    private lateinit var tvRotationAppsSummary: TextView
    private lateinit var scheduleTimeContainer: View
    private lateinit var batteryThresholdContainer: View
    private lateinit var adRewardIntervalContainer: View
    private lateinit var adRewardKeywordsContainer: View
    private lateinit var detailFlowParamsContainer: View

    private lateinit var etAdRewardKeywords: TextInputEditText

    // 多时段定时窗口（分钟，0-1439），每个元素为 (开始, 结束)
    private var scheduleWindows: MutableList<Pair<Int, Int>> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupSliders()
        setupSwitches()
        setupAppFilter()
        setupAdReward()
        setupScheduleWindows()
        setupAppPickerResult()

        loadSettingsToUI()

        cardSceneEntry.setOnClickListener { openScenePicker() }
        btnApplyRecommend.setOnClickListener { applyRecommendParams() }
        btnSaveSettings.setOnClickListener { saveSettings() }
    }

    private fun bindViews(v: View) {
        // 基础参数
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
        etAdKeywords = v.findViewById(R.id.etAdKeywords)
        btnApplyRecommend = v.findViewById(R.id.btnApplyRecommend)
        btnSaveSettings = v.findViewById(R.id.btnSaveSettings)

        // 行为设置
        switchAutoLike = v.findViewById(R.id.switchAutoLike)
        switchAdBlock = v.findViewById(R.id.switchAdBlock)
        switchTimedStop = v.findViewById(R.id.switchTimedStop)
        switchAppRotation = v.findViewById(R.id.switchAppRotation)
        switchKeepScreenOn = v.findViewById(R.id.switchKeepScreenOn)
        switchSchedule = v.findViewById(R.id.switchSchedule)
        switchRecover = v.findViewById(R.id.switchRecover)
        switchBatteryGuard = v.findViewById(R.id.switchBatteryGuard)
        switchWifiOnly = v.findViewById(R.id.switchWifiOnly)
        switchAdReward = v.findViewById(R.id.switchAdReward)
        checkAdRewardAck = v.findViewById(R.id.checkAdRewardAck)
        switchDetailFlow = v.findViewById(R.id.switchDetailFlow)

        sliderLikeProbability = v.findViewById(R.id.sliderLikeProbability)
        sliderTimedStopMinutes = v.findViewById(R.id.sliderTimedStopMinutes)
        sliderRotationMinutes = v.findViewById(R.id.sliderRotationMinutes)
        sliderBatteryThreshold = v.findViewById(R.id.sliderBatteryThreshold)
        sliderAdRewardInterval = v.findViewById(R.id.sliderAdRewardInterval)

        sliderDetailDwellMin = v.findViewById(R.id.sliderDetailDwellMin)
        sliderDetailDwellMax = v.findViewById(R.id.sliderDetailDwellMax)
        sliderDetailReadAll = v.findViewById(R.id.sliderDetailReadAll)
        sliderDetailMaxScrolls = v.findViewById(R.id.sliderDetailMaxScrolls)
        tvDetailDwellMin = v.findViewById(R.id.tvDetailDwellMin)
        tvDetailDwellMax = v.findViewById(R.id.tvDetailDwellMax)
        tvDetailReadAll = v.findViewById(R.id.tvDetailReadAll)
        tvDetailMaxScrolls = v.findViewById(R.id.tvDetailMaxScrolls)

        tvLikeProbability = v.findViewById(R.id.tvLikeProbability)
        tvTimedStopMinutes = v.findViewById(R.id.tvTimedStopMinutes)
        tvRotationMinutes = v.findViewById(R.id.tvRotationMinutes)
        tvBatteryThreshold = v.findViewById(R.id.tvBatteryThreshold)
        tvAdRewardInterval = v.findViewById(R.id.tvAdRewardInterval)
        tvAdRewardAckWarn = v.findViewById(R.id.tvAdRewardAckWarn)

        rvScheduleWindows = v.findViewById(R.id.rvScheduleWindows)
        tvScheduleEmpty = v.findViewById(R.id.tvScheduleEmpty)
        btnAddWindow = v.findViewById(R.id.btnAddWindow)

        spinnerFilterMode = v.findViewById(R.id.spinnerFilterMode)
        tvFilterSummary = v.findViewById(R.id.tvFilterSummary)
        btnFilterApps = v.findViewById(R.id.btnFilterApps)

        likeProbabilityContainer = v.findViewById(R.id.likeProbabilityContainer)
        timedStopContainer = v.findViewById(R.id.timedStopContainer)
        rotationContainer = v.findViewById(R.id.rotationContainer)
        rotationAppsRow = v.findViewById(R.id.rotationAppsRow)
        tvRotationAppsSummary = v.findViewById(R.id.tvRotationAppsSummary)
        scheduleTimeContainer = v.findViewById(R.id.scheduleTimeContainer)
        batteryThresholdContainer = v.findViewById(R.id.batteryThresholdContainer)
        adRewardIntervalContainer = v.findViewById(R.id.adRewardIntervalContainer)
        adRewardKeywordsContainer = v.findViewById(R.id.adRewardKeywordsContainer)
        detailFlowParamsContainer = v.findViewById(R.id.detailFlowParamsContainer)

        etAdRewardKeywords = v.findViewById(R.id.etAdRewardKeywords)
    }

    private fun setupSliders() {
        sliderMinInterval.addOnChangeListener { _, value, _ -> tvMinInterval.text = "${value.toInt()}s" }
        sliderMaxInterval.addOnChangeListener { _, value, _ -> tvMaxInterval.text = "${value.toInt()}s" }
        sliderMinDuration.addOnChangeListener { _, value, _ -> tvMinDuration.text = "${value.toInt()}ms" }
        sliderMaxDuration.addOnChangeListener { _, value, _ -> tvMaxDuration.text = "${value.toInt()}ms" }

        sliderLikeProbability.addOnChangeListener { _, value, _ -> tvLikeProbability.text = "${value.toInt()}%" }
        sliderTimedStopMinutes.addOnChangeListener { _, value, _ -> tvTimedStopMinutes.text = "${value.toInt()}min" }
        sliderRotationMinutes.addOnChangeListener { _, value, _ -> tvRotationMinutes.text = "${value.toInt()}min" }
        sliderBatteryThreshold.addOnChangeListener { _, value, _ -> tvBatteryThreshold.text = "${value.toInt()}%" }
        sliderAdRewardInterval.addOnChangeListener { _, value, _ -> tvAdRewardInterval.text = "${value.toInt()}min" }

        sliderDetailDwellMin.addOnChangeListener { _, value, _ -> tvDetailDwellMin.text = "${value.toInt()}s" }
        sliderDetailDwellMax.addOnChangeListener { _, value, _ -> tvDetailDwellMax.text = "${value.toInt()}s" }
        sliderDetailReadAll.addOnChangeListener { _, value, _ -> tvDetailReadAll.text = "$value%" }
        sliderDetailMaxScrolls.addOnChangeListener { _, value, _ -> tvDetailMaxScrolls.text = value.toInt().toString() }
    }

    private fun setupSwitches() {
        switchAutoLike.setOnCheckedChangeListener { _, isChecked ->
            likeProbabilityContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        switchTimedStop.setOnCheckedChangeListener { _, isChecked ->
            timedStopContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        switchAppRotation.setOnCheckedChangeListener { _, isChecked ->
            rotationContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            rotationAppsRow.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        rotationAppsRow.setOnClickListener { openAppPicker() }
        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            scheduleTimeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        switchBatteryGuard.setOnCheckedChangeListener { _, isChecked ->
            batteryThresholdContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        switchDetailFlow.setOnCheckedChangeListener { _, isChecked ->
            detailFlowParamsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupAppFilter() {
        val modes = listOf(
            AppConfig.FILTER_OFF to R.string.filter_mode_off,
            AppConfig.FILTER_WHITELIST to R.string.filter_mode_whitelist,
            AppConfig.FILTER_BLACKLIST to R.string.filter_mode_blacklist
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            modes.map { getString(it.second) }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerFilterMode.adapter = adapter
        spinnerFilterMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long) {
                val ctx = context ?: return
                val mode = modes[pos].first
                val list = AppConfig.getAppFilterList(ctx)
                updateFilterSummary(mode, list)
                btnFilterApps.isEnabled = mode != AppConfig.FILTER_OFF
                // 选择变化时实时落盘，无需等「保存」按钮
                if (mode != AppConfig.getAppFilterMode(ctx)) {
                    AppConfig.setAppFilterMode(ctx, mode)
                    AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        btnFilterApps.setOnClickListener { openFilterAppPicker() }
    }

    private fun updateFilterSummary(mode: String, list: Set<String>) {
        val count = list.size
        tvFilterSummary.text = when (mode) {
            AppConfig.FILTER_OFF -> getString(R.string.filter_off_hint)
            AppConfig.FILTER_WHITELIST -> getString(R.string.filter_summary, count, getString(R.string.filter_mode_whitelist))
            else -> getString(R.string.filter_summary, count, getString(R.string.filter_mode_blacklist))
        }
    }

    private fun openFilterAppPicker() {
        val fm = childFragmentManager
        if (fm.isStateSaved || fm.isDestroyed) return
        val dialog = AppPickerDialogFragment.newInstance(AppConfig.getAppFilterList(requireContext()), REQ_FILTER_APPS)
        dialog.show(fm, "filter_app_picker")
    }

    private fun setupAdReward() {
        checkAdRewardAck.setOnCheckedChangeListener { _, checked ->
            switchAdReward.isEnabled = checked
            tvAdRewardAckWarn.visibility = if (checked) View.GONE else View.VISIBLE
            updateAdRewardContainers()
        }
        switchAdReward.setOnCheckedChangeListener { _, _ -> updateAdRewardContainers() }
    }

    private fun updateAdRewardContainers() {
        val show = checkAdRewardAck.isChecked && switchAdReward.isChecked
        adRewardIntervalContainer.visibility = if (show) View.VISIBLE else View.GONE
        adRewardKeywordsContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupAppPickerResult() {
        childFragmentManager.setFragmentResultListener(
            AppPickerDialogFragment.RESULT_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val requestCode = bundle.getInt(AppPickerDialogFragment.RESULT_EXTRA_REQUEST_CODE)
            val selected = bundle.getStringArrayList(AppPickerDialogFragment.RESULT_EXTRA_SELECTED)
                .orEmpty().toSet()
            onAppsConfirmed(requestCode, selected)
        }
    }

    private fun openAppPicker() {
        val fm = childFragmentManager
        if (fm.isStateSaved || fm.isDestroyed) return
        val dialog = AppPickerDialogFragment.newInstance(AppConfig.getRotationApps(requireContext()), REQ_ROTATION_APPS)
        dialog.show(fm, "rotation_app_picker")
    }

    private fun onAppsConfirmed(requestCode: Int, selected: Set<String>) {
        val ctx = context ?: return
        when (requestCode) {
            REQ_ROTATION_APPS -> {
                AppConfig.setRotationApps(ctx, selected)
                updateRotationAppsSummary()
                AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
            }
            REQ_FILTER_APPS -> {
                AppConfig.setAppFilterList(ctx, selected)
                val mode = AppConfig.getAppFilterMode(ctx)
                updateFilterSummary(mode, selected)
                AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
            }
        }
    }

    // ---- 多时段定时窗口 ----
    private lateinit var windowAdapter: ScheduleWindowAdapter

    private fun setupScheduleWindows() {
        rvScheduleWindows.layoutManager = LinearLayoutManager(requireContext())
        windowAdapter = ScheduleWindowAdapter(
            scheduleWindows,
            onStartClick = { idx -> pickWindowTime(idx, true) },
            onEndClick = { idx -> pickWindowTime(idx, false) },
            onDelete = { idx ->
                if (idx in scheduleWindows.indices) {
                    scheduleWindows.removeAt(idx)
                    refreshWindowUI()
                }
            }
        )
        rvScheduleWindows.adapter = windowAdapter
        rvScheduleWindows.isNestedScrollingEnabled = false
        btnAddWindow.setOnClickListener {
            if (scheduleWindows.size >= 8) {
                Toast.makeText(requireContext(), R.string.task_schedule_max_windows, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scheduleWindows.add(AppConfig.DEFAULT_SCHEDULE_START_MIN to AppConfig.DEFAULT_SCHEDULE_END_MIN)
            refreshWindowUI()
        }
    }

    private fun refreshWindowUI() {
        windowAdapter.notifyDataSetChanged()
        tvScheduleEmpty.visibility = if (scheduleWindows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pickWindowTime(index: Int, isStart: Boolean) {
        if (index !in scheduleWindows.indices) return
        val ctx = context ?: return
        val cur = if (isStart) scheduleWindows[index].first else scheduleWindows[index].second
        val h = cur / 60
        val m = cur % 60
        TimePickerDialog(ctx, { _, hh, mm ->
            if (!isAdded || index !in scheduleWindows.indices) return@TimePickerDialog
            val min = hh * 60 + mm
            val old = scheduleWindows[index]
            val newStart = if (isStart) min else old.first
            val newEnd = if (isStart) old.second else min
            if (newStart == newEnd) {
                context?.let {
                    Toast.makeText(it, R.string.task_schedule_same_time, Toast.LENGTH_SHORT).show()
                }
                return@TimePickerDialog
            }
            scheduleWindows[index] = newStart to newEnd
            refreshWindowUI()
        }, h, m, true).show()
    }

    private fun updateRotationAppsSummary() {
        val count = AppConfig.getRotationApps(requireContext()).size
        tvRotationAppsSummary.text = if (count == 0) {
            getString(R.string.rotation_apps_empty)
        } else {
            getString(R.string.rotation_apps_count, count)
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
        updateSceneSummary()
        etAdKeywords.setText(AppConfig.getAdKeywords(ctx).joinToString(","))

        // 行为设置
        switchAutoLike.isChecked = AppConfig.isAutoLike(ctx)
        switchAdBlock.isChecked = AppConfig.isAdBlock(ctx)
        switchTimedStop.isChecked = AppConfig.isTimedStop(ctx)
        switchAppRotation.isChecked = AppConfig.isAppRotation(ctx)
        switchKeepScreenOn.isChecked = AppConfig.isKeepScreenOn(ctx)
        switchSchedule.isChecked = AppConfig.isScheduleEnabled(ctx)
        switchRecover.isChecked = AppConfig.isRecoverEnabled(ctx)
        switchBatteryGuard.isChecked = AppConfig.isBatteryGuard(ctx)
        switchWifiOnly.isChecked = AppConfig.isWifiOnly(ctx)

        val filterMode = AppConfig.getAppFilterMode(ctx)
        val filterList = AppConfig.getAppFilterList(ctx)
        val modeIndex = when (filterMode) {
            AppConfig.FILTER_WHITELIST -> 1
            AppConfig.FILTER_BLACKLIST -> 2
            else -> 0
        }
        spinnerFilterMode.setSelection(modeIndex)
        updateFilterSummary(filterMode, filterList)
        btnFilterApps.isEnabled = filterMode != AppConfig.FILTER_OFF

        sliderLikeProbability.value = AppConfig.getLikeProbability(ctx).toFloat()
            .coerceIn(sliderLikeProbability.valueFrom, sliderLikeProbability.valueTo)
        sliderTimedStopMinutes.value = AppConfig.getTimedStopMinutes(ctx).toFloat()
            .coerceIn(sliderTimedStopMinutes.valueFrom, sliderTimedStopMinutes.valueTo)
        sliderRotationMinutes.value = AppConfig.getRotationMinutes(ctx).toFloat()
            .coerceIn(sliderRotationMinutes.valueFrom, sliderRotationMinutes.valueTo)
        sliderBatteryThreshold.value = AppConfig.getBatteryThreshold(ctx).toFloat()
            .coerceIn(sliderBatteryThreshold.valueFrom, sliderBatteryThreshold.valueTo)

        scheduleWindows.clear()
        scheduleWindows.addAll(AppConfig.getScheduleWindows(ctx))
        refreshWindowUI()

        tvLikeProbability.text = "${sliderLikeProbability.value.toInt()}%"
        tvTimedStopMinutes.text = "${sliderTimedStopMinutes.value.toInt()}min"
        tvRotationMinutes.text = "${sliderRotationMinutes.value.toInt()}min"
        tvBatteryThreshold.text = "${sliderBatteryThreshold.value.toInt()}%"

        likeProbabilityContainer.visibility = if (switchAutoLike.isChecked) View.VISIBLE else View.GONE
        timedStopContainer.visibility = if (switchTimedStop.isChecked) View.VISIBLE else View.GONE
        rotationContainer.visibility = if (switchAppRotation.isChecked) View.VISIBLE else View.GONE
        rotationAppsRow.visibility = if (switchAppRotation.isChecked) View.VISIBLE else View.GONE
        updateRotationAppsSummary()
        scheduleTimeContainer.visibility = if (switchSchedule.isChecked) View.VISIBLE else View.GONE
        batteryThresholdContainer.visibility = if (switchBatteryGuard.isChecked) View.VISIBLE else View.GONE

        val acked = AppConfig.isAdRewardAcked(ctx)
        switchAdReward.isChecked = AppConfig.getAdRewardEnabledRaw(ctx)
        switchAdReward.isEnabled = acked
        checkAdRewardAck.isChecked = acked
        tvAdRewardAckWarn.visibility = if (acked) View.GONE else View.VISIBLE
        updateAdRewardContainers()

        sliderAdRewardInterval.value = AppConfig.getAdRewardInterval(ctx).toFloat()
            .coerceIn(sliderAdRewardInterval.valueFrom, sliderAdRewardInterval.valueTo)
        tvAdRewardInterval.text = "${sliderAdRewardInterval.value.toInt()}min"
        etAdRewardKeywords.setText(AppConfig.getAdRewardKeywords(ctx).joinToString(","))

        switchDetailFlow.isChecked = AppConfig.isDetailFlowEnabled(ctx)
        detailFlowParamsContainer.visibility = if (switchDetailFlow.isChecked) View.VISIBLE else View.GONE
        sliderDetailDwellMin.value = AppConfig.getDetailDwellMin(ctx).toFloat()
            .coerceIn(sliderDetailDwellMin.valueFrom, sliderDetailDwellMin.valueTo)
        tvDetailDwellMin.text = "${sliderDetailDwellMin.value.toInt()}s"
        sliderDetailDwellMax.value = AppConfig.getDetailDwellMax(ctx).toFloat()
            .coerceIn(sliderDetailDwellMax.valueFrom, sliderDetailDwellMax.valueTo)
        tvDetailDwellMax.text = "${sliderDetailDwellMax.value.toInt()}s"
        sliderDetailReadAll.value = AppConfig.getDetailReadAllProbability(ctx).toFloat()
            .coerceIn(sliderDetailReadAll.valueFrom, sliderDetailReadAll.valueTo)
        tvDetailReadAll.text = "${sliderDetailReadAll.value.toInt()}%"
        sliderDetailMaxScrolls.value = AppConfig.getDetailMaxScrolls(ctx).toFloat()
            .coerceIn(sliderDetailMaxScrolls.valueFrom, sliderDetailMaxScrolls.valueTo)
        tvDetailMaxScrolls.text = sliderDetailMaxScrolls.value.toInt().toString()
    }

    private fun updateSceneSummary() {
        val ctx = requireContext()
        val sceneName = getString(SceneConfig.getScene(AppConfig.getCurrentScene(ctx)).nameRes)
        tvSceneSummary.text = getString(R.string.scene_entry_summary, sceneName)
    }

    private fun openScenePicker() {
        val fm = childFragmentManager
        if (fm.isStateSaved || fm.isDestroyed) return
        val dialog = ScenePickerDialogFragment()
        dialog.onSceneChanged = {
            if (isAdded) {
                updateSceneSummary()
                onSceneChanged?.invoke()
            }
        }
        dialog.show(fm, "scene_picker")
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
            Toast.makeText(ctx, getString(R.string.params_error, msg), Toast.LENGTH_SHORT).show()
            return
        }
        // 基础参数
        AppConfig.setMinInterval(ctx, minI)
        AppConfig.setMaxInterval(ctx, maxI)
        AppConfig.setMinDuration(ctx, minD)
        AppConfig.setMaxDuration(ctx, maxD)
        AppConfig.setAdKeywords(ctx, AppConfig.parseKeywords(etAdKeywords.text.toString()))

        // 行为设置
        AppConfig.setAutoLike(ctx, switchAutoLike.isChecked)
        AppConfig.setLikeProbability(ctx, sliderLikeProbability.value.toInt())
        AppConfig.setAdBlock(ctx, switchAdBlock.isChecked)
        AppConfig.setTimedStop(ctx, switchTimedStop.isChecked)
        AppConfig.setTimedStopMinutes(ctx, sliderTimedStopMinutes.value.toInt())
        AppConfig.setAppRotation(ctx, switchAppRotation.isChecked)
        AppConfig.setRotationMinutes(ctx, sliderRotationMinutes.value.toInt())
        AppConfig.setKeepScreenOn(ctx, switchKeepScreenOn.isChecked)

        AppConfig.setScheduleEnabled(ctx, switchSchedule.isChecked)
        AppConfig.setScheduleWindows(ctx, scheduleWindows.map { it.first to it.second })
        AppConfig.setRecoverEnabled(ctx, switchRecover.isChecked)
        AppConfig.setBatteryGuard(ctx, switchBatteryGuard.isChecked)
        AppConfig.setBatteryThreshold(ctx, sliderBatteryThreshold.value.toInt())
        AppConfig.setWifiOnly(ctx, switchWifiOnly.isChecked)

        val filterMode = when (spinnerFilterMode.selectedItemPosition) {
            1 -> AppConfig.FILTER_WHITELIST
            2 -> AppConfig.FILTER_BLACKLIST
            else -> AppConfig.FILTER_OFF
        }
        AppConfig.setAppFilterMode(ctx, filterMode)
        AppConfig.setAppFilterList(ctx, AppConfig.getAppFilterList(ctx))

        AppConfig.setAdReward(ctx, switchAdReward.isChecked)
        AppConfig.setAdRewardAcked(ctx, checkAdRewardAck.isChecked)
        AppConfig.setAdRewardInterval(ctx, sliderAdRewardInterval.value.toInt())
        AppConfig.setAdRewardKeywords(ctx, AppConfig.parseKeywords(etAdRewardKeywords.text?.toString().orEmpty()))

        AppConfig.setDetailFlowEnabled(ctx, switchDetailFlow.isChecked)
        AppConfig.setDetailDwellMin(ctx, sliderDetailDwellMin.value.toInt())
        AppConfig.setDetailDwellMax(ctx, sliderDetailDwellMax.value.toInt())
        AppConfig.setDetailReadAllProbability(ctx, sliderDetailReadAll.value.toInt())
        AppConfig.setDetailMaxScrolls(ctx, sliderDetailMaxScrolls.value.toInt())

        AutoScrollAccessibilityService.instance?.onScheduleConfigChanged()

        Toast.makeText(ctx, R.string.params_saved, Toast.LENGTH_SHORT).show()
        onSaved?.invoke()
        dismiss()
    }

    private class ScheduleWindowAdapter(
        private val items: MutableList<Pair<Int, Int>>,
        private val onStartClick: (Int) -> Unit,
        private val onEndClick: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<ScheduleWindowAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvStart: TextView = v.findViewById(R.id.tvWinStart)
            val tvEnd: TextView = v.findViewById(R.id.tvWinEnd)
            val btnDelete: View = v.findViewById(R.id.btnWinDelete)
        }

        private fun fmt(min: Int) = String.format("%02d:%02d", min / 60, min % 60)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_window, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (s, e) = items[position]
            holder.tvStart.text = fmt(s)
            holder.tvEnd.text = fmt(e)
            holder.tvStart.setOnClickListener { onStartClick(holder.bindingAdapterPosition) }
            holder.tvEnd.setOnClickListener { onEndClick(holder.bindingAdapterPosition) }
            holder.btnDelete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
        }
    }

    companion object {
        private const val REQ_ROTATION_APPS = 1
        private const val REQ_FILTER_APPS = 2
    }
}

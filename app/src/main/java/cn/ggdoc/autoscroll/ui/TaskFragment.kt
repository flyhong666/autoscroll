package cn.ggdoc.autoscroll.ui

import cn.ggdoc.autoscroll.util.registerReceiverSafe
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

/**
 * 任务页：自动点赞 / 广告屏蔽 / 定时停止 / 多APP轮换 / 屏幕常亮
 *       + 定时运行 / 低电量暂停 / 仅 Wi-Fi
 *       + 看广告得金币（高风险，需风险确认）
 *       + 统计看板（实时展示滚动 / 点赞 / 广告屏蔽 / 激励次数与时长）
 */
class TaskFragment : Fragment(), AppPickerDialogFragment.AppPickerResultListener {

    private lateinit var switchAutoLike: SwitchMaterial
    private lateinit var switchAdBlock: SwitchMaterial
    private lateinit var switchTimedStop: SwitchMaterial
    private lateinit var switchAppRotation: SwitchMaterial
    private lateinit var switchKeepScreenOn: SwitchMaterial
    private lateinit var switchSchedule: SwitchMaterial
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

    // 详情流（新闻 / 社交场景）参数滑块
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
    private lateinit var rvScheduleWindows: RecyclerView
    private lateinit var tvScheduleEmpty: TextView
    private lateinit var btnAddWindow: MaterialButton
    private lateinit var switchRecover: SwitchMaterial
    private lateinit var tvAdRewardInterval: TextView
    private lateinit var tvAdRewardAckWarn: TextView

    // 应用黑白名单
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

    private lateinit var btnSaveTasks: MaterialButton

    // 统计看板
    private lateinit var tvStatScroll: TextView
    private lateinit var tvStatLike: TextView
    private lateinit var tvStatAdBlock: TextView
    private lateinit var tvStatAdReward: TextView
    private lateinit var tvStatTime: TextView

    // 多时段定时窗口（分钟，0-1439），每个元素为 (开始, 结束)
    private var scheduleWindows: MutableList<Pair<Int, Int>> = mutableListOf()

    /** 监听无障碍服务状态广播，实时刷新统计看板 */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED) {
                refreshStats()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupSliders()
        setupSwitches()
        setupScheduleWindows()
        setupAppFilter()
        setupAdReward()
        loadSettingsToUI()
        btnSaveTasks.setOnClickListener { saveSettings() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED)
        requireContext().registerReceiverSafe(stateReceiver, filter)
        refreshStats()
    }

    override fun onPause() {
        super.onPause()
        runCatching { requireContext().unregisterReceiver(stateReceiver) }
    }

    private fun bindViews(v: View) {
        switchAutoLike = v.findViewById(R.id.switchAutoLike)
        switchAdBlock = v.findViewById(R.id.switchAdBlock)
        switchTimedStop = v.findViewById(R.id.switchTimedStop)
        switchAppRotation = v.findViewById(R.id.switchAppRotation)
        switchKeepScreenOn = v.findViewById(R.id.switchKeepScreenOn)
        switchSchedule = v.findViewById(R.id.switchSchedule)
        switchBatteryGuard = v.findViewById(R.id.switchBatteryGuard)
        switchWifiOnly = v.findViewById(R.id.switchWifiOnly)
        switchRecover = v.findViewById(R.id.switchRecover)
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
        rvScheduleWindows = v.findViewById(R.id.rvScheduleWindows)
        tvScheduleEmpty = v.findViewById(R.id.tvScheduleEmpty)
        btnAddWindow = v.findViewById(R.id.btnAddWindow)
        tvAdRewardInterval = v.findViewById(R.id.tvAdRewardInterval)
        tvAdRewardAckWarn = v.findViewById(R.id.tvAdRewardAckWarn)

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

        tvStatScroll = v.findViewById(R.id.tvStatScroll)
        tvStatLike = v.findViewById(R.id.tvStatLike)
        tvStatAdBlock = v.findViewById(R.id.tvStatAdBlock)
        tvStatAdReward = v.findViewById(R.id.tvStatAdReward)
        tvStatTime = v.findViewById(R.id.tvStatTime)

        btnSaveTasks = v.findViewById(R.id.btnSaveTasks)
    }

    private fun setupSliders() {
        sliderLikeProbability.addOnChangeListener { _, value, _ ->
            tvLikeProbability.text = "${value.toInt()}%"
        }
        sliderTimedStopMinutes.addOnChangeListener { _, value, _ ->
            tvTimedStopMinutes.text = "${value.toInt()}min"
        }
        sliderRotationMinutes.addOnChangeListener { _, value, _ ->
            tvRotationMinutes.text = "${value.toInt()}min"
        }
        sliderBatteryThreshold.addOnChangeListener { _, value, _ ->
            tvBatteryThreshold.text = "${value.toInt()}%"
        }
        sliderAdRewardInterval.addOnChangeListener { _, value, _ ->
            tvAdRewardInterval.text = "${value.toInt()}min"
        }

        // 详情流参数滑块：拖动时实时刷新数值，并由 AppConfig 的 coerce 保证合法
        sliderDetailDwellMin.addOnChangeListener { _, value, _ ->
            tvDetailDwellMin.text = "${value.toInt()}s"
        }
        sliderDetailDwellMax.addOnChangeListener { _, value, _ ->
            tvDetailDwellMax.text = "${value.toInt()}s"
        }
        sliderDetailReadAll.addOnChangeListener { _, value, _ ->
            tvDetailReadAll.text = "$value%"
        }
        sliderDetailMaxScrolls.addOnChangeListener { _, value, _ ->
            tvDetailMaxScrolls.text = value.toInt().toString()
        }
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
                val mode = modes[pos].first
                val list = AppConfig.getAppFilterList(requireContext())
                updateFilterSummary(mode, list)
                btnFilterApps.isEnabled = mode != AppConfig.FILTER_OFF
                // 选择变化时实时落盘，无需等「保存」按钮
                if (mode != AppConfig.getAppFilterMode(requireContext())) {
                    AppConfig.setAppFilterMode(requireContext(), mode)
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
        // M4 修复：onSaveInstanceState 之后 show() 会抛 IllegalStateException
        if (fm.isStateSaved || fm.isDestroyed) return
        val dialog = AppPickerDialogFragment.newInstance(AppConfig.getAppFilterList(requireContext()), REQ_FILTER_APPS)
        dialog.setTargetFragment(this, REQ_FILTER_APPS)
        dialog.show(fm, "filter_app_picker")
    }

    private fun setupAdReward() {
        checkAdRewardAck.setOnCheckedChangeListener { _, checked ->
            // 未确认风险时，开关禁用，并提示风险
            switchAdReward.isEnabled = checked
            tvAdRewardAckWarn.visibility = if (checked) View.GONE else View.VISIBLE
            // M8 修复：参数区可见性由「风险确认 ∧ 开关」共同决定，避免勾选确认但
            // 开关关闭时仍显示无效参数区
            updateAdRewardContainers()
        }
        switchAdReward.setOnCheckedChangeListener { _, _ ->
            updateAdRewardContainers()
        }
    }

    /** 广告奖励参数区的可见性：必须同时满足「已确认风险」且「开关开启」 */
    private fun updateAdRewardContainers() {
        val show = checkAdRewardAck.isChecked && switchAdReward.isChecked
        adRewardIntervalContainer.visibility = if (show) View.VISIBLE else View.GONE
        adRewardKeywordsContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

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
        val cur = if (isStart) scheduleWindows[index].first else scheduleWindows[index].second
        val h = cur / 60
        val m = cur % 60
        TimePickerDialog(requireContext(), { _, hh, mm ->
            val min = hh * 60 + mm
            val old = scheduleWindows[index]
            val newStart = if (isStart) min else old.first
            val newEnd = if (isStart) old.second else min
            // H1 附带修复：起止相同时 ScheduleUtils 视为「全天生效」，但闹钟会安排
            // 同一分钟的 START+END 导致启动即停止，行为矛盾。这里直接拦截。
            if (newStart == newEnd) {
                Toast.makeText(requireContext(), R.string.task_schedule_same_time, Toast.LENGTH_SHORT).show()
                return@TimePickerDialog
            }
            scheduleWindows[index] = newStart to newEnd
            refreshWindowUI()
        }, h, m, true).show()
    }

    private fun refreshStats() {
        tvStatScroll.text = AutoScrollAccessibilityService.scrollCount.toString()
        tvStatLike.text = AutoScrollAccessibilityService.likeCount.toString()
        tvStatAdBlock.text = AutoScrollAccessibilityService.adBlockCount.toString()
        tvStatAdReward.text = AutoScrollAccessibilityService.adRewardCount.toString()
        tvStatTime.text = formatDuration(AutoScrollAccessibilityService.runningSeconds)
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun loadSettingsToUI() {
        val ctx = requireContext()
        switchAutoLike.isChecked = AppConfig.isAutoLike(ctx)
        switchAdBlock.isChecked = AppConfig.isAdBlock(ctx)
        switchTimedStop.isChecked = AppConfig.isTimedStop(ctx)
        switchAppRotation.isChecked = AppConfig.isAppRotation(ctx)
        switchKeepScreenOn.isChecked = AppConfig.isKeepScreenOn(ctx)
        switchSchedule.isChecked = AppConfig.isScheduleEnabled(ctx)
        switchRecover.isChecked = AppConfig.isRecoverEnabled(ctx)
        switchBatteryGuard.isChecked = AppConfig.isBatteryGuard(ctx)
        switchWifiOnly.isChecked = AppConfig.isWifiOnly(ctx)

        // 应用黑白名单
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

        // 看广告得金币
        val acked = AppConfig.isAdRewardAcked(ctx)
        switchAdReward.isChecked = AppConfig.getAdRewardEnabledRaw(ctx)
        switchAdReward.isEnabled = acked
        checkAdRewardAck.isChecked = acked
        tvAdRewardAckWarn.visibility = if (acked) View.GONE else View.VISIBLE
        // M8 修复：统一走 updateAdRewardContainers 计算可见性（acked ∧ switch）
        updateAdRewardContainers()

        sliderAdRewardInterval.value = AppConfig.getAdRewardInterval(ctx).toFloat()
            .coerceIn(sliderAdRewardInterval.valueFrom, sliderAdRewardInterval.valueTo)
        tvAdRewardInterval.text = "${sliderAdRewardInterval.value.toInt()}min"
        etAdRewardKeywords.setText(AppConfig.getAdRewardKeywords(ctx).joinToString(","))

        // 详情流（新闻 / 社交）参数
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

    private fun updateRotationAppsSummary() {
        val count = AppConfig.getRotationApps(requireContext()).size
        tvRotationAppsSummary.text = if (count == 0) {
            getString(R.string.rotation_apps_empty)
        } else {
            getString(R.string.rotation_apps_count, count)
        }
    }

    private fun openAppPicker() {
        val fm = childFragmentManager
        // M4 修复：onSaveInstanceState 之后 show() 会抛 IllegalStateException
        if (fm.isStateSaved || fm.isDestroyed) return
        val dialog = AppPickerDialogFragment.newInstance(AppConfig.getRotationApps(requireContext()), REQ_ROTATION_APPS)
        dialog.setTargetFragment(this, REQ_ROTATION_APPS)
        dialog.show(fm, "rotation_app_picker")
    }

    /**
     * AppPicker 确认回调（targetFragment 机制，旋转重建后依然可达，
     * 不像普通字段会在重建时丢失）。M6 修复。
     */
    override fun onAppsConfirmed(requestCode: Int, selected: Set<String>) {
        val ctx = context ?: return
        when (requestCode) {
            REQ_ROTATION_APPS -> {
                AppConfig.setRotationApps(ctx, selected)
                updateRotationAppsSummary()
                // 实时刷新服务内的轮换池，无需重启滚动
                AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
            }
            REQ_FILTER_APPS -> {
                AppConfig.setAppFilterList(ctx, selected)
                val mode = AppConfig.getAppFilterMode(ctx)
                updateFilterSummary(mode, selected)
                // 实时刷新服务内的过滤列表，无需重启滚动
                AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
            }
        }
    }

    private fun saveSettings() {
        val ctx = requireContext()
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

        // 应用黑白名单（模式选择已在切换时实时落盘，这里再确保一次）
        val filterMode = when (spinnerFilterMode.selectedItemPosition) {
            1 -> AppConfig.FILTER_WHITELIST
            2 -> AppConfig.FILTER_BLACKLIST
            else -> AppConfig.FILTER_OFF
        }
        AppConfig.setAppFilterMode(ctx, filterMode)
        AppConfig.setAppFilterList(ctx, AppConfig.getAppFilterList(ctx))

        // 看广告得金币（高风险）：开关 + 风险确认 双条件
        AppConfig.setAdReward(ctx, switchAdReward.isChecked)
        AppConfig.setAdRewardAcked(ctx, checkAdRewardAck.isChecked)
        AppConfig.setAdRewardInterval(ctx, sliderAdRewardInterval.value.toInt())
        AppConfig.setAdRewardKeywords(
            ctx,
            AppConfig.parseKeywords(etAdRewardKeywords.text?.toString().orEmpty())
        )

        // 详情流（新闻 / 社交）参数：随滚动一并生效，决定拟人浏览的停留/读完/翻页上限
        AppConfig.setDetailFlowEnabled(ctx, switchDetailFlow.isChecked)
        AppConfig.setDetailDwellMin(ctx, sliderDetailDwellMin.value.toInt())
        AppConfig.setDetailDwellMax(ctx, sliderDetailDwellMax.value.toInt())
        AppConfig.setDetailReadAllProbability(ctx, sliderDetailReadAll.value.toInt())
        AppConfig.setDetailMaxScrolls(ctx, sliderDetailMaxScrolls.value.toInt())

        // 实时同步到无障碍服务（含定时闹钟重排、激励间隔重载）
        AutoScrollAccessibilityService.instance?.onScheduleConfigChanged()

        val msg = if (switchSchedule.isChecked) R.string.toast_schedule_set
        else R.string.toast_schedule_cancelled
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        Toast.makeText(ctx, R.string.toast_task_saved, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_ROTATION_APPS = 1
        private const val REQ_FILTER_APPS = 2
    }

    // ========== 多时段定时窗口列表适配器 ==========
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
            VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_schedule_window, parent, false)
            )

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
}

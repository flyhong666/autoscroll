package cn.ggdoc.autoscroll.ui

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
import androidx.fragment.app.Fragment
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
class TaskFragment : Fragment() {

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
    private lateinit var tvScheduleStart: TextView
    private lateinit var tvScheduleEnd: TextView
    private lateinit var tvAdRewardInterval: TextView
    private lateinit var tvAdRewardAckWarn: TextView

    private lateinit var likeProbabilityContainer: View
    private lateinit var timedStopContainer: View
    private lateinit var rotationContainer: View
    private lateinit var scheduleTimeContainer: View
    private lateinit var batteryThresholdContainer: View
    private lateinit var adRewardIntervalContainer: View
    private lateinit var adRewardKeywordsContainer: View

    private lateinit var etAdRewardKeywords: TextInputEditText

    private lateinit var btnSaveTasks: MaterialButton

    // 统计看板
    private lateinit var tvStatScroll: TextView
    private lateinit var tvStatLike: TextView
    private lateinit var tvStatAdBlock: TextView
    private lateinit var tvStatAdReward: TextView
    private lateinit var tvStatTime: TextView

    // 定时运行起止时间（分钟，0-1439）
    private var scheduleStartMin: Int = AppConfig.DEFAULT_SCHEDULE_START_MIN
    private var scheduleEndMin: Int = AppConfig.DEFAULT_SCHEDULE_END_MIN

    companion object {
        private const val TARGET_START = 0
        private const val TARGET_END = 1
    }

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
        setupTimePickers()
        setupAdReward()
        loadSettingsToUI()
        btnSaveTasks.setOnClickListener { saveSettings() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(stateReceiver, filter)
        }
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
        switchAdReward = v.findViewById(R.id.switchAdReward)
        checkAdRewardAck = v.findViewById(R.id.checkAdRewardAck)

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
        tvScheduleStart = v.findViewById(R.id.tvScheduleStart)
        tvScheduleEnd = v.findViewById(R.id.tvScheduleEnd)
        tvAdRewardInterval = v.findViewById(R.id.tvAdRewardInterval)
        tvAdRewardAckWarn = v.findViewById(R.id.tvAdRewardAckWarn)

        likeProbabilityContainer = v.findViewById(R.id.likeProbabilityContainer)
        timedStopContainer = v.findViewById(R.id.timedStopContainer)
        rotationContainer = v.findViewById(R.id.rotationContainer)
        scheduleTimeContainer = v.findViewById(R.id.scheduleTimeContainer)
        batteryThresholdContainer = v.findViewById(R.id.batteryThresholdContainer)
        adRewardIntervalContainer = v.findViewById(R.id.adRewardIntervalContainer)
        adRewardKeywordsContainer = v.findViewById(R.id.adRewardKeywordsContainer)

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
        }
        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            scheduleTimeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        switchBatteryGuard.setOnCheckedChangeListener { _, isChecked ->
            batteryThresholdContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupAdReward() {
        checkAdRewardAck.setOnCheckedChangeListener { _, checked ->
            // 未确认风险时，开关与参数区禁用，并提示风险
            switchAdReward.isEnabled = checked
            adRewardIntervalContainer.visibility = if (checked) View.VISIBLE else View.GONE
            adRewardKeywordsContainer.visibility = if (checked) View.VISIBLE else View.GONE
            tvAdRewardAckWarn.visibility = if (checked) View.GONE else View.VISIBLE
        }
    }

    private fun setupTimePickers() {
        tvScheduleStart.setOnClickListener { showTimePicker(TARGET_START) }
        tvScheduleEnd.setOnClickListener { showTimePicker(TARGET_END) }
    }

    private fun showTimePicker(target: Int) {
        val cur = if (target == TARGET_START) scheduleStartMin else scheduleEndMin
        val h = cur / 60
        val m = cur % 60
        TimePickerDialog(requireContext(), { _, hh, mm ->
            val min = hh * 60 + mm
            if (target == TARGET_START) scheduleStartMin = min else scheduleEndMin = min
            updateTimeLabels()
        }, h, m, true).show()
    }

    private fun updateTimeLabels() {
        tvScheduleStart.text = formatMinute(scheduleStartMin)
        tvScheduleEnd.text = formatMinute(scheduleEndMin)
    }

    private fun formatMinute(min: Int): String =
        String.format("%02d:%02d", min / 60, min % 60)

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
        switchBatteryGuard.isChecked = AppConfig.isBatteryGuard(ctx)
        switchWifiOnly.isChecked = AppConfig.isWifiOnly(ctx)

        sliderLikeProbability.value = AppConfig.getLikeProbability(ctx).toFloat()
            .coerceIn(sliderLikeProbability.valueFrom, sliderLikeProbability.valueTo)
        sliderTimedStopMinutes.value = AppConfig.getTimedStopMinutes(ctx).toFloat()
            .coerceIn(sliderTimedStopMinutes.valueFrom, sliderTimedStopMinutes.valueTo)
        sliderRotationMinutes.value = AppConfig.getRotationMinutes(ctx).toFloat()
            .coerceIn(sliderRotationMinutes.valueFrom, sliderRotationMinutes.valueTo)
        sliderBatteryThreshold.value = AppConfig.getBatteryThreshold(ctx).toFloat()
            .coerceIn(sliderBatteryThreshold.valueFrom, sliderBatteryThreshold.valueTo)

        scheduleStartMin = AppConfig.getScheduleStartMin(ctx)
        scheduleEndMin = AppConfig.getScheduleEndMin(ctx)
        updateTimeLabels()

        tvLikeProbability.text = "${sliderLikeProbability.value.toInt()}%"
        tvTimedStopMinutes.text = "${sliderTimedStopMinutes.value.toInt()}min"
        tvRotationMinutes.text = "${sliderRotationMinutes.value.toInt()}min"
        tvBatteryThreshold.text = "${sliderBatteryThreshold.value.toInt()}%"

        likeProbabilityContainer.visibility = if (switchAutoLike.isChecked) View.VISIBLE else View.GONE
        timedStopContainer.visibility = if (switchTimedStop.isChecked) View.VISIBLE else View.GONE
        rotationContainer.visibility = if (switchAppRotation.isChecked) View.VISIBLE else View.GONE
        scheduleTimeContainer.visibility = if (switchSchedule.isChecked) View.VISIBLE else View.GONE
        batteryThresholdContainer.visibility = if (switchBatteryGuard.isChecked) View.VISIBLE else View.GONE

        // 看广告得金币
        val acked = AppConfig.isAdRewardAcked(ctx)
        switchAdReward.isChecked = AppConfig.getAdRewardEnabledRaw(ctx)
        switchAdReward.isEnabled = acked
        checkAdRewardAck.isChecked = acked
        adRewardIntervalContainer.visibility = if (acked) View.VISIBLE else View.GONE
        adRewardKeywordsContainer.visibility = if (acked) View.VISIBLE else View.GONE
        tvAdRewardAckWarn.visibility = if (acked) View.GONE else View.VISIBLE

        sliderAdRewardInterval.value = AppConfig.getAdRewardInterval(ctx).toFloat()
            .coerceIn(sliderAdRewardInterval.valueFrom, sliderAdRewardInterval.valueTo)
        tvAdRewardInterval.text = "${sliderAdRewardInterval.value.toInt()}min"
        etAdRewardKeywords.setText(AppConfig.getAdRewardKeywords(ctx).joinToString(","))

        // 详情流（新闻 / 社交）参数
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
        AppConfig.setScheduleStartMin(ctx, scheduleStartMin)
        AppConfig.setScheduleEndMin(ctx, scheduleEndMin)
        AppConfig.setBatteryGuard(ctx, switchBatteryGuard.isChecked)
        AppConfig.setBatteryThreshold(ctx, sliderBatteryThreshold.value.toInt())
        AppConfig.setWifiOnly(ctx, switchWifiOnly.isChecked)

        // 看广告得金币（高风险）：开关 + 风险确认 双条件
        AppConfig.setAdReward(ctx, switchAdReward.isChecked)
        AppConfig.setAdRewardAcked(ctx, checkAdRewardAck.isChecked)
        AppConfig.setAdRewardInterval(ctx, sliderAdRewardInterval.value.toInt())
        AppConfig.setAdRewardKeywords(
            ctx,
            AppConfig.parseKeywords(etAdRewardKeywords.text?.toString().orEmpty())
        )

        // 详情流（新闻 / 社交）参数：随滚动一并生效，决定拟人浏览的停留/读完/翻页上限
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
}

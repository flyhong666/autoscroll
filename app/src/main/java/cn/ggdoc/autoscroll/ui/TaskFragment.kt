package cn.ggdoc.autoscroll.ui

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
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 任务页：自动点赞 / 广告屏蔽 / 定时停止 / 多APP轮换 / 屏幕常亮
 */
class TaskFragment : Fragment() {

    private lateinit var switchAutoLike: SwitchMaterial
    private lateinit var switchAdBlock: SwitchMaterial
    private lateinit var switchTimedStop: SwitchMaterial
    private lateinit var switchAppRotation: SwitchMaterial
    private lateinit var switchKeepScreenOn: SwitchMaterial

    private lateinit var sliderLikeProbability: Slider
    private lateinit var sliderTimedStopMinutes: Slider
    private lateinit var sliderRotationMinutes: Slider

    private lateinit var tvLikeProbability: TextView
    private lateinit var tvTimedStopMinutes: TextView
    private lateinit var tvRotationMinutes: TextView

    private lateinit var likeProbabilityContainer: View
    private lateinit var timedStopContainer: View
    private lateinit var rotationContainer: View

    private lateinit var btnSaveTasks: MaterialButton

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
        loadSettingsToUI()
        btnSaveTasks.setOnClickListener { saveSettings() }
    }

    private fun bindViews(v: View) {
        switchAutoLike = v.findViewById(R.id.switchAutoLike)
        switchAdBlock = v.findViewById(R.id.switchAdBlock)
        switchTimedStop = v.findViewById(R.id.switchTimedStop)
        switchAppRotation = v.findViewById(R.id.switchAppRotation)
        switchKeepScreenOn = v.findViewById(R.id.switchKeepScreenOn)

        sliderLikeProbability = v.findViewById(R.id.sliderLikeProbability)
        sliderTimedStopMinutes = v.findViewById(R.id.sliderTimedStopMinutes)
        sliderRotationMinutes = v.findViewById(R.id.sliderRotationMinutes)

        tvLikeProbability = v.findViewById(R.id.tvLikeProbability)
        tvTimedStopMinutes = v.findViewById(R.id.tvTimedStopMinutes)
        tvRotationMinutes = v.findViewById(R.id.tvRotationMinutes)

        likeProbabilityContainer = v.findViewById(R.id.likeProbabilityContainer)
        timedStopContainer = v.findViewById(R.id.timedStopContainer)
        rotationContainer = v.findViewById(R.id.rotationContainer)

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
    }

    private fun loadSettingsToUI() {
        val ctx = requireContext()
        switchAutoLike.isChecked = AppConfig.isAutoLike(ctx)
        switchAdBlock.isChecked = AppConfig.isAdBlock(ctx)
        switchTimedStop.isChecked = AppConfig.isTimedStop(ctx)
        switchAppRotation.isChecked = AppConfig.isAppRotation(ctx)
        switchKeepScreenOn.isChecked = AppConfig.isKeepScreenOn(ctx)

        sliderLikeProbability.value = AppConfig.getLikeProbability(ctx).toFloat()
            .coerceIn(sliderLikeProbability.valueFrom, sliderLikeProbability.valueTo)
        sliderTimedStopMinutes.value = AppConfig.getTimedStopMinutes(ctx).toFloat()
            .coerceIn(sliderTimedStopMinutes.valueFrom, sliderTimedStopMinutes.valueTo)
        sliderRotationMinutes.value = AppConfig.getRotationMinutes(ctx).toFloat()
            .coerceIn(sliderRotationMinutes.valueFrom, sliderRotationMinutes.valueTo)

        tvLikeProbability.text = "${sliderLikeProbability.value.toInt()}%"
        tvTimedStopMinutes.text = "${sliderTimedStopMinutes.value.toInt()}min"
        tvRotationMinutes.text = "${sliderRotationMinutes.value.toInt()}min"

        likeProbabilityContainer.visibility = if (switchAutoLike.isChecked) View.VISIBLE else View.GONE
        timedStopContainer.visibility = if (switchTimedStop.isChecked) View.VISIBLE else View.GONE
        rotationContainer.visibility = if (switchAppRotation.isChecked) View.VISIBLE else View.GONE
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

        // 实时同步到无障碍服务
        AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()

        Toast.makeText(ctx, R.string.toast_task_saved, Toast.LENGTH_SHORT).show()
    }
}

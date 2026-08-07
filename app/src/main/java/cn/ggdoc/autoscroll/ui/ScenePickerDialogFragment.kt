package cn.ggdoc.autoscroll.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.CustomGestureStep
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.databinding.DialogGestureStepBinding
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 场景选择弹窗：6 大场景卡片选择；选中「自定义通用」时显示可编排的手势序列编辑器。
 * 从控制页「设置」入口打开，替代原先独立的场景 Tab 页。
 */
class ScenePickerDialogFragment : DialogFragment() {

    /** 场景发生切换时回调（通知调用方刷新摘要） */
    var onSceneChanged: (() -> Unit)? = null

    private lateinit var rvScenes: RecyclerView
    private lateinit var rvGestureSteps: RecyclerView
    private lateinit var stepAdapter: GestureStepAdapter
    private lateinit var customGesturePanel: MaterialCardView
    private lateinit var tvGestureEmpty: View
    private var currentSelectedId: String = AppConfig.SCENE_SHORT_VIDEO

    private val steps = mutableListOf<CustomGestureStep>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_scene_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvScenes = view.findViewById(R.id.rvScenes)
        rvScenes.layoutManager = LinearLayoutManager(requireContext())

        rvGestureSteps = view.findViewById(R.id.rvGestureSteps)
        rvGestureSteps.layoutManager = LinearLayoutManager(requireContext())
        stepAdapter = GestureStepAdapter(
            onUp = { moveStep(it, -1) },
            onDown = { moveStep(it, 1) },
            onDelete = { deleteStep(it) }
        )
        rvGestureSteps.adapter = stepAdapter

        customGesturePanel = view.findViewById(R.id.customGesturePanel)
        tvGestureEmpty = view.findViewById(R.id.tvGestureEmpty)

        view.findViewById<MaterialButton>(R.id.btnAddGesture).setOnClickListener { showStepEditor(-1) }
        view.findViewById<MaterialButton>(R.id.btnSceneDone).setOnClickListener { dismiss() }

        currentSelectedId = AppConfig.getCurrentScene(requireContext())
        bindAdapter()
        refreshGesturePanel()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun bindAdapter() {
        val scenes = SceneConfig.getAllScenes()
        rvScenes.adapter = SceneAdapter(scenes, currentSelectedId) { scene ->
            currentSelectedId = scene.id
            AppConfig.setCurrentScene(requireContext(), scene.id)
            AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
            Toast.makeText(requireContext(), R.string.toast_scene_changed, Toast.LENGTH_SHORT).show()
            bindAdapter()
            refreshGesturePanel()
            onSceneChanged?.invoke()
        }
    }

    private fun refreshGesturePanel() {
        val isCustom = currentSelectedId == AppConfig.SCENE_CUSTOM
        customGesturePanel.visibility = if (isCustom) View.VISIBLE else View.GONE
        if (!isCustom) return
        steps.clear()
        steps.addAll(AppConfig.getCustomGestureSequence(requireContext()))
        stepAdapter.submit(steps)
        tvGestureEmpty.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveSteps() {
        AppConfig.setCustomGestureSequence(requireContext(), steps.toList())
        AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
    }

    private fun moveStep(pos: Int, dir: Int) {
        val target = pos + dir
        if (target < 0 || target >= steps.size) return
        steps.add(target, steps.removeAt(pos))
        saveSteps()
        stepAdapter.submit(steps)
    }

    private fun deleteStep(pos: Int) {
        steps.removeAt(pos)
        saveSteps()
        stepAdapter.submit(steps)
        tvGestureEmpty.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 编辑 / 新增一步手势。editPos = -1 表示新增 */
    private fun showStepEditor(editPos: Int) {
        val binding = DialogGestureStepBinding.inflate(layoutInflater)
        val isEdit = editPos >= 0
        val existing = if (isEdit) steps[editPos] else null

        val spinner = binding.spinnerGesture
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            CustomGestureStep.GESTURE_LABELS
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val initialTypeIndex = if (existing != null) {
            CustomGestureStep.GESTURE_TYPES.indexOf(existing.gesture).coerceAtLeast(0)
        } else 0
        spinner.setSelection(initialTypeIndex)

        binding.sliderWait.value = (existing?.waitSec ?: 2).toFloat()
        binding.sliderX.value = (existing?.xPct ?: 50).toFloat()
        binding.sliderY.value = (existing?.yPct ?: 50).toFloat()
        binding.sliderDist.value = (existing?.distPct ?: 70).toFloat()
        updateStepDialogText(binding)

        val onTypeChanged = {
            val type = CustomGestureStep.GESTURE_TYPES[spinner.selectedItemPosition]
            val isSwipe = type in setOf(
                CustomGestureStep.TYPE_SWIPE_UP, CustomGestureStep.TYPE_SWIPE_DOWN,
                CustomGestureStep.TYPE_SWIPE_LEFT, CustomGestureStep.TYPE_SWIPE_RIGHT
            )
            val isWait = type == CustomGestureStep.TYPE_WAIT
            binding.tvDistLabel.visibility = if (isSwipe) View.VISIBLE else View.GONE
            binding.sliderDist.visibility = if (isSwipe) View.VISIBLE else View.GONE
            binding.tvPosLabel.visibility = if (isWait) View.GONE else View.VISIBLE
            binding.sliderX.visibility = if (isWait) View.GONE else View.VISIBLE
            binding.sliderY.visibility = if (isWait) View.GONE else View.VISIBLE
            binding.tvX.visibility = if (isWait) View.GONE else View.VISIBLE
            binding.tvY.visibility = if (isWait) View.GONE else View.VISIBLE
        }
        spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) = onTypeChanged()
            override fun onNothingSelected(p: android.widget.AdapterView<*>) {}
        })
        onTypeChanged()

        val listeners = listOf(binding.sliderWait, binding.sliderX, binding.sliderY, binding.sliderDist).map { s ->
            com.google.android.material.slider.Slider.OnChangeListener { _, _, _ -> updateStepDialogText(binding) }
        }
        binding.sliderWait.addOnChangeListener(listeners[0])
        binding.sliderX.addOnChangeListener(listeners[1])
        binding.sliderY.addOnChangeListener(listeners[2])
        binding.sliderDist.addOnChangeListener(listeners[3])

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) R.string.custom_gesture_edit else R.string.custom_gesture_add)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val type = CustomGestureStep.GESTURE_TYPES[spinner.selectedItemPosition]
                val step = CustomGestureStep(
                    gesture = type,
                    waitSec = binding.sliderWait.value.toInt(),
                    xPct = binding.sliderX.value.toInt(),
                    yPct = binding.sliderY.value.toInt(),
                    distPct = binding.sliderDist.value.toInt()
                )
                if (isEdit) steps[editPos] = step else steps.add(step)
                saveSteps()
                stepAdapter.submit(steps)
                tvGestureEmpty.visibility = View.GONE
            }
            .show()
    }

    private fun updateStepDialogText(binding: DialogGestureStepBinding) {
        binding.tvWait.text = getString(R.string.custom_gesture_wait_value, binding.sliderWait.value.toInt())
        binding.tvX.text = getString(R.string.custom_gesture_x_value, binding.sliderX.value.toInt())
        binding.tvY.text = getString(R.string.custom_gesture_y_value, binding.sliderY.value.toInt())
        binding.tvDist.text = getString(R.string.custom_gesture_dist_value, binding.sliderDist.value.toInt())
    }
}

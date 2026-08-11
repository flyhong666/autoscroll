package cn.ggdoc.autoscroll.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.RecordedAction
import cn.ggdoc.autoscroll.recorder.RecordedScript
import cn.ggdoc.autoscroll.recorder.ScriptStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 脚本编辑器：对已有脚本的步骤做增 / 删 / 改 / 加，
 * 保存时通过 [ScriptStore.save] 覆写原文件。
 *
 * 从 [RecorderFragment] 的「更多→编辑步骤」唤起。
 */
class ScriptEditorDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_FILE_NAME = "file_name"

        fun newInstance(fileName: String, onSaved: () -> Unit): ScriptEditorDialogFragment {
            return ScriptEditorDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_FILE_NAME, fileName) }
                this.onSavedCallback = onSaved
            }
        }
    }

    private var onSavedCallback: (() -> Unit)? = null
    private lateinit var fileName: String
    private lateinit var script: RecordedScript
    private lateinit var steps: MutableList<RecordedAction>

    private lateinit var rvSteps: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var adapter: StepAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_script_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fileName = arguments?.getString(ARG_FILE_NAME).orEmpty()
        if (fileName.isBlank()) {
            dismiss()
            return
        }
        val loaded = ScriptStore.load(requireContext(), fileName)
        if (loaded == null) {
            Toast.makeText(requireContext(), R.string.toast_script_load_failed, Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        script = loaded
        steps = loaded.actions.toMutableList()

        tvTitle = view.findViewById(R.id.tvEditorTitle)
        tvSubtitle = view.findViewById(R.id.tvEditorSubtitle)
        tvEmpty = view.findViewById(R.id.tvEditorEmpty)
        rvSteps = view.findViewById(R.id.rvEditorSteps)
        btnSave = view.findViewById(R.id.btnSave)

        tvTitle.text = getString(R.string.editor_title, script.name)
        refreshMeta()

        adapter = StepAdapter(steps, ::onStepClick, ::onStepDelete)
        rvSteps.layoutManager = LinearLayoutManager(requireContext())
        rvSteps.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnAddWait).setOnClickListener {
            steps.add(RecordedAction(type = RecordedAction.TYPE_WAIT, duration = 1000L, delay = 0L))
            refreshAll()
        }
        view.findViewById<MaterialButton>(R.id.btnAddClick).setOnClickListener {
            val dm = requireContext().resources.displayMetrics
            val cx = dm.widthPixels / 2
            val cy = (dm.heightPixels * 0.55f).toInt()
            steps.add(
                RecordedAction(
                    type = RecordedAction.TYPE_CLICK,
                    x = cx, y = cy,
                    duration = 60L, delay = 0L,
                    desc = "中央点击（新建）"
                )
            )
            refreshAll()
        }
        btnSave.setOnClickListener { onSaveClicked() }
    }

    override fun onStart() {
        super.onStart()
        // 底部弹窗风格：最大高度 85% 屏幕
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.85f).toInt()
        )
    }

    private fun refreshMeta() {
        tvSubtitle.text = getString(R.string.editor_subtitle, steps.size)
        tvEmpty.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshAll() {
        refreshMeta()
        adapter.notifyDataSetChanged()
    }

    // ---------- 单步交互 ----------

    private fun onStepClick(pos: Int) {
        val step = steps.getOrNull(pos) ?: return
        showStepEditor(pos, step)
    }

    private fun onStepDelete(pos: Int) {
        if (pos < 0 || pos >= steps.size) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除第 ${pos + 1} 步？")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_menu_delete) { _, _ ->
                steps.removeAt(pos)
                refreshAll()
            }
            .show()
    }

    /**
     * 简易单步字段编辑对话框：
     * 用垂直堆叠的多个 EditText 暴露核心字段；坐标/时长/间隔统一用数字输入，
     * 类型通过单选项切换（click / longClick / swipe / wait）。
     */
    private fun showStepEditor(pos: Int, s: RecordedAction) {
        val ctx = requireContext()
        val types = arrayOf(
            RecordedAction.TYPE_CLICK to ctx.getString(R.string.editor_action_click),
            RecordedAction.TYPE_LONG_CLICK to ctx.getString(R.string.editor_action_long_click),
            RecordedAction.TYPE_SWIPE to ctx.getString(R.string.editor_action_swipe),
            RecordedAction.TYPE_WAIT to ctx.getString(R.string.editor_action_wait)
        )
        var selectedType = s.type

        val etX = EditText(ctx).apply {
            hint = "X（像素）"
            setText(s.x.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etY = EditText(ctx).apply {
            hint = "Y（像素）"
            setText(s.y.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etX2 = EditText(ctx).apply {
            hint = "X2（滑动终点，像素）"
            setText(s.x2.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etY2 = EditText(ctx).apply {
            hint = "Y2（滑动终点，像素）"
            setText(s.y2.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etDelay = EditText(ctx).apply {
            hint = ctx.getString(R.string.editor_edit_delay)
            setText(s.delay.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etDuration = EditText(ctx).apply {
            hint = ctx.getString(R.string.editor_edit_duration)
            setText(s.duration.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 }

        container.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.editor_edit_type)
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, ctx.theme))
        }, lp)

        val typeLabels = types.map { it.second }.toTypedArray()
        val typeSelectedIdx = types.indexOfFirst { it.first == s.type }.coerceAtLeast(0)
        var chosenTypeIdx = typeSelectedIdx

        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle(ctx.getString(R.string.editor_edit_title, pos + 1))
            .setSingleChoiceItems(typeLabels, typeSelectedIdx) { _, which -> chosenTypeIdx = which }
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val t = types[chosenTypeIdx].first
                val x = etX.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val y = etY.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val x2 = etX2.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val y2 = etY2.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val delay = etDelay.text?.toString()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
                val duration = etDuration.text?.toString()?.toLongOrNull()?.coerceAtLeast(0)
                    ?: if (t == RecordedAction.TYPE_LONG_CLICK) 600L else 60L
                steps[pos] = s.copy(
                    type = t,
                    x = x, y = y, x2 = x2, y2 = y2,
                    delay = delay, duration = duration
                )
                refreshAll()
            }
            .create()

        // 类型选完再把数值输入加进去，避免单选项和 EditText 挤在一起
        dlg.setOnShowListener {
            container.addView(etDelay, lp)
            container.addView(etDuration, lp)
            if (chosenTypeIdx != types.indexOfFirst { it.first == RecordedAction.TYPE_WAIT }) {
                container.addView(etX, lp)
                container.addView(etY, lp)
                if (chosenTypeIdx == types.indexOfFirst { it.first == RecordedAction.TYPE_SWIPE }) {
                    container.addView(etX2, lp)
                    container.addView(etY2, lp)
                }
            }
        }
        dlg.show()
    }

    // ---------- 保存 ----------

    private fun onSaveClicked() {
        val updated = script.copy(actions = steps.toList())
        val ok = ScriptStore.save(requireContext(), updated, fileName) != null
        if (ok) {
            Toast.makeText(
                requireContext(),
                getString(R.string.editor_saved, steps.size),
                Toast.LENGTH_SHORT
            ).show()
            onSavedCallback?.invoke()
            dismiss()
        } else {
            Toast.makeText(requireContext(), R.string.editor_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Adapter ----------

    private class StepAdapter(
        private val items: MutableList<RecordedAction>,
        private val onClick: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<StepAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvIndex: TextView = v.findViewById(R.id.tvStepIndex)
            val tvAction: TextView = v.findViewById(R.id.tvStepAction)
            val tvMeta: TextView = v.findViewById(R.id.tvStepMeta)
            val btnDelete: View = v.findViewById(R.id.btnStepDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_script_step_editor, parent, false)
            )

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val a = items[position]
            val ctx = holder.itemView.context
            holder.tvIndex.text = (position + 1).toString()
            val actionStr = when (a.type) {
                RecordedAction.TYPE_CLICK -> ctx.getString(R.string.editor_action_click) +
                        " (${a.x}, ${a.y})"
                RecordedAction.TYPE_LONG_CLICK -> ctx.getString(R.string.editor_action_long_click) +
                        " (${a.x}, ${a.y})"
                RecordedAction.TYPE_SWIPE -> ctx.getString(R.string.editor_action_swipe) +
                        " (${a.x}, ${a.y}) → (${a.x2}, ${a.y2})"
                RecordedAction.TYPE_WAIT -> ctx.getString(R.string.editor_action_wait)
                else -> a.type
            }
            val desc = if (a.desc.isNotBlank()) "「${a.desc}」" else ""
            holder.tvAction.text = "$actionStr $desc"
            holder.tvMeta.text = buildString {
                append(ctx.getString(R.string.editor_edit_delay).take(4))
                append(" ${a.delay}ms · ")
                append(ctx.getString(R.string.editor_edit_duration).take(2))
                append(" ${a.duration}ms")
            }
            holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
            holder.btnDelete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
        }
    }
}

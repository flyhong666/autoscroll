package cn.ggdoc.autoscroll.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.recorder.ScriptPlayer
import cn.ggdoc.autoscroll.recorder.ScriptStore
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.RecorderOverlayService
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

/**
 * 操作记录器（Tab 页）：录制、管理、回放用户操作脚本。
 * 功能与 [ScriptActivity] 一致，但作为主界面底部导航的独立 Tab 展示，
 * 不再需要单独打开一个 Activity。
 */
class RecorderFragment : Fragment() {

    private lateinit var rvScripts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnStartRecord: MaterialButton
    private lateinit var adapter: ScriptAdapter

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_recorder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvScripts = view.findViewById(R.id.rvScripts)
        tvEmpty = view.findViewById(R.id.tvScriptEmpty)
        btnStartRecord = view.findViewById(R.id.btnStartRecord)

        adapter = ScriptAdapter(emptyList(), ::onPlayClicked, ::onMoreClicked)
        rvScripts.layoutManager = LinearLayoutManager(requireContext())
        rvScripts.adapter = adapter

        btnStartRecord.setOnClickListener { onRecordClicked() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refreshList() {
        if (!::adapter.isInitialized) return
        val list = ScriptStore.list(requireContext())
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    // ========== 录制 ==========

    private fun onRecordClicked() {
        if (!ensureReady()) return
        if (ActionRecorder.isRecording) {
            toast(R.string.toast_record_already)
            return
        }
        // Android 9（API 28）以下：无障碍事件不携带滑动方向（scrollDeltaX/Y），
        // 录制结果不准确，先弹出版本过低提示，由用户决定是否仍要开始。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.recorder_version_low_title)
                .setMessage(R.string.recorder_version_low_msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.recorder_version_low_continue) { _, _ -> showRecordConfirm() }
                .show()
            return
        }
        showRecordConfirm()
    }

    private fun showRecordConfirm() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.script_record_confirm_title)
            .setMessage(R.string.script_record_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_record_start_now) { _, _ -> beginRecording() }
            .show()
    }

    private fun beginRecording() {
        ActionRecorder.start(requireContext())
        RecorderOverlayService.start(requireContext(), RecorderOverlayService.MODE_RECORD)
        toast(R.string.toast_record_started)
        // 退到后台，让用户切到目标 APP 操作
        handler.postDelayed({
            runCatching { requireActivity().moveTaskToBack(true) }
        }, 300L)
    }

    // 录制结束时由悬浮条调用 ScriptStore + ActionRecorder.stopAndSave 落盘，
    // 这里监听列表刷新（RecorderOverlayService 结束录制后通过广播/列表更新），
    // 同时在 onResume 时刷新，保证返回本页能看到最新脚本。

    // ========== 回放 ==========

    private fun onPlayClicked(entry: ScriptStore.Entry) {
        if (!ensureReady()) return
        if (ScriptPlayer.isPlaying) {
            toast(R.string.toast_script_already_playing)
            return
        }
        val script = ScriptStore.load(requireContext(), entry.fileName)
        if (script == null || script.actions.isEmpty()) {
            toast(R.string.toast_script_load_failed)
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_play_script, null)
        val sliderLoops = view.findViewById<Slider>(R.id.sliderPlayLoops)
        val sliderSpeed = view.findViewById<Slider>(R.id.sliderPlaySpeed)
        val tvLoops = view.findViewById<TextView>(R.id.tvPlayLoops)
        val tvSpeed = view.findViewById<TextView>(R.id.tvPlaySpeed)

        tvLoops.text = getString(R.string.script_loops_value, sliderLoops.value.toInt())
        tvSpeed.text = getString(R.string.script_speed_value, sliderSpeed.value)
        sliderLoops.addOnChangeListener { _, v, _ ->
            tvLoops.text = getString(R.string.script_loops_value, v.toInt())
        }
        sliderSpeed.addOnChangeListener { _, v, _ ->
            tvSpeed.text = getString(R.string.script_speed_value, v)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(script.name)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_run) { _, _ ->
                beginPlay(entry, sliderLoops.value.toInt(), sliderSpeed.value)
            }
            .show()
    }

    private fun beginPlay(entry: ScriptStore.Entry, loops: Int, speed: Float) {
        val ctx = requireContext()
        val script = ScriptStore.load(ctx, entry.fileName) ?: return
        val launched = launchTargetApp(script.pkg)
        if (!launched) runCatching { requireActivity().moveTaskToBack(true) }
        toast(R.string.toast_script_play_starting)

        handler.postDelayed({
            val service = AutoScrollAccessibilityService.instance
            if (service == null) {
                toast(R.string.toast_accessibility_disconnected)
                return@postDelayed
            }
            if (ScriptPlayer.play(service, script, loops, speed)) {
                RecorderOverlayService.start(ctx, RecorderOverlayService.MODE_PLAY)
            }
        }, if (launched) 1800L else 700L)
    }

    private fun launchTargetApp(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == requireContext().packageName) return false
        return try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== 更多操作 ==========

    private fun onMoreClicked(entry: ScriptStore.Entry, anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add(0, MENU_DETAIL, 0, R.string.script_menu_detail)
        menu.menu.add(0, MENU_EDIT, 1, R.string.script_menu_edit)
        menu.menu.add(0, MENU_RENAME, 2, R.string.script_menu_rename)
        menu.menu.add(0, MENU_EXPORT, 3, R.string.script_menu_export)
        menu.menu.add(0, MENU_DELETE, 4, R.string.script_menu_delete)
        menu.menu.add(0, MENU_IMPORT, 5, R.string.script_menu_import)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DETAIL -> showDetail(entry)
                MENU_EDIT -> openEditor(entry)
                MENU_RENAME -> showRename(entry)
                MENU_EXPORT -> exportScript(entry)
                MENU_DELETE -> confirmDelete(entry)
                MENU_IMPORT -> showImportPicker()
            }
            true
        }
        menu.show()
    }

    private fun openEditor(entry: ScriptStore.Entry) {
        if (!isAdded) return
        val fm = parentFragmentManager
        if (fm.isStateSaved) return
        ScriptEditorDialogFragment.newInstance(entry.fileName) { refreshList() }
            .show(fm, "ScriptEditorDialog")
    }

    /**
     * 展示外部专属目录 scripts/ 下的可导入文件列表。
     * 选中后通过 [ScriptStore.importFromExternal] 复制到私有目录并刷新。
     */
    private fun showImportPicker() {
        val ctx = requireContext()
        val files = ScriptStore.listExternalImportable(ctx)
        if (files.isEmpty()) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.script_menu_import)
                .setMessage("外部目录（Android/data/${ctx.packageName}/files/scripts）下没有可导入的脚本。\n\n先从某条脚本点「导出」，或把 .json 脚本文件放到上述目录后再导入。")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = files.map { it.name }.toTypedArray()
        var chosen = -1
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.script_menu_import)
            .setSingleChoiceItems(labels, -1) { _, which -> chosen = which }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("导入") { _, _ ->
                if (chosen < 0) return@setPositiveButton
                val src = files[chosen]
                val newName = ScriptStore.importFromExternal(ctx, src)
                if (newName != null) {
                    toast(getString(R.string.editor_import_done, newName))
                    refreshList()
                } else {
                    toast(R.string.editor_import_failed)
                }
            }
            .show()
    }

    private fun showDetail(entry: ScriptStore.Entry) {
        val script = ScriptStore.load(requireContext(), entry.fileName) ?: return
        val body = script.actions
            .take(MAX_DETAIL_LINES)
            .mapIndexed { i, a -> a.readable(i) }
            .joinToString("\n")
        val more = if (script.actions.size > MAX_DETAIL_LINES) {
            "\n… 其余 ${script.actions.size - MAX_DETAIL_LINES} 步略"
        } else ""
        val header = getString(
            R.string.script_detail_header,
            script.pkg.ifBlank { "-" },
            script.actions.size,
            (script.estimatedMs / 1000f).toInt()
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(script.name)
            .setMessage(header + "\n\n" + body + more)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showRename(entry: ScriptStore.Entry) {
        val input = EditText(requireContext()).apply {
            setText(entry.name)
            setSelection(entry.name.length)
            setPadding(56, 32, 56, 8)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.script_menu_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    toast(R.string.toast_script_name_empty)
                    return@setPositiveButton
                }
                if (ScriptStore.rename(requireContext(), entry.fileName, newName)) {
                    refreshList()
                } else {
                    toast(R.string.toast_script_rename_failed)
                }
            }
            .show()
    }

    private fun exportScript(entry: ScriptStore.Entry) {
        val file = ScriptStore.export(requireContext(), entry.fileName)
        if (file == null) {
            toast(R.string.toast_script_export_failed)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.script_menu_export)
                .setMessage(getString(R.string.script_export_done, file.absolutePath))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun confirmDelete(entry: ScriptStore.Entry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.script_menu_delete)
            .setMessage(getString(R.string.script_delete_confirm, entry.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_menu_delete) { _, _ ->
                if (ScriptStore.delete(requireContext(), entry.fileName)) refreshList()
                else toast(R.string.toast_script_delete_failed)
            }
            .show()
    }

    // ========== 前置条件 ==========

    /** 录制 / 回放都依赖无障碍服务 + 悬浮窗权限 */
    private fun ensureReady(): Boolean {
        if (AutoScrollAccessibilityService.instance == null) {
            toast(R.string.toast_accessibility_disconnected)
            return false
        }
        if (!Settings.canDrawOverlays(requireContext())) {
            toast(R.string.toast_overlay_permission_missing)
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                )
            } catch (_: Exception) {
            }
            return false
        }
        return true
    }

    private fun toast(resId: Int) = Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    private fun toast(msg: CharSequence) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MENU_DETAIL = 1
        private const val MENU_EDIT = 2
        private const val MENU_RENAME = 3
        private const val MENU_EXPORT = 4
        private const val MENU_DELETE = 5
        private const val MENU_IMPORT = 6
        private const val MAX_DETAIL_LINES = 80
    }
}

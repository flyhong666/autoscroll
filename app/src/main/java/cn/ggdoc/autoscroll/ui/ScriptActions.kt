package cn.ggdoc.autoscroll.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.recorder.ScriptPlayer
import cn.ggdoc.autoscroll.recorder.ScriptStore
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.RecorderOverlayService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

/**
 * 脚本列表页（[RecorderFragment]）公共操作抽离。
 *
 * 两个宿主原先各自维护约 200 行重复逻辑（录制确认 / 回放弹窗 / 详情 /
 * 重命名 / 导出 / 删除 / 导入 / 权限校验），此处收敛为唯一实现。
 * 宿主差异（刷新列表、打开编辑器、退后台等）通过 [ScriptHost] 接口注入。
 */
class ScriptActions(private val host: ScriptHost) {

    /** 宿主抽象：Fragment / Activity 各自的实现注入到这里 */
    interface ScriptHost {
        val ctx: Context
        fun toast(resId: Int)
        fun toast(msg: CharSequence)
        fun refreshList()
        fun moveTaskToBack()
        fun startActivity(intent: Intent)
        /** 打开脚本步骤编辑器（宿主负责弹窗管理与回调接线） */
        fun openEditor(fileName: String)
    }

    private val handler = Handler(Looper.getMainLooper())

    // ========== 录制 ==========

    fun onRecordClicked() {
        if (!ensureReady()) return
        if (ActionRecorder.isRecording) {
            host.toast(R.string.toast_record_already)
            return
        }
        // Android 9（API 28）以下：无障碍事件不携带滑动方向（scrollDeltaX/Y），
        // 录制结果不准确，先弹出版本过低提示，由用户决定是否仍要开始
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            MaterialAlertDialogBuilder(host.ctx)
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
        MaterialAlertDialogBuilder(host.ctx)
            .setTitle(R.string.script_record_confirm_title)
            .setMessage(R.string.script_record_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_record_start_now) { _, _ -> beginRecording() }
            .show()
    }

    private fun beginRecording() {
        ActionRecorder.start(host.ctx)
        RecorderOverlayService.start(host.ctx, RecorderOverlayService.MODE_RECORD)
        host.toast(R.string.toast_record_started)
        // 退到后台，让用户切到目标 APP 操作
        handler.postDelayed({ host.moveTaskToBack() }, 300L)
    }

    // ========== 回放 ==========

    fun onPlayClicked(entry: ScriptStore.Entry) {
        if (!ensureReady()) return
        if (ScriptPlayer.isPlaying) {
            host.toast(R.string.toast_script_already_playing)
            return
        }
        val script = ScriptStore.load(host.ctx, entry.fileName)
        if (script == null || script.actions.isEmpty()) {
            host.toast(R.string.toast_script_load_failed)
            return
        }

        val view = LayoutInflater.from(host.ctx).inflate(R.layout.dialog_play_script, null)
        val sliderLoops = view.findViewById<Slider>(R.id.sliderPlayLoops)
        val sliderSpeed = view.findViewById<Slider>(R.id.sliderPlaySpeed)
        val tvLoops = view.findViewById<TextView>(R.id.tvPlayLoops)
        val tvSpeed = view.findViewById<TextView>(R.id.tvPlaySpeed)

        tvLoops.text = host.ctx.getString(R.string.script_loops_value, sliderLoops.value.toInt())
        tvSpeed.text = host.ctx.getString(R.string.script_speed_value, sliderSpeed.value)
        sliderLoops.addOnChangeListener { _, v, _ ->
            tvLoops.text = host.ctx.getString(R.string.script_loops_value, v.toInt())
        }
        sliderSpeed.addOnChangeListener { _, v, _ ->
            tvSpeed.text = host.ctx.getString(R.string.script_speed_value, v)
        }

        MaterialAlertDialogBuilder(host.ctx)
            .setTitle(script.name)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_run) { _, _ ->
                beginPlay(entry, sliderLoops.value.toInt(), sliderSpeed.value)
            }
            .show()
    }

    private fun beginPlay(entry: ScriptStore.Entry, loops: Int, speed: Float) {
        val ctx = host.ctx
        val script = ScriptStore.load(ctx, entry.fileName) ?: return
        val launched = launchTargetApp(script.pkg)
        if (!launched) host.moveTaskToBack()
        host.toast(R.string.toast_script_play_starting)

        // 等目标应用起来再开始回放
        handler.postDelayed({
            val service = AutoScrollAccessibilityService.instance
            if (service == null) {
                host.toast(R.string.toast_accessibility_disconnected)
                return@postDelayed
            }
            if (ScriptPlayer.play(service, script, loops, speed)) {
                RecorderOverlayService.start(ctx, RecorderOverlayService.MODE_PLAY)
            }
        }, if (launched) 1800L else 700L)
    }

    private fun launchTargetApp(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == host.ctx.packageName) return false
        return try {
            val intent = host.ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== 更多操作 ==========

    fun onMoreClicked(entry: ScriptStore.Entry, anchor: View) {
        val menu = PopupMenu(host.ctx, anchor)
        menu.menu.add(0, MENU_DETAIL, 0, R.string.script_menu_detail)
        menu.menu.add(0, MENU_EDIT, 1, R.string.script_menu_edit)
        menu.menu.add(0, MENU_RENAME, 2, R.string.script_menu_rename)
        menu.menu.add(0, MENU_EXPORT, 3, R.string.script_menu_export)
        menu.menu.add(0, MENU_DELETE, 4, R.string.script_menu_delete)
        menu.menu.add(0, MENU_IMPORT, 5, R.string.script_menu_import)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DETAIL -> showDetail(entry)
                MENU_EDIT -> host.openEditor(entry.fileName)
                MENU_RENAME -> showRename(entry)
                MENU_EXPORT -> exportScript(entry)
                MENU_DELETE -> confirmDelete(entry)
                MENU_IMPORT -> showImportPicker()
            }
            true
        }
        menu.show()
    }

    private fun showDetail(entry: ScriptStore.Entry) {
        val ctx = host.ctx
        val script = ScriptStore.load(ctx, entry.fileName) ?: return
        val body = script.actions
            .take(MAX_DETAIL_LINES)
            .mapIndexed { i, a -> a.readable(i) }
            .joinToString("\n")
        val more = if (script.actions.size > MAX_DETAIL_LINES) {
            ctx.getString(R.string.script_detail_more, script.actions.size - MAX_DETAIL_LINES)
        } else ""
        val header = ctx.getString(
            R.string.script_detail_header,
            script.pkg.ifBlank { "-" },
            script.actions.size,
            (script.estimatedMs / 1000f).toInt()
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(script.name)
            .setMessage(header + "\n\n" + body + more)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showRename(entry: ScriptStore.Entry) {
        val input = EditText(host.ctx).apply {
            setText(entry.name)
            setSelection(entry.name.length)
            setPadding(56, 32, 56, 8)
        }
        MaterialAlertDialogBuilder(host.ctx)
            .setTitle(R.string.script_menu_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    host.toast(R.string.toast_script_name_empty)
                    return@setPositiveButton
                }
                if (ScriptStore.rename(host.ctx, entry.fileName, newName)) {
                    host.refreshList()
                } else {
                    host.toast(R.string.toast_script_rename_failed)
                }
            }
            .show()
    }

    private fun exportScript(entry: ScriptStore.Entry) {
        val file = ScriptStore.export(host.ctx, entry.fileName)
        if (file == null) {
            host.toast(R.string.toast_script_export_failed)
        } else {
            MaterialAlertDialogBuilder(host.ctx)
                .setTitle(R.string.script_menu_export)
                .setMessage(host.ctx.getString(R.string.script_export_done, file.absolutePath))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun confirmDelete(entry: ScriptStore.Entry) {
        MaterialAlertDialogBuilder(host.ctx)
            .setTitle(R.string.script_menu_delete)
            .setMessage(host.ctx.getString(R.string.script_delete_confirm, entry.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_menu_delete) { _, _ ->
                if (ScriptStore.delete(host.ctx, entry.fileName)) host.refreshList()
                else host.toast(R.string.toast_script_delete_failed)
            }
            .show()
    }

    /**
     * 展示外部专属目录 scripts/ 下的可导入文件列表。
     * 选中后通过 [ScriptStore.importFromExternal] 复制到私有目录并刷新。
     */
    private fun showImportPicker() {
        val ctx = host.ctx
        val files = ScriptStore.listExternalImportable(ctx)
        if (files.isEmpty()) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.script_menu_import)
                .setMessage(ctx.getString(R.string.script_import_empty_hint, ctx.packageName))
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
            .setPositiveButton(R.string.script_import_action) { _, _ ->
                if (chosen < 0) return@setPositiveButton
                val src = files[chosen]
                val newName = ScriptStore.importFromExternal(ctx, src)
                if (newName != null) {
                    host.toast(ctx.getString(R.string.editor_import_done, newName))
                    host.refreshList()
                } else {
                    host.toast(R.string.editor_import_failed)
                }
            }
            .show()
    }

    // ========== 前置条件 ==========

    /** 录制 / 回放都依赖无障碍服务 + 悬浮窗权限 */
    private fun ensureReady(): Boolean {
        if (AutoScrollAccessibilityService.instance == null) {
            host.toast(R.string.toast_accessibility_disconnected)
            return false
        }
        if (!Settings.canDrawOverlays(host.ctx)) {
            host.toast(R.string.toast_overlay_permission_missing)
            try {
                host.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${host.ctx.packageName}")
                    )
                )
            } catch (_: Exception) {
            }
            return false
        }
        return true
    }

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

package cn.ggdoc.autoscroll.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ActionRecorder
import cn.ggdoc.autoscroll.recorder.ScriptPlayer
import cn.ggdoc.autoscroll.recorder.ScriptStore
import cn.ggdoc.autoscroll.ui.ScriptEditorDialogFragment
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.service.RecorderOverlayService
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

/**
 * 脚本录制 / 管理页：录制新脚本、回放、重命名、导出、删除
 */
class ScriptActivity : AppCompatActivity() {

    private lateinit var rvScripts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnStartRecord: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var adapter: ScriptAdapter

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script)

        rvScripts = findViewById(R.id.rvScripts)
        tvEmpty = findViewById(R.id.tvScriptEmpty)
        btnStartRecord = findViewById(R.id.btnStartRecord)
        btnBack = findViewById(R.id.btnScriptBack)

        adapter = ScriptAdapter(emptyList(), ::onPlayClicked, ::onMoreClicked)
        rvScripts.layoutManager = LinearLayoutManager(this)
        rvScripts.adapter = adapter

        btnStartRecord.setOnClickListener { onRecordClicked() }
        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val list = ScriptStore.list(this)
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.script_record_confirm_title)
            .setMessage(R.string.script_record_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_record_start_now) { _, _ -> beginRecording() }
            .show()
    }

    private fun beginRecording() {
        ActionRecorder.start(this)
        RecorderOverlayService.start(this, RecorderOverlayService.MODE_RECORD)
        toast(R.string.toast_record_started)
        // 退到后台，让用户切到目标 APP 操作
        handler.postDelayed({ moveTaskToBack(true) }, 300L)
    }

    // ========== 回放 ==========

    private fun onPlayClicked(entry: ScriptStore.Entry) {
        if (!ensureReady()) return
        if (ScriptPlayer.isPlaying) {
            toast(R.string.toast_script_already_playing)
            return
        }
        val script = ScriptStore.load(this, entry.fileName)
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

        MaterialAlertDialogBuilder(this)
            .setTitle(script.name)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_run) { _, _ ->
                beginPlay(entry, sliderLoops.value.toInt(), sliderSpeed.value)
            }
            .show()
    }

    private fun beginPlay(entry: ScriptStore.Entry, loops: Int, speed: Float) {
        val script = ScriptStore.load(this, entry.fileName) ?: return
        val launched = launchTargetApp(script.pkg)
        if (!launched) moveTaskToBack(true)
        toast(R.string.toast_script_play_starting)

        // 等目标应用起来再开始回放
        handler.postDelayed({
            val service = AutoScrollAccessibilityService.instance
            if (service == null) {
                toast(R.string.toast_accessibility_disconnected)
                return@postDelayed
            }
            if (ScriptPlayer.play(service, script, loops, speed)) {
                RecorderOverlayService.start(this, RecorderOverlayService.MODE_PLAY)
            }
        }, if (launched) 1800L else 700L)
    }

    private fun launchTargetApp(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == packageName) return false
        return try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== 更多操作 ==========

    private fun onMoreClicked(entry: ScriptStore.Entry, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, MENU_DETAIL, 0, R.string.script_menu_detail)
        menu.menu.add(0, MENU_EDIT, 1, R.string.script_menu_edit)
        menu.menu.add(0, MENU_RENAME, 2, R.string.script_menu_rename)
        menu.menu.add(0, MENU_EXPORT, 3, R.string.script_menu_export)
        menu.menu.add(0, MENU_DELETE, 4, R.string.script_menu_delete)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DETAIL -> showDetail(entry)
                MENU_EDIT -> openEditor(entry)
                MENU_RENAME -> showRename(entry)
                MENU_EXPORT -> exportScript(entry)
                MENU_DELETE -> confirmDelete(entry)
            }
            true
        }
        menu.show()
    }

    private fun openEditor(entry: ScriptStore.Entry) {
        if (isFinishing || isDestroyed) return
        ScriptEditorDialogFragment.newInstance(entry.fileName) { refreshList() }
            .show(supportFragmentManager, "ScriptEditorDialog")
    }

    private fun showDetail(entry: ScriptStore.Entry) {
        val script = ScriptStore.load(this, entry.fileName) ?: return
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
        MaterialAlertDialogBuilder(this)
            .setTitle(script.name)
            .setMessage(header + "\n\n" + body + more)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showRename(entry: ScriptStore.Entry) {
        val input = EditText(this).apply {
            setText(entry.name)
            setSelection(entry.name.length)
            setPadding(56, 32, 56, 8)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.script_menu_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    toast(R.string.toast_script_name_empty)
                    return@setPositiveButton
                }
                if (ScriptStore.rename(this, entry.fileName, newName)) {
                    refreshList()
                } else {
                    toast(R.string.toast_script_rename_failed)
                }
            }
            .show()
    }

    private fun exportScript(entry: ScriptStore.Entry) {
        val file = ScriptStore.export(this, entry.fileName)
        if (file == null) {
            toast(R.string.toast_script_export_failed)
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.script_menu_export)
                .setMessage(getString(R.string.script_export_done, file.absolutePath))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun confirmDelete(entry: ScriptStore.Entry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.script_menu_delete)
            .setMessage(getString(R.string.script_delete_confirm, entry.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.script_menu_delete) { _, _ ->
                if (ScriptStore.delete(this, entry.fileName)) refreshList()
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
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_overlay_permission_missing)
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
            return false
        }
        return true
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val MENU_DETAIL = 1
        private const val MENU_EDIT = 2
        private const val MENU_RENAME = 3
        private const val MENU_EXPORT = 4
        private const val MENU_DELETE = 5
        private const val MAX_DETAIL_LINES = 80
    }
}

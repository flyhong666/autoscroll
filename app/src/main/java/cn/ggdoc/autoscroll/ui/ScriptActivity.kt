package cn.ggdoc.autoscroll.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ScriptStore
import com.google.android.material.button.MaterialButton

/**
 * 脚本录制 / 管理页：录制新脚本、回放、重命名、导出、删除、导入。
 * 公共操作（录制 / 回放 / 详情 / 重命名 / 导出 / 删除 / 导入）由 [ScriptActions] 统一实现。
 */
class ScriptActivity : AppCompatActivity(), ScriptEditorDialogFragment.OnScriptEditedListener, ScriptActions.ScriptHost {

    private lateinit var rvScripts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnStartRecord: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var adapter: ScriptAdapter

    /** 公共脚本操作（延迟初始化：首次调用时才访问 context） */
    private val scriptActions by lazy { ScriptActions(this) }

    override val ctx: Context
        get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script)
        supportFragmentManager.setFragmentResultListener(
            ScriptEditorDialogFragment.RESULT_KEY,
            this
        ) { _, _ ->
            refreshList()
        }

        rvScripts = findViewById(R.id.rvScripts)
        tvEmpty = findViewById(R.id.tvScriptEmpty)
        btnStartRecord = findViewById(R.id.btnStartRecord)
        btnBack = findViewById(R.id.btnScriptBack)

        adapter = ScriptAdapter(emptyList(), scriptActions::onPlayClicked, scriptActions::onMoreClicked)
        rvScripts.layoutManager = LinearLayoutManager(this)
        rvScripts.adapter = adapter

        btnStartRecord.setOnClickListener { scriptActions.onRecordClicked() }
        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun refreshList() {
        // ScriptStore 主线程 IO 摘除：全量读+解析脚本 JSON 放后台线程
        ScriptStore.listAsync(this) { list ->
            if (!isFinishing && !isDestroyed) {
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ========== ScriptHost 实现 ==========

    override fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    override fun toast(msg: CharSequence) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun moveTaskToBack() {
        @Suppress("DEPRECATION")
        moveTaskToBack(true)
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
    }

    override fun openEditor(fileName: String) {
        if (isFinishing || isDestroyed) return
        ScriptEditorDialogFragment.newInstance(fileName)
            .show(supportFragmentManager, "ScriptEditorDialog")
    }

    /** 编辑器保存后刷新列表（targetFragment 回调） */
    override fun onScriptEdited() {
        refreshList()
    }
}

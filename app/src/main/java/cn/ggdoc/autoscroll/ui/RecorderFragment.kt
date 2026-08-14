package cn.ggdoc.autoscroll.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ScriptStore
import com.google.android.material.button.MaterialButton

/**
 * 操作记录器（Tab 页）：录制、管理、回放用户操作脚本。
 * 作为主界面底部导航的独立 Tab 展示，是脚本管理的唯一入口。
 *
 * 公共操作（录制 / 回放 / 详情 / 重命名 / 导出 / 删除 / 导入）由 [ScriptActions] 统一实现。
 */
class RecorderFragment : Fragment(), ScriptEditorDialogFragment.OnScriptEditedListener, ScriptActions.ScriptHost {

    private lateinit var rvScripts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnStartRecord: MaterialButton
    private lateinit var adapter: ScriptAdapter

    /** 公共脚本操作（延迟初始化：首次调用时才访问 context） */
    private val scriptActions by lazy { ScriptActions(this) }

    override val ctx: Context
        get() = requireContext()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_recorder, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
            ScriptEditorDialogFragment.RESULT_KEY,
            this
        ) { _, _ ->
            if (isAdded) refreshList()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvScripts = view.findViewById(R.id.rvScripts)
        tvEmpty = view.findViewById(R.id.tvScriptEmpty)
        btnStartRecord = view.findViewById(R.id.btnStartRecord)

        adapter = ScriptAdapter(emptyList(), scriptActions::onPlayClicked, scriptActions::onMoreClicked)
        rvScripts.layoutManager = LinearLayoutManager(requireContext())
        rvScripts.adapter = adapter

        btnStartRecord.setOnClickListener { scriptActions.onRecordClicked() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun refreshList() {
        if (!::adapter.isInitialized) return
        // ScriptStore 主线程 IO 摘除：全量读+解析脚本 JSON 放后台线程
        val ctx = requireContext().applicationContext
        val act = activity
        ScriptStore.listAsync(ctx) { list ->
            if (act != null && isAdded) {
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ========== ScriptHost 实现 ==========

    override fun toast(resId: Int) = Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()

    override fun toast(msg: CharSequence) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun moveTaskToBack() {
        runCatching { requireActivity().moveTaskToBack(true) }
    }

    override fun startActivity(intent: Intent) {
        runCatching { this@RecorderFragment.startActivity(intent) }
    }

    override fun openEditor(fileName: String) {
        if (!isAdded) return
        val fm = parentFragmentManager
        if (fm.isStateSaved) return
        ScriptEditorDialogFragment.newInstance(fileName)
            .show(fm, "ScriptEditorDialog")
    }

    /** 编辑器保存后刷新列表（targetFragment 回调，跨重建存活） */
    override fun onScriptEdited() {
        if (isAdded) refreshList()
    }
}

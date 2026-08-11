package cn.ggdoc.autoscroll.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import com.google.android.material.button.MaterialButton

/**
 * 从已安装应用里多选包名（用于「多 APP 轮换」的应用池 / 黑白名单）。
 *
 * M6 修复：
 *  - 初始选择集放 arguments，旋转重建不丢失；
 *  - 应用列表在后台线程加载（PackageManager 全量查询耗时），避免主线程卡顿/ANR；
 *  - 确认结果通过 targetFragment 回调（旋转重建后依然可达），
 *    普通字段 onConfirm 在重建后会丢失。
 */
class AppPickerDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_INITIAL_SELECTION = "initial_selection"
        private const val ARG_REQUEST_CODE = "request_code"

        fun newInstance(initialSelection: Set<String>, requestCode: Int = 0): AppPickerDialogFragment {
            return AppPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_SELECTION, initialSelection.joinToString(","))
                    putInt(ARG_REQUEST_CODE, requestCode)
                }
            }
        }
    }

    /** 确认结果回调接口：由目标 Fragment 实现（经 targetFragment 跨重建存活） */
    interface AppPickerResultListener {
        fun onAppsConfirmed(requestCode: Int, selected: Set<String>)
    }

    /** 兼容旧用法：非 targetFragment 方式创建时仍可设置回调（不跨重建） */
    var onConfirm: ((Set<String>) -> Unit)? = null

    private lateinit var rvApps: RecyclerView
    private lateinit var etSearch: android.widget.EditText
    private lateinit var tvCount: TextView
    private lateinit var btnDone: MaterialButton
    private lateinit var adapter: AppListAdapter

    private data class AppInfo(val packageName: String, val label: String)

    private val allApps = mutableListOf<AppInfo>()
    private val selected = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_app_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // M6 修复：初始选择集从 arguments 读取，旋转重建不丢失
        selected.addAll(parseInitialSelection())

        rvApps = view.findViewById(R.id.rvApps)
        rvApps.layoutManager = LinearLayoutManager(requireContext())

        etSearch = view.findViewById(R.id.etAppSearch)
        tvCount = view.findViewById(R.id.tvAppCount)
        btnDone = view.findViewById<MaterialButton>(R.id.btnAppPickerDone)

        adapter = AppListAdapter(emptyList(), selected) { pkg, isChecked ->
            if (isChecked) selected.add(pkg) else selected.remove(pkg)
            updateCount()
        }
        rvApps.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<MaterialButton>(R.id.btnAppPickerSelectAll).setOnClickListener {
            selected.addAll(allApps.map { it.packageName })
            adapter.notifySelectionChanged()
            updateCount()
        }
        view.findViewById<MaterialButton>(R.id.btnAppPickerClear).setOnClickListener {
            selected.clear()
            adapter.notifySelectionChanged()
            updateCount()
        }
        btnDone.setOnClickListener {
            if (!::adapter.isInitialized || allApps.isEmpty()) return@setOnClickListener
            confirmAndDismiss()
        }

        updateCount()
        // M6 修复：应用列表在后台线程加载，避免主线程数百次 PackageManager 查询卡顿
        loadAppsInBackground()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun confirmAndDismiss() {
        val result = selected.toSet()
        val target = targetFragment
        when {
            onConfirm != null -> onConfirm?.invoke(result)
            target is AppPickerResultListener ->
                target.onAppsConfirmed(arguments?.getInt(ARG_REQUEST_CODE) ?: 0, result)
            parentFragment is AppPickerResultListener ->
                (parentFragment as AppPickerResultListener)
                    .onAppsConfirmed(arguments?.getInt(ARG_REQUEST_CODE) ?: 0, result)
            activity is AppPickerResultListener ->
                (activity as AppPickerResultListener)
                    .onAppsConfirmed(arguments?.getInt(ARG_REQUEST_CODE) ?: 0, result)
        }
        dismiss()
    }

    private fun parseInitialSelection(): Set<String> {
        val raw = arguments?.getString(ARG_INITIAL_SELECTION).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun updateCount() {
        if (!::tvCount.isInitialized) return
        tvCount.text = getString(R.string.rotation_apps_count, selected.size)
    }

    private fun loadAppsInBackground() {
        // 加载完成前禁用确定按钮，避免空列表确认
        btnDone.isEnabled = false
        tvCount.text = getString(R.string.rotation_apps_loading)
        val appCtx = requireContext().applicationContext
        val act = activity ?: return
        Thread {
            val apps = loadInstalledApps(appCtx)
            act.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                allApps.clear()
                allApps.addAll(apps)
                allApps.sortBy { it.label.lowercase() }
                adapter.submitAll(allApps)
                btnDone.isEnabled = true
                updateCount()
            }
        }.start()
    }

    private fun loadInstalledApps(context: android.content.Context): List<AppInfo> {
        val pm = context.packageManager
        val list = mutableListOf<AppInfo>()
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_ALL
        return try {
            for (app in pm.getInstalledApplications(flags)) {
                // 仅保留有启动入口的应用（可被轮换启动的）
                if (pm.getLaunchIntentForPackage(app.packageName) == null) continue
                val label = app.loadLabel(pm).toString()
                list.add(AppInfo(app.packageName, label.ifBlank { app.packageName }))
            }
            list
        } catch (e: Exception) {
            list
        }
    }

    private class AppListAdapter(
        private val all: MutableList<AppInfo>,
        private val selected: MutableSet<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.VH>() {

        private var filtered: List<AppInfo> = all

        fun submitAll(apps: List<AppInfo>) {
            all.clear()
            all.addAll(apps)
            filtered = all
            notifyDataSetChanged()
        }

        fun filter(keyword: String) {
            val k = keyword.trim().lowercase()
            filtered = if (k.isEmpty()) all else all.filter {
                it.label.lowercase().contains(k) || it.packageName.lowercase().contains(k)
            }
            notifyDataSetChanged()
        }

        fun notifySelectionChanged() = notifyDataSetChanged()

        override fun getItemCount() = filtered.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_picker, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = filtered[position]
            val isSel = selected.contains(app.packageName)
            holder.label.text = app.label
            holder.pkg.text = app.packageName
            try {
                holder.icon.setImageDrawable(
                    holder.itemView.context.packageManager.getApplicationIcon(app.packageName)
                )
            } catch (e: Exception) {
                holder.icon.setImageDrawable(null)
            }
            holder.checkbox.isChecked = isSel
            holder.itemView.setOnClickListener {
                val newState = !selected.contains(app.packageName)
                if (newState) selected.add(app.packageName) else selected.remove(app.packageName)
                holder.checkbox.isChecked = newState
                onToggle(app.packageName, newState)
            }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.ivAppIcon)
            val label: TextView = v.findViewById(R.id.tvAppLabel)
            val pkg: TextView = v.findViewById(R.id.tvAppPkg)
            val checkbox: CheckBox = v.findViewById(R.id.cbApp)
        }
    }
}

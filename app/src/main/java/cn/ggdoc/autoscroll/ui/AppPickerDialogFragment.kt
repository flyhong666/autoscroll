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
 * 从已安装应用里多选包名（用于「多 APP 轮换」的应用池）。
 * initialSelection：已选包名集合；onConfirm：确认回调（返回最终选择集合）。
 */
class AppPickerDialogFragment : DialogFragment() {

    var initialSelection: Set<String> = emptySet()
    var onConfirm: ((Set<String>) -> Unit)? = null

    private lateinit var rvApps: RecyclerView
    private lateinit var etSearch: android.widget.EditText
    private lateinit var tvCount: TextView
    private lateinit var adapter: AppListAdapter

    private data class AppInfo(val packageName: String, val label: String)

    private val allApps = mutableListOf<AppInfo>()
    private val selected = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_app_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected.addAll(initialSelection)

        rvApps = view.findViewById(R.id.rvApps)
        rvApps.layoutManager = LinearLayoutManager(requireContext())

        etSearch = view.findViewById(R.id.etAppSearch)
        tvCount = view.findViewById(R.id.tvAppCount)

        allApps.addAll(loadInstalledApps())
        allApps.sortBy { it.label.lowercase() }

        adapter = AppListAdapter(allApps, selected) { pkg, isChecked ->
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
        view.findViewById<MaterialButton>(R.id.btnAppPickerDone).setOnClickListener {
            onConfirm?.invoke(selected.toSet())
            dismiss()
        }

        updateCount()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun updateCount() {
        tvCount.text = getString(R.string.rotation_apps_count, selected.size)
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = requireContext().packageManager
        val list = mutableListOf<AppInfo>()
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_ALL
        for (app in pm.getInstalledApplications(flags)) {
            // 仅保留有启动入口的应用（可被轮换启动的）
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue
            val label = app.loadLabel(pm).toString()
            list.add(AppInfo(app.packageName, label.ifBlank { app.packageName }))
        }
        return list
    }

    private class AppListAdapter(
        private val all: List<AppInfo>,
        private val selected: MutableSet<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.VH>() {

        private var filtered: List<AppInfo> = all

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

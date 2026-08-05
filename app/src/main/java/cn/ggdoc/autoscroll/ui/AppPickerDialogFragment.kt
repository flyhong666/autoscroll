package cn.ggdoc.autoscroll.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import com.google.android.material.button.MaterialButton

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/**
 * 已安装应用多选弹窗：列出本机可启动的应用，用户勾选要自动刷的 App。
 * 通过 [onConfirm] 回调返回选中的包名集合。
 */
class AppPickerDialogFragment : DialogFragment() {

    var initialSelection: Set<String> = emptySet()
    var onConfirm: ((Set<String>) -> Unit)? = null

    private lateinit var rvApps: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvPickerCount: TextView
    private lateinit var tvPickerEmpty: TextView

    private val allApps = mutableListOf<AppInfo>()
    private val selected = mutableSetOf<String>()
    private val filtered = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_app_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvApps = view.findViewById(R.id.rvApps)
        etSearch = view.findViewById(R.id.etSearch)
        tvPickerCount = view.findViewById(R.id.tvPickerCount)
        tvPickerEmpty = view.findViewById(R.id.tvPickerEmpty)

        selected.addAll(initialSelection)

        loadApps()
        filtered.addAll(allApps)

        adapter = AppAdapter(filtered, selected) { pkg ->
            if (selected.contains(pkg)) selected.remove(pkg) else selected.add(pkg)
            updateCount()
            adapter.notifySelectionChanged()
        }
        rvApps.layoutManager = LinearLayoutManager(requireContext())
        rvApps.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnPickerDone).setOnClickListener {
            onConfirm?.invoke(LinkedHashSet(selected))
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btnSelectAll).setOnClickListener {
            selected.clear()
            selected.addAll(filtered.map { it.packageName })
            updateCount()
            adapter.notifySelectionChanged()
        }
        view.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            selected.clear()
            updateCount()
            adapter.notifySelectionChanged()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                applyFilter(s?.toString().orEmpty())

            override fun afterTextChanged(s: Editable?) {}
        })

        updateCount()
        applyFilter("")
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadApps() {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { AppInfo(pm.getApplicationLabel(it).toString(), it.packageName, it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
        allApps.addAll(apps)
    }

    private fun applyFilter(query: String) {
        val q = query.lowercase().trim()
        filtered.clear()
        filtered.addAll(
            if (q.isEmpty()) allApps else allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        )
        adapter.notifyDataSetChanged()
        val empty = filtered.isEmpty()
        tvPickerEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvApps.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun updateCount() {
        tvPickerCount.text = if (selected.isEmpty()) {
            getString(R.string.allowed_apps_empty)
        } else {
            getString(R.string.allowed_apps_count, selected.size)
        }
    }

    private class AppAdapter(
        private val list: MutableList<AppInfo>,
        private val selectedSet: MutableSet<String>,
        private val onToggle: (String) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        fun notifySelectionChanged() = notifyDataSetChanged()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_picker, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = list[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            holder.pkg.text = app.packageName
            holder.check.isChecked = selectedSet.contains(app.packageName)
            holder.itemView.setOnClickListener { onToggle(app.packageName) }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.ivAppIcon)
            val label: TextView = v.findViewById(R.id.tvAppLabel)
            val pkg: TextView = v.findViewById(R.id.tvAppPackage)
            val check: CheckBox = v.findViewById(R.id.cbApp)
        }
    }
}

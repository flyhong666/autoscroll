package cn.ggdoc.autoscroll.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import com.google.android.material.button.MaterialButton

/**
 * 应用中心：统一管理「轮换应用池」与「过滤名单」两个按应用的角色，
 * 并在此一处设置全局过滤模式（off / 白名单 / 黑名单）。
 * 与设置面板内的快捷入口共享同一份 AppConfig 数据。
 */
class AppCenterActivity : AppCompatActivity() {

    private data class AppInfo(val packageName: String, val label: String)

    private lateinit var rvApps: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnDone: MaterialButton
    private lateinit var spinnerFilterMode: Spinner

    private val allApps = mutableListOf<AppInfo>()
    private val rotation = mutableSetOf<String>()
    private val filter = mutableSetOf<String>()
    private var filterMode = AppConfig.FILTER_OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_center)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        rvApps = findViewById(R.id.rvApps)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnDone = findViewById(R.id.btnDone)
        spinnerFilterMode = findViewById(R.id.spinnerFilterMode)

        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = AppAdapter(allApps, rotation, filter) { refreshCount() }

        rotation.addAll(AppConfig.getRotationApps(this))
        filter.addAll(AppConfig.getAppFilterList(this))
        filterMode = AppConfig.getAppFilterMode(this)

        setupFilterModeSpinner()
        btnDone.setOnClickListener { save() }

        loadAppsInBackground()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupFilterModeSpinner() {
        val modes = listOf(
            AppConfig.FILTER_OFF to R.string.filter_mode_off,
            AppConfig.FILTER_WHITELIST to R.string.filter_mode_whitelist,
            AppConfig.FILTER_BLACKLIST to R.string.filter_mode_blacklist
        )
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, modes.map { getString(it.second) }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerFilterMode.adapter = adapter
        spinnerFilterMode.setSelection(modes.indexOfFirst { it.first == filterMode }.coerceAtLeast(0))
        spinnerFilterMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long) {
                    filterMode = modes[pos].first
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }
    }

    private fun refreshCount() {
        // 预留：展示已选数（可选实现）
    }

    private fun save() {
        AppConfig.setRotationApps(this, rotation)
        AppConfig.setAppFilterList(this, filter)
        AppConfig.setAppFilterMode(this, filterMode)
        AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
        Toast.makeText(this, R.string.toast_app_center_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun loadAppsInBackground() {
        btnDone.isEnabled = false
        val appCtx = applicationContext
        Thread {
            val apps = loadInstalledApps(appCtx)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allApps.clear()
                allApps.addAll(apps)
                allApps.sortBy { it.label.lowercase() }
                (rvApps.adapter as? AppAdapter)?.notifyDataSetChanged()
                tvEmpty.visibility = if (allApps.isEmpty()) View.VISIBLE else View.GONE
                btnDone.isEnabled = true
            }
        }.start()
    }

    private fun loadInstalledApps(context: android.content.Context): List<AppInfo> {
        val pm = context.packageManager
        val list = mutableListOf<AppInfo>()
        return try {
            for (app in pm.getInstalledApplications(
                PackageManager.GET_META_DATA or PackageManager.MATCH_ALL
            )) {
                if (pm.getLaunchIntentForPackage(app.packageName) == null) continue
                val label = app.loadLabel(pm).toString()
                list.add(AppInfo(app.packageName, label.ifBlank { app.packageName }))
            }
            list
        } catch (e: Exception) {
            list
        }
    }

    private class AppAdapter(
        private val all: MutableList<AppInfo>,
        private val rotation: MutableSet<String>,
        private val filter: MutableSet<String>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        override fun getItemCount() = all.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_center, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = all[position]
            holder.label.text = app.label
            holder.pkg.text = app.packageName
            try {
                holder.icon.setImageDrawable(
                    holder.itemView.context.packageManager.getApplicationIcon(app.packageName)
                )
            } catch (e: Exception) {
                holder.icon.setImageDrawable(null)
            }
            holder.cbRotation.isChecked = rotation.contains(app.packageName)
            holder.cbFilter.isChecked = filter.contains(app.packageName)
            holder.cbRotation.setOnCheckedChangeListener { _, c ->
                if (c) rotation.add(app.packageName) else rotation.remove(app.packageName)
                onChanged()
            }
            holder.cbFilter.setOnCheckedChangeListener { _, c ->
                if (c) filter.add(app.packageName) else filter.remove(app.packageName)
                onChanged()
            }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.ivAppIcon)
            val label: TextView = v.findViewById(R.id.tvAppLabel)
            val pkg: TextView = v.findViewById(R.id.tvAppPkg)
            val cbRotation: CheckBox = v.findViewById(R.id.cbRotation)
            val cbFilter: CheckBox = v.findViewById(R.id.cbFilter)
        }
    }
}

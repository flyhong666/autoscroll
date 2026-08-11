package cn.ggdoc.autoscroll.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.util.AppLog
import cn.ggdoc.autoscroll.util.NodePoolStats
import cn.ggdoc.autoscroll.util.StatsCsvExporter
import com.google.android.material.button.MaterialButton

/**
 * 运行日志页（清单 #12）。
 *
 * 展示 [AppLog] 的环形缓冲内容，并提供：
 *  - 刷新 / 清除
 *  - 导出日志（txt）
 *  - 导出统计 CSV（清单 #13，由 [StatsCsvExporter] 落盘）
 *
 * 进入本页即代表用户想看现场，因此这里只做展示与导出，不修改业务状态。
 */
class LogActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var btnExportLog: MaterialButton
    private lateinit var btnExportStats: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNodePoolStats: MaterialButton
    private lateinit var tvNodePoolStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tvLog)
        btnRefresh = findViewById(R.id.btnLogRefresh)
        btnClear = findViewById(R.id.btnLogClear)
        btnExportLog = findViewById(R.id.btnLogExport)
        btnExportStats = findViewById(R.id.btnLogExportStats)
        btnBack = findViewById(R.id.btnLogBack)
        btnNodePoolStats = findViewById(R.id.btnNodePoolStats)
        tvNodePoolStats = findViewById(R.id.tvNodePoolStats)

        tvLog.movementMethod = ScrollingMovementMethod()

        btnRefresh.setOnClickListener { loadLog() }
        btnNodePoolStats.setOnClickListener { toggleNodePoolStats() }
        btnClear.setOnClickListener {
            AppLog.clear()
            loadLog()
            Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show()
        }
        btnExportLog.setOnClickListener {
            val f = AppLog.exportToFile(this)
            Toast.makeText(
                this,
                if (f != null) getString(R.string.log_exported, f.absolutePath)
                else getString(R.string.log_export_failed),
                Toast.LENGTH_LONG
            ).show()
        }
        btnExportStats.setOnClickListener {
            val f = StatsCsvExporter.export(this)
            Toast.makeText(
                this,
                if (f != null) getString(R.string.stats_exported, f.absolutePath)
                else getString(R.string.stats_export_failed),
                Toast.LENGTH_LONG
            ).show()
        }
        btnBack.setOnClickListener { finish() }

        loadLog()
    }

    private fun loadLog() {
        tvLog.text = AppLog.toText()
    }

    /** 节点池统计卡片：首次点击展开并刷新快照，再次点击收起 */
    private fun toggleNodePoolStats() {
        if (tvNodePoolStats.visibility == View.VISIBLE) {
            tvNodePoolStats.visibility = View.GONE
        } else {
            tvNodePoolStats.text = NodePoolStats.snapshot().format()
            tvNodePoolStats.visibility = View.VISIBLE
        }
    }
}

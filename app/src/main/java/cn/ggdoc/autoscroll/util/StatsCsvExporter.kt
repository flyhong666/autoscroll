package cn.ggdoc.autoscroll.util

import android.content.Context
import cn.ggdoc.autoscroll.config.StatsStore
import java.io.File

/**
 * 统计 CSV 导出（清单 #13）。把「今日 / 累计」两组统计写成 CSV，
 * 写到 app 外部私有目录，便于用户导出后查看或反馈。
 */
object StatsCsvExporter {

    fun export(context: Context): File? {
        val ctx = context.applicationContext
        val today = StatsStore.today(ctx)
        val total = StatsStore.total(ctx)
        val dir = File(ctx.getExternalFilesDir(null), "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "autoscroll_stats_${System.currentTimeMillis()}.csv")
        return try {
            val sb = StringBuilder()
            sb.appendLine("scope,scrolls,likes,adBlocks,adRewards,details,seconds")
            sb.appendLine(
                "today,${today.scrolls},${today.likes},${today.adBlocks}," +
                        "${today.adRewards},${today.details},${today.seconds}"
            )
            sb.appendLine(
                "total,${total.scrolls},${total.likes},${total.adBlocks}," +
                        "${total.adRewards},${total.details},${total.seconds}"
            )
            file.writeText(sb.toString())
            file
        } catch (e: Exception) {
            AppLog.e("StatsCsv", "导出统计 CSV 失败", e)
            null
        }
    }
}

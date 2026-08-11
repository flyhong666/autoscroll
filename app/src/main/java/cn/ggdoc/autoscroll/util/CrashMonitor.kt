package cn.ggdoc.autoscroll.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程级未捕获异常捕获（清单 #7 崩溃监控）。
 *
 * 无障碍服务常年在后台跑，崩溃极难复现和定位。这里接管
 * [Thread.setDefaultUncaughtExceptionHandler]，把崩溃堆栈写入 [AppLog]
 * （进环形缓冲，用户可在日志页看到）+ 独立 crash 文件（便于带文件反馈），
 * 再交给原 handler，不改变系统默认的崩溃行为（即仍然会崩溃 / 上报）。
 */
object CrashMonitor {

    private val installed = AtomicBoolean(false)
    private var appContext: Context? = null
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (installed.getAndSet(true)) return
        appContext = context.applicationContext
        AppLog.init(context.applicationContext)
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("Crash", "未捕获异常 @ ${thread.name}", throwable)
            writeCrashFile(throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(throwable: Throwable) {
        val ctx = appContext ?: return
        val dir = File(ctx.getExternalFilesDir(null), "crashes")
        if (!dir.exists()) dir.mkdirs()
        // 仅保留最近 20 个崩溃文件，避免无限增长
        runCatching {
            dir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.dropLast(20)
                ?.forEach { it.delete() }
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$ts.txt")
        runCatching {
            file.writeText(
                "${throwable.javaClass.name}: ${throwable.message}\n\n" +
                        Log.getStackTraceString(throwable)
            )
        }
    }
}

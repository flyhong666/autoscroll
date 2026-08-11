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
            // M3 修复：崩溃处理器内部绝不允许再抛异常（否则 previous handler 不会被执行，
            // 系统默认的崩溃行为被破坏）。这里把业务逻辑整体包起来。
            try {
                AppLog.e("Crash", "未捕获异常 @ ${thread.name}", throwable)
                writeCrashFile(throwable)
            } catch (ignored: Throwable) {
                // 吞掉，保证 previous handler 一定能被调用
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(throwable: Throwable) {
        val ctx = appContext ?: return
        // M3 修复：getExternalFilesDir 可能返回 null（存储不可用），回退到 filesDir
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "crashes")
        if (!dir.exists()) dir.mkdirs()
        // 仅保留最近 20 个崩溃文件，避免无限增长
        runCatching {
            dir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.dropLast(20)
                ?.forEach { it.delete() }
        }
        // M3 修复：文件名加毫秒，避免同一秒内多次崩溃互相覆盖
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "crash_$ts.txt")
        runCatching {
            file.writeText(
                "${throwable.javaClass.name}: ${throwable.message}\n\n" +
                        Log.getStackTraceString(throwable)
            )
        }
    }
}

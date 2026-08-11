package cn.ggdoc.autoscroll.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 全局环形缓冲日志。
 *
 * 解决的问题：原代码散落大量 Log.d/w/e，但无障碍服务常年在后台跑、用户不在现场，
 * 出问题时既看不到也取不到。这里把所有日志收口到一个内存环形缓冲 + 可选落盘，
 * 配套 [cn.ggdoc.autoscroll.ui.LogActivity] 让用户能随时查看 / 导出。
 *
 * 线程安全：写缓冲用 synchronized；落盘函数由调用方在合适的线程调用，不内部起线程。
 */
object AppLog {

    enum class Level { D, I, W, E }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val msg: String,
        val throwableText: String?
    ) {
        fun format(): String {
            val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(time))
            val lvl = level.name[0]
            val t = if (throwableText != null) "\n$throwableText" else ""
            return "$ts $lvl/$tag: $msg$t"
        }
    }

    private const val MAX_ENTRIES = 1000
    private const val THREAD_NAME = "Coroutine"
    private val buffer = ArrayDeque<Entry>()
    private val lock = Any()

    @Volatile
    private var logDir: File? = null

    /** 设置日志落盘目录（外部存储 app 私有目录，无需权限）。建议在 Application/主入口调用。 */
    fun init(context: Context) {
        logDir = File(context.applicationContext.getExternalFilesDir(null), "logs").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun log(level: Level, tag: String, msg: String, throwable: Throwable? = null) {
        val tText = throwable?.let { Log.getStackTraceString(it) }
        synchronized(lock) {
            buffer.addLast(Entry(System.currentTimeMillis(), level, tag, msg, tText))
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
        // 同步输出到系统 Logcat，保持原有可观测性
        when (level) {
            Level.D -> Log.d(tag, msg, throwable)
            Level.I -> Log.i(tag, msg, throwable)
            Level.W -> Log.w(tag, msg, throwable)
            Level.E -> Log.e(tag, msg, throwable)
        }
    }

    fun d(tag: String, msg: String, t: Throwable? = null) = log(Level.D, tag, msg, t)
    fun i(tag: String, msg: String, t: Throwable? = null) = log(Level.I, tag, msg, t)
    fun w(tag: String, msg: String, t: Throwable? = null) = log(Level.W, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(Level.E, tag, msg, t)

    fun getEntries(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun toText(): String = synchronized(lock) {
        if (buffer.isEmpty()) "(暂无日志)" else buffer.joinToString("\n") { it.format() }
    }

    fun clear() = synchronized(lock) { buffer.clear() }

    /** 全量日志导出为 txt 文件，返回文件（失败返回 null）。 */
    fun exportToFile(context: Context): File? {
        val dir = logDir
            ?: File(context.applicationContext.getExternalFilesDir(null), "logs").also { logDir = it }
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "autoscroll_log_${System.currentTimeMillis()}.txt")
        return try {
            file.writeText(toText())
            file
        } catch (e: Exception) {
            Log.e("AppLog", "导出日志失败", e)
            null
        }
    }

    /** 给协程异常处理器复用，避免在 5 个 Controller 里重复写同样的 lambda，并让异常进日志页。 */
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        this.e(THREAD_NAME, "协程未捕获异常", e)
    }
}

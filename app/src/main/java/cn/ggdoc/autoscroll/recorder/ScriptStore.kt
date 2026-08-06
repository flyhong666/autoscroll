package cn.ggdoc.autoscroll.recorder

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 脚本文件存储：App 私有目录 files/scripts/ 下的 .json 文件
 * 导出目录：Android/data/<pkg>/files/scripts/（文件管理器可直接访问，无需存储权限）
 */
object ScriptStore {

    private const val TAG = "ScriptStore"
    private const val DIR_NAME = "scripts"
    private const val EXT = ".json"

    /** 列表项摘要（避免列表页持有全部动作） */
    data class Entry(
        val fileName: String,
        val name: String,
        val createdAt: Long,
        val actionCount: Int,
        val pkg: String,
        val estimatedMs: Long
    )

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun list(context: Context): List<Entry> = try {
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(EXT) }
            ?.mapNotNull { f ->
                readScript(f)?.let {
                    Entry(f.name, it.name, it.createdAt, it.actions.size, it.pkg, it.estimatedMs)
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "列出脚本失败", e)
        emptyList()
    }

    fun load(context: Context, fileName: String): RecordedScript? =
        readScript(File(dir(context), fileName))

    private fun readScript(file: File): RecordedScript? = try {
        if (!file.exists()) null else RecordedScript.fromJson(JSONObject(file.readText(Charsets.UTF_8)))
    } catch (e: Exception) {
        Log.e(TAG, "读取脚本失败：${file.name}", e)
        null
    }

    /** 保存脚本，返回文件名；失败返回 null */
    fun save(context: Context, script: RecordedScript, fileName: String? = null): String? = try {
        val target = File(dir(context), fileName ?: buildFileName(script.createdAt))
        target.writeText(script.toPrettyString(), Charsets.UTF_8)
        target.name
    } catch (e: Exception) {
        Log.e(TAG, "保存脚本失败", e)
        null
    }

    fun delete(context: Context, fileName: String): Boolean = try {
        File(dir(context), fileName).delete()
    } catch (e: Exception) {
        Log.e(TAG, "删除脚本失败", e)
        false
    }

    fun rename(context: Context, fileName: String, newName: String): Boolean {
        val script = load(context, fileName) ?: return false
        return save(context, script.copy(name = newName), fileName) != null
    }

    /** 导出到外部专属目录，返回目标文件（失败 null） */
    fun export(context: Context, fileName: String): File? = try {
        val src = File(dir(context), fileName)
        val outDir = File(context.getExternalFilesDir(null), DIR_NAME)
            .apply { if (!exists()) mkdirs() }
        val out = File(outDir, fileName)
        src.copyTo(out, overwrite = true)
        out
    } catch (e: Exception) {
        Log.e(TAG, "导出脚本失败", e)
        null
    }

    fun defaultScriptName(): String =
        "录制_" + SimpleDateFormat("MMdd_HHmm", Locale.getDefault()).format(Date())

    fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    private fun buildFileName(createdAt: Long): String =
        "script_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(createdAt)) + EXT
}

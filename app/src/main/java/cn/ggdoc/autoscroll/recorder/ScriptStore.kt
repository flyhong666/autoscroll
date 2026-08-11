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

    /**
     * 文件名校验：仅允许无路径分隔符、无 `..` 的简单文件名。
     * 修复：import 外部文件直接使用 extFile.name，恶意命名的文件可把内容写到
     * scripts 目录之外（目录逃逸），load/delete/rename/export 同样必须校验。
     */
    private fun safeFileName(fileName: String): String? {
        val n = fileName.trim()
        if (n.isEmpty()) return null
        if (n.contains('/') || n.contains('\\') || n.contains("..")) return null
        return n
    }

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

    /** 后台加载脚本列表（IO 不阻塞主线程），完成后回主线程回调 */
    fun listAsync(context: Context, onResult: (List<Entry>) -> Unit) {
        val appCtx = context.applicationContext
        Thread {
            val list = list(appCtx)
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(list) }
        }.start()
    }

    fun load(context: Context, fileName: String): RecordedScript? {
        val safe = safeFileName(fileName) ?: return null
        return readScript(File(dir(context), safe))
    }

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

    fun delete(context: Context, fileName: String): Boolean {
        val safe = safeFileName(fileName) ?: return false
        return try {
            File(dir(context), safe).delete()
        } catch (e: Exception) {
            Log.e(TAG, "删除脚本失败", e)
            false
        }
    }

    fun rename(context: Context, fileName: String, newName: String): Boolean {
        val safe = safeFileName(fileName) ?: return false
        val script = load(context, safe) ?: return false
        val safeBase = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .takeIf { it.isNotEmpty() } ?: return false
        val newFileName = "$safeBase$EXT"
        // 文件名本就一致：仅更新内部 name 字段
        if (newFileName == safe) {
            return save(context, script.copy(name = newName), safe) != null
        }
        // 避免覆盖已存在的脚本
        if (File(dir(context), newFileName).exists()) return false
        // L3 修复：原实现只改内部 name 字段、用原 fileName 存盘，文件名不变，
        // 导致导出时仍是旧文件名、令人困惑。这里真正移动文件到新文件名。
        val old = File(dir(context), safe)
        if (save(context, script.copy(name = newName), newFileName) == null) return false
        old.delete()
        return true
    }

    /** 导出到外部专属目录，返回目标文件（失败 null） */
    fun export(context: Context, fileName: String): File? {
        val safe = safeFileName(fileName) ?: return null
        return try {
            val src = File(dir(context), safe)
            // 部分设备 / 受限存储下 getExternalFilesDir 可能返回 null，安全降级
            val extRoot = context.getExternalFilesDir(null) ?: return null
            val outDir = File(extRoot, DIR_NAME).apply { if (!exists()) mkdirs() }
            val out = File(outDir, safe)
            src.copyTo(out, overwrite = true)
            out
        } catch (e: Exception) {
            Log.e(TAG, "导出脚本失败", e)
            null
        }
    }

    /**
     * 从外部专属目录导入 .json 脚本文件到私有目录。
     *
     * 同名冲突时自动在文件名后追加 "_1" "_2" 避免覆盖。
     *
     * @return 导入成功返回新文件名，失败返回 null
     */
    fun importFromExternal(context: Context, extFile: File): String? = try {
        if (!extFile.exists() || !extFile.name.endsWith(EXT)) return null
        // 文件名校验：外部文件可能带恶意路径，只取简单文件名
        val safeBase = safeFileName(extFile.name)?.removeSuffix(EXT) ?: return null
        // 校验 JSON 合法性：能解析才算有效脚本
        val parsed = readScript(extFile) ?: return null
        // 去重：计算目标名，冲突则自增编号
        var targetName = "$safeBase$EXT"
        var i = 1
        val ctxDir = dir(context)
        while (File(ctxDir, targetName).exists()) {
            targetName = "${safeBase}_${i}${EXT}"
            i++
        }
        val target = File(ctxDir, targetName)
        extFile.copyTo(target, overwrite = false)
        // 若外部文件 name 字段为空，给个兜底
        if (parsed.name.isBlank()) {
            val fallback = safeBase.takeIf { it.isNotBlank() } ?: defaultScriptName()
            save(context, parsed.copy(name = fallback), targetName)
        }
        targetName
    } catch (e: Exception) {
        Log.e(TAG, "导入脚本失败：${extFile.name}", e)
        null
    }

    /**
     * 列出外部专属目录 scripts/ 下所有可导入的 .json 文件。
     * 用于给用户展示「选择导入」列表，不用走 SAF 选择器，避免存储权限申请。
     */
    fun listExternalImportable(context: Context): List<File> = try {
        val extRoot = context.getExternalFilesDir(null) ?: return emptyList()
        val extDir = File(extRoot, DIR_NAME)
        if (!extDir.exists()) emptyList()
        else {
            val ctxDir = dir(context)
            extDir.listFiles { f ->
                f.isFile && f.name.endsWith(EXT) && !File(ctxDir, f.name).exists()
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "列出外部脚本失败", e)
        emptyList()
    }

    fun defaultScriptName(): String =
        "录制_" + SimpleDateFormat("MMdd_HHmm", Locale.getDefault()).format(Date())

    fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    private fun buildFileName(createdAt: Long): String =
        "script_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(createdAt)) + EXT
}

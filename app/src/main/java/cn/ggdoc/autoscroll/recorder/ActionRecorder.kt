package cn.ggdoc.autoscroll.recorder

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * 操作记录器：把用户在其他 APP 里的点击 / 长按 / 滑动记录成可回放脚本。
 *
 * 事件来源为无障碍服务转发的 TYPE_VIEW_CLICKED / TYPE_VIEW_LONG_CLICKED / TYPE_VIEW_SCROLLED，
 * 通过控件在屏幕上的 bounds 反推坐标，回放时由 [ScriptPlayer] 用 dispatchGesture 还原。
 */
object ActionRecorder {

    private const val TAG = "ActionRecorder"

    const val BROADCAST_RECORDER_CHANGED = "cn.ggdoc.autoscroll.RECORDER_CHANGED"
    const val EXTRA_RECORDING = "recording"
    const val EXTRA_COUNT = "count"

    /** 单个脚本最多录制步数，防止误操作录出超长脚本 */
    const val MAX_ACTIONS = 300

    private const val FIRST_DELAY_MS = 600L
    private const val MIN_DELAY_MS = 120L
    private const val MAX_DELAY_MS = 10_000L

    /** 一次惯性滑动会连发多个 SCROLLED 事件，窗口内的合并成一条 */
    private const val SCROLL_MERGE_MS = 450L

    @Volatile
    var isRecording = false
        private set

    private val actions = ArrayList<RecordedAction>()
    private var lastActionElapsed = 0L
    private var lastScrollElapsed = 0L
    private var targetPackage = ""

    val actionCount: Int
        get() = synchronized(actions) { actions.size }

    /** 当前正在录制的目标应用包名（未捕获到任何操作时为空） */
    val recordingPackage: String
        get() = targetPackage

    fun start(context: Context) {
        synchronized(actions) { actions.clear() }
        lastActionElapsed = SystemClock.elapsedRealtime()
        lastScrollElapsed = 0L
        targetPackage = ""
        isRecording = true
        Log.i(TAG, "开始录制")
        notifyChanged(context)
    }

    /** 放弃本次录制，不落盘 */
    fun cancel(context: Context) {
        if (!isRecording && actionCount == 0) return
        isRecording = false
        synchronized(actions) { actions.clear() }
        Log.i(TAG, "取消录制")
        notifyChanged(context)
    }

    /**
     * 停止录制并写入脚本文件。
     * @return 文件名 to 脚本；没有捕获到任何有效步骤时返回 null
     */
    fun stopAndSave(context: Context, name: String? = null): Pair<String, RecordedScript>? {
        isRecording = false
        val snapshot = synchronized(actions) { actions.toList() }
        notifyChanged(context)
        if (snapshot.isEmpty()) {
            Log.w(TAG, "录制结束但没有捕获到任何操作")
            return null
        }
        val script = RecordedScript(
            name = name?.takeIf { it.isNotBlank() } ?: ScriptStore.defaultScriptName(),
            createdAt = System.currentTimeMillis(),
            pkg = targetPackage,
            actions = snapshot
        )
        val fileName = ScriptStore.save(context, script) ?: return null
        Log.i(TAG, "录制结束，已保存 ${snapshot.size} 步 → $fileName")
        return fileName to script
    }

    /** 由无障碍服务在 onAccessibilityEvent 中转发 */
    fun onAccessibilityEvent(context: Context, event: AccessibilityEvent) {
        if (!isRecording) return
        val pkg = event.packageName?.toString().orEmpty()
        // 忽略本应用自身（脚本页 / 悬浮控制条）产生的事件
        if (pkg.isEmpty() || pkg == context.packageName) return

        val action = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                buildTap(event, RecordedAction.TYPE_CLICK)

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                buildTap(event, RecordedAction.TYPE_LONG_CLICK)

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastScrollElapsed < SCROLL_MERGE_MS) return
                lastScrollElapsed = now
                buildSwipe(event)
            }

            else -> null
        } ?: return

        if (targetPackage.isEmpty()) targetPackage = pkg
        append(context, action)
    }

    // ---------- 构造动作 ----------

    private fun buildTap(event: AccessibilityEvent, type: String): RecordedAction? {
        val rect = boundsOf(event) ?: return null
        return RecordedAction(
            type = type,
            x = rect.centerX(),
            y = rect.centerY(),
            duration = if (type == RecordedAction.TYPE_LONG_CLICK) 700L else 60L,
            desc = describe(event)
        )
    }

    private fun buildSwipe(event: AccessibilityEvent): RecordedAction? {
        val rect = boundsOf(event) ?: return null
        var dx = 0
        var dy = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dx = event.scrollDeltaX
            dy = event.scrollDeltaY
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        val desc = describe(event)

        return if (abs(dx) > abs(dy) && dx != 0) {
            // 横向：内容右移（dx>0）对应手指左滑
            val amount = (rect.width() * 0.6f).toInt().coerceAtLeast(200)
            val sx = if (dx > 0) cx + amount / 2 else cx - amount / 2
            val ex = if (dx > 0) cx - amount / 2 else cx + amount / 2
            RecordedAction(RecordedAction.TYPE_SWIPE, sx, cy, ex, cy, duration = 300L, desc = desc)
        } else {
            // 纵向：内容下移（dy>=0，含未知）对应手指上滑
            val amount = (rect.height() * 0.6f).toInt().coerceAtLeast(200)
            val up = dy >= 0
            val sy = if (up) cy + amount / 2 else cy - amount / 2
            val ey = if (up) cy - amount / 2 else cy + amount / 2
            RecordedAction(RecordedAction.TYPE_SWIPE, cx, sy, cx, ey, duration = 320L, desc = desc)
        }
    }

    private fun boundsOf(event: AccessibilityEvent): Rect? {
        val node = try {
            event.source
        } catch (e: Exception) {
            Log.w(TAG, "获取事件源失败", e)
            null
        } ?: return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return if (rect.width() > 0 && rect.height() > 0) rect else null
    }

    private fun describe(event: AccessibilityEvent): String {
        val text = event.text?.joinToString(" ") { it.toString() }?.trim().orEmpty()
        if (text.isNotEmpty()) return text.take(24)
        val cd = event.contentDescription?.toString()?.trim().orEmpty()
        if (cd.isNotEmpty()) return cd.take(24)
        return event.className?.toString()?.substringAfterLast('.').orEmpty().take(24)
    }

    // ---------- 落库 ----------

    private fun append(context: Context, action: RecordedAction) {
        val now = SystemClock.elapsedRealtime()
        var reachedLimit = false
        var size = 0
        synchronized(actions) {
            if (actions.size >= MAX_ACTIONS) {
                reachedLimit = true
                size = actions.size
            } else {
                val delay = if (actions.isEmpty()) {
                    FIRST_DELAY_MS
                } else {
                    (now - lastActionElapsed).coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
                }
                actions.add(action.copy(delay = delay))
                size = actions.size
            }
        }
        lastActionElapsed = now
        if (reachedLimit) {
            Log.w(TAG, "已达最大步数 $MAX_ACTIONS，忽略后续操作")
            return
        }
        Log.d(TAG, "录制第 $size 步：${action.type} (${action.x}, ${action.y})")
        notifyChanged(context)
    }

    private fun notifyChanged(context: Context) {
        val app = context.applicationContext
        app.sendBroadcast(
            Intent(BROADCAST_RECORDER_CHANGED)
                .setPackage(app.packageName)
                .putExtra(EXTRA_RECORDING, isRecording)
                .putExtra(EXTRA_COUNT, actionCount)
        )
    }
}

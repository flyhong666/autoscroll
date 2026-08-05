package cn.ggdoc.autoscroll.recorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.ref.WeakReference

/**
 * 脚本回放器：按录制顺序用 dispatchGesture 还原点击 / 长按 / 滑动。
 * 支持循环次数与倍速，播放中可随时通过悬浮控制条停止。
 */
object ScriptPlayer {

    private const val TAG = "ScriptPlayer"

    const val BROADCAST_PLAYER_CHANGED = "cn.ggdoc.autoscroll.PLAYER_CHANGED"
    const val EXTRA_PLAYING = "playing"
    const val EXTRA_STEP = "step"
    const val EXTRA_TOTAL = "total"
    const val EXTRA_LOOP = "loop"
    const val EXTRA_LOOPS = "loops"

    @Volatile
    var isPlaying = false
        private set

    var scriptName: String = ""
        private set
    var stepIndex = 0
        private set
    var stepTotal = 0
        private set
    var loopIndex = 0
        private set
    var loopTotal = 1
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var serviceRef: WeakReference<AccessibilityService>? = null
    private var appContext: Context? = null
    private var actions: List<RecordedAction> = emptyList()
    private var speed = 1.0f
    private var pending: Runnable? = null
    private var screenW = 0
    private var screenH = 0

    /**
     * 开始回放。
     * @param loops 循环次数（>=1）
     * @param speedFactor 倍速（0.25~4）
     */
    fun play(
        service: AccessibilityService,
        script: RecordedScript,
        loops: Int,
        speedFactor: Float
    ): Boolean {
        if (isPlaying) {
            Log.w(TAG, "已有脚本在回放中")
            return false
        }
        if (script.actions.isEmpty()) return false

        serviceRef = WeakReference(service)
        appContext = service.applicationContext
        actions = script.actions
        scriptName = script.name
        stepTotal = actions.size
        stepIndex = 0
        loopTotal = loops.coerceAtLeast(1)
        loopIndex = 1
        speed = speedFactor.coerceIn(0.25f, 4f)

        val dm = service.resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        isPlaying = true
        Log.i(TAG, "开始回放「${script.name}」：$stepTotal 步 × $loopTotal 次，${speed}x")
        notifyChanged()
        scheduleStep()
        return true
    }

    fun stop() {
        if (!isPlaying) return
        Log.i(TAG, "手动停止回放")
        finish()
    }

    // ---------- 内部执行 ----------

    private fun scheduleStep() {
        if (!isPlaying) return
        if (stepIndex >= actions.size) {
            if (loopIndex >= loopTotal) {
                Log.i(TAG, "回放完成")
                finish()
                return
            }
            loopIndex++
            stepIndex = 0
            notifyChanged()
        }
        val action = actions[stepIndex]
        val delay = (action.delay / speed).toLong().coerceAtLeast(0L)
        val runnable = Runnable { execute(action) }
        pending = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun execute(action: RecordedAction) {
        if (!isPlaying) return
        val service = serviceRef?.get()
        if (service == null) {
            Log.w(TAG, "无障碍服务已断开，终止回放")
            finish()
            return
        }
        val duration = (action.duration / speed).toLong().coerceIn(30L, 30_000L)
        val advance = Runnable {
            stepIndex++
            notifyChanged()
            scheduleStep()
        }
        val gesture = buildGesture(action, duration)
        if (gesture == null) {
            handler.post(advance)
            return
        }
        val dispatched = try {
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        handler.post(advance)
                    }

                    override fun onCancelled(g: GestureDescription?) {
                        Log.w(TAG, "第 ${stepIndex + 1} 步手势被取消")
                        handler.post(advance)
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture 异常", e)
            false
        }
        // dispatch 失败时按预计时长兜底推进，避免卡死
        if (!dispatched) handler.postDelayed(advance, duration)
    }

    private fun buildGesture(a: RecordedAction, duration: Long): GestureDescription? {
        val path = Path()
        when (a.type) {
            RecordedAction.TYPE_CLICK, RecordedAction.TYPE_LONG_CLICK -> {
                path.moveTo(clampX(a.x), clampY(a.y))
            }

            RecordedAction.TYPE_SWIPE -> {
                path.moveTo(clampX(a.x), clampY(a.y))
                path.lineTo(clampX(a.x2), clampY(a.y2))
            }

            else -> return null   // wait 等非手势步骤只消耗 delay
        }
        return try {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "构建手势失败", e)
            null
        }
    }

    private fun clampX(v: Int): Float =
        if (screenW <= 0) v.toFloat() else v.coerceIn(1, screenW - 1).toFloat()

    private fun clampY(v: Int): Float =
        if (screenH <= 0) v.toFloat() else v.coerceIn(1, screenH - 1).toFloat()

    private fun finish() {
        isPlaying = false
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        serviceRef = null
        notifyChanged()
    }

    private fun notifyChanged() {
        val ctx = appContext ?: return
        ctx.sendBroadcast(
            Intent(BROADCAST_PLAYER_CHANGED).setPackage(ctx.packageName).apply {
                putExtra(EXTRA_PLAYING, isPlaying)
                putExtra(EXTRA_STEP, stepIndex)
                putExtra(EXTRA_TOTAL, stepTotal)
                putExtra(EXTRA_LOOP, loopIndex)
                putExtra(EXTRA_LOOPS, loopTotal)
            }
        )
    }
}

package cn.ggdoc.autoscroll.recorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
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
        // displayMetrics 不含状态栏/导航栏区域，改用真实屏幕尺寸，避免边缘坐标被裁切
        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenW = bounds.width()
                screenH = bounds.height()
            } else {
                val real = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(real)
                screenW = real.widthPixels
                screenH = real.heightPixels
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取真实屏幕尺寸失败，使用 displayMetrics", e)
        }

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

    private fun advanceStep() {
        // 若回放已停止（用户手动终止 / 服务断开），不再推进步骤，避免 stepIndex 越界
        if (!isPlaying) return
        stepIndex++
        notifyChanged()
        scheduleStep()
    }

    private fun execute(action: RecordedAction) {
        if (!isPlaying) return
        val service = serviceRef?.get()
        if (service == null) {
            Log.w(TAG, "无障碍服务已断开，终止回放")
            finish()
            return
        }
        // 条件分支：屏幕未出现指定文本则跳过本步，直接进入下一步
        if (action.condition.isNotBlank() && !conditionSatisfied(service, action.condition)) {
            Log.d(TAG, "条件未满足（未见「${action.condition}」），跳过第 ${stepIndex + 1} 步")
            handler.post(advance)
            return
        }
        val duration = (action.duration / speed).toLong().coerceIn(30L, 30_000L)
        val advance = Runnable { advanceStep() }
        // 双击：连发两次短按，两次之间留出人类间隔
        if (action.type == RecordedAction.TYPE_DOUBLE_TAP) {
            dispatchDoubleTap(service, action, duration, advance)
            return
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

    /** 双击回放：用同一个点按手势连发两次，首次完成后等待 ~70ms 再发第二次 */
    private fun dispatchDoubleTap(
        service: AccessibilityService,
        action: RecordedAction,
        duration: Long,
        advance: Runnable
    ) {
        val gesture = buildGesture(action, duration) ?: run {
            handler.post(advance)
            return
        }
        var firstDone = false
        val fireSecond = Runnable {
            // H3 修复：第二次 dispatchGesture 之前服务可能已断开 / 系统拒绝手势——
            // 抛异常会直接崩溃主线程 Handler，返回 false 则回调永不触发导致播放卡死。
            // 两种情况都必须兜底推进。
            if (!isPlaying) {
                advance.run()
                return@Runnable
            }
            val ok = try {
                service.dispatchGesture(gesture, cb, handler)
            } catch (e: Exception) {
                Log.e(TAG, "双击第二次 dispatchGesture 异常", e)
                false
            }
            if (!ok) handler.post(advance)
        }
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                if (!isPlaying) return
                if (!firstDone) {
                    firstDone = true
                    handler.postDelayed(fireSecond, 70L)
                } else {
                    advance.run()
                }
            }

            override fun onCancelled(g: GestureDescription?) {
                if (!isPlaying) return
                if (!firstDone) {
                    firstDone = true
                    handler.postDelayed(fireSecond, 70L)
                } else {
                    advance.run()
                }
            }
        }
        val ok = try {
            service.dispatchGesture(gesture, cb, handler)
        } catch (e: Exception) {
            Log.e(TAG, "双击 dispatchGesture 异常", e)
            false
        }
        if (!ok) handler.postDelayed(advance, duration)
    }

    /** 条件分支判定：当前窗口可见文本是否包含 [keyword]（不要求可点击） */
    private fun conditionSatisfied(service: AccessibilityService, keyword: String): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.w(TAG, "条件判定：取根节点失败", e)
            null
        } ?: return false
        val hit = try {
            cn.ggdoc.autoscroll.task.AdNodeKit.findNodeByText(root, keyword)
        } catch (e: Exception) {
            Log.w(TAG, "条件判定：扫描失败", e)
            null
        }
        if (hit != null && hit !== root) runCatching { hit.recycle() }
        runCatching { root.recycle() }
        return hit != null
    }

    private fun buildGesture(a: RecordedAction, duration: Long): GestureDescription? {
        val path = Path()
        when (a.type) {
            RecordedAction.TYPE_CLICK, RecordedAction.TYPE_LONG_CLICK, RecordedAction.TYPE_DOUBLE_TAP -> {
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

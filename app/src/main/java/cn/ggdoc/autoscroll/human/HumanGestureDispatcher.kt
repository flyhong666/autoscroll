package cn.ggdoc.autoscroll.human

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.util.Log
import kotlin.random.Random

/**
 * 拟人手势下发器：把 [GestureMath] 算出的贝塞尔轨迹真正发给系统。
 *
 * ## 为什么需要它
 * 原实现是 `Path().moveTo(a).lineTo(b)` + 单个 StrokeDescription。
 * 系统对单个 stroke 内部按**弧长匀速**插值，所以最终触摸序列是
 * 「完美等距的直线点列」——这是自动化最容易被识别的特征，没有之一。
 *
 * ## 变速怎么做
 * 单个 stroke 无法变速（系统强制匀速插值），必须靠**分段**：
 * 把轨迹切成 4 段，每段位移不同但时长按 sqrt 权重分配，
 * 于是段与段之间的平均速度不同，整体呈现「快起慢收」。
 *
 * 段之间用 `StrokeDescription.continueStroke()` 衔接，
 * 保证是**一次连续的手指按下-移动-抬起**，而不是 4 次独立点击。
 *
 * ## 版本策略
 * `continueStroke` 是 **API 26** 引入的。minSdk 是 24，因此：
 * - **API 26+**：完整的分段变速 + 贝塞尔弧度 + 微抖动
 * - **API 24~25**：单段贝塞尔折线（**弧度和抖动保留**，仅变速降级为匀速）
 *
 * 低版本仍能拿到大部分拟人收益，所以按既定原则**不打扰用户**、不弹版本提示。
 */
object HumanGestureDispatcher {

    private const val TAG = "HumanGesture"

    /** 低于此距离视为点按，不做曲线处理 */
    private const val TAP_THRESHOLD_PX = 6f

    /** 分段变速所需的最小距离：太短的滑动分段没有意义，反而增加失败风险 */
    private const val MIN_DISTANCE_FOR_SEGMENTS = 80f

    /** 系统对单个 GestureDescription 的 stroke 时长下限保护 */
    private const val MIN_STROKE_MS = 16L

    /**
     * 下发一次拟人滑动 / 点按。
     *
     * @param onFinished 手势结束回调，参数为「是否正常完成」（false 表示被系统取消）
     */
    fun dispatchSwipe(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        handler: Handler,
        rnd: Random = Random.Default,
        onFinished: (completed: Boolean) -> Unit = {}
    ) {
        val start = GestureMath.Pt(startX, startY)
        val end = GestureMath.Pt(endX, endY)
        val duration = durationMs.coerceAtLeast(MIN_STROKE_MS)

        // ---- 点按：无需曲线，单点即可 ----
        if (GestureMath.isTap(start, end, TAP_THRESHOLD_PX)) {
            val path = Path().apply { moveTo(startX, startY) }
            dispatchSingle(service, path, duration, handler, onFinished)
            return
        }

        val distance = GestureMath.distance(start, end)
        // 长距离滑动弧度大一些，短距离弧度小，与真人手腕活动范围一致
        val curveRatio = if (distance > 600f) 0.055f else 0.035f
        val points = GestureMath.samplePath(
            start = start,
            end = end,
            samples = GestureMath.DEFAULT_SAMPLES,
            curveRatio = curveRatio,
            jitterPx = 2.5f,
            rnd = rnd
        )

        val canSegment = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                distance >= MIN_DISTANCE_FOR_SEGMENTS

        if (canSegment) {
            dispatchSegmented(service, points, duration, handler, onFinished)
        } else {
            // API 24~25 或短距离：单段贝塞尔折线，保留弧度与抖动
            dispatchSingle(service, toPath(points), duration, handler, onFinished)
        }
    }

    /** 把采样点连成 Path（折线密度足够高，视觉与触摸序列上等同于曲线） */
    private fun toPath(points: List<GestureMath.Pt>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        return path
    }

    /** 单段下发（点按、低版本、短距离） */
    private fun dispatchSingle(
        service: AccessibilityService,
        path: Path,
        durationMs: Long,
        handler: Handler,
        onFinished: (Boolean) -> Unit
    ) {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) = onFinished(true)
            override fun onCancelled(g: GestureDescription?) = onFinished(false)
        }
        if (!service.dispatchGesture(gesture, cb, handler)) {
            Log.w(TAG, "dispatchGesture 返回 false（单段）")
            onFinished(false)
        }
    }

    /**
     * 分段连续下发（API 26+），实现变速。
     *
     * 每段完成后用 `continueStroke` 接上下一段，形成一次连续手势。
     * 任一段失败即终止并回调 false——不重试，避免与上层的取消回退逻辑打架。
     */
    private fun dispatchSegmented(
        service: AccessibilityService,
        points: List<GestureMath.Pt>,
        totalMs: Long,
        handler: Handler,
        onFinished: (Boolean) -> Unit
    ) {
        val segments = GestureMath.splitSegments(points, GestureMath.DEFAULT_SEGMENTS)
        if (segments.isEmpty()) {
            onFinished(false)
            return
        }
        val durations = GestureMath.allocateDurations(segments, totalMs, MIN_STROKE_MS)

        // 递归下发：第 index 段完成后接第 index+1 段
        fun step(index: Int, previous: GestureDescription.StrokeDescription?) {
            if (index >= segments.size) {
                onFinished(true)
                return
            }
            val isLast = index == segments.size - 1
            val path = toPath(segments[index].points)
            val dur = durations[index]

            val stroke = try {
                if (previous == null) {
                    GestureDescription.StrokeDescription(path, 0L, dur, !isLast)
                } else {
                    previous.continueStroke(path, 0L, dur, !isLast)
                }
            } catch (t: Throwable) {
                // 极少数 ROM 对 continueStroke 的起点校验较严，衔接失败时
                // 直接放弃剩余段：已经滑出去的部分通常已达成效果
                Log.w(TAG, "分段手势构造失败（第 $index 段），提前结束", t)
                onFinished(index > 0)
                return
            }

            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val cb = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    step(index + 1, stroke)
                }

                override fun onCancelled(g: GestureDescription?) {
                    Log.d(TAG, "分段手势第 $index 段被取消")
                    // 首段就被取消 = 整个手势没生效，需要上层兜底；
                    // 中途取消说明已经滑动了一部分，按成功处理避免重复滑动
                    onFinished(index > 0)
                }
            }
            if (!service.dispatchGesture(gesture, cb, handler)) {
                Log.w(TAG, "dispatchGesture 返回 false（第 $index 段）")
                onFinished(index > 0)
            }
        }

        step(0, null)
    }

    /**
     * 拟人双击（点赞用）。
     *
     * 两次点击的落点略有偏移（真人不可能两次点在同一像素），
     * 间隔也做随机化（真人双击间隔在 80~160ms 之间波动）。
     */
    fun dispatchDoubleTap(
        service: AccessibilityService,
        x: Float,
        y: Float,
        handler: Handler,
        rnd: Random = Random.Default
    ) {
        val jitter = 4f
        val x2 = x + (rnd.nextFloat() - 0.5f) * 2f * jitter
        val y2 = y + (rnd.nextFloat() - 0.5f) * 2f * jitter

        val p1 = Path().apply { moveTo(x, y) }
        val p2 = Path().apply { moveTo(x2, y2) }

        val d1 = rnd.nextLong(55, 95)
        val gap = rnd.nextLong(80, 160)
        val d2 = rnd.nextLong(55, 95)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0L, d1))
            .addStroke(GestureDescription.StrokeDescription(p2, d1 + gap, d2))
            .build()
        try {
            service.dispatchGesture(gesture, null, handler)
        } catch (t: Throwable) {
            Log.e(TAG, "双击手势下发失败", t)
        }
    }
}

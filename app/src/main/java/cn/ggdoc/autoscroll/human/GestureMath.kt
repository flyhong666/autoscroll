package cn.ggdoc.autoscroll.human

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 手势轨迹的纯几何计算。
 *
 * 这里刻意不引用任何 Android 类（Path / PointF 等），原因有二：
 * 1. 可以直接跑 JVM 单元测试，不需要 Robolectric；
 * 2. 轨迹算法与手势下发解耦，便于单独调参与验证。
 *
 * 真人滑动与程序直线滑动的三个可辨识差异，本文件逐一建模：
 * - **弧度**：手指绕腕关节/指关节旋转，轨迹是弓形而非直线 -> 三次贝塞尔
 * - **变速**：起手快、收尾慢（含惯性回落）-> easeOutCubic 时间分配
 * - **微抖动**：生理性震颤，采样点有 ±1~3px 的高频噪声 -> 逐点抖动
 */
object GestureMath {

    /** 轨迹采样点（自定义，避免依赖 android.graphics.PointF） */
    data class Pt(val x: Float, val y: Float)

    /** 一个变速分段：采样点序列 + 该段应占用的时长占比 */
    data class Segment(val points: List<Pt>, val durationRatio: Float)

    /** 采样点总数：太少轨迹变折线，太多无谓开销。24 点在 300~800ms 手势下足够平滑 */
    const val DEFAULT_SAMPLES = 24

    /** 变速分段数。段内匀速、段间变速，4 段已能明显区别于匀速直线 */
    const val DEFAULT_SEGMENTS = 4

    /**
     * 缓动函数：快起慢收（easeOutCubic）。
     *
     * 真人滑动的速度曲线是「爆发式起手 + 摩擦减速收尾」，
     * 前 30% 的时间会走完约 66% 的距离，与匀速的 30% 差异显著。
     */
    fun easeOutCubic(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val inv = 1f - c
        return 1f - inv * inv * inv
    }

    /**
     * 计算三次贝塞尔的两个控制点。
     *
     * 控制点沿主方向的 30%/70% 处，再朝**垂直于主方向**偏移，形成弓形。
     * 偏移量取路径长度的 [curveRatio]，并带随机符号——真人有时向左弓、有时向右弓。
     *
     * @param curveRatio 弓形幅度占路径长度的比例，建议 0.02~0.08
     */
    fun controlPoints(
        start: Pt,
        end: Pt,
        curveRatio: Float,
        rnd: Random = Random.Default
    ): Pair<Pt, Pt> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 1f) {
            // 起终点几乎重合（点按类手势）：控制点退化到原点，避免除零
            return Pt(start.x, start.y) to Pt(end.x, end.y)
        }
        // 单位法向量（把方向向量逆时针转 90°）
        val nx = -dy / len
        val ny = dx / len

        // 两个控制点的弓形幅度略有差异，避免完美对称的「机器弧」
        val sign = if (rnd.nextBoolean()) 1f else -1f
        val amp1 = len * curveRatio * (0.6f + rnd.nextFloat() * 0.8f) * sign
        val amp2 = len * curveRatio * (0.6f + rnd.nextFloat() * 0.8f) * sign

        val c1 = Pt(start.x + dx * 0.30f + nx * amp1, start.y + dy * 0.30f + ny * amp1)
        val c2 = Pt(start.x + dx * 0.70f + nx * amp2, start.y + dy * 0.70f + ny * amp2)
        return c1 to c2
    }

    /** 三次贝塞尔在参数 t 处的坐标 */
    fun bezierAt(p0: Pt, c1: Pt, c2: Pt, p3: Pt, t: Float): Pt {
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        return Pt(
            a * p0.x + b * c1.x + c * c2.x + d * p3.x,
            a * p0.y + b * c1.y + c * c2.y + d * p3.y
        )
    }

    /**
     * 生成完整轨迹采样点。
     *
     * 采样在**时间轴**上按 [easeOutCubic] 非均匀取点：
     * 时间等分 -> 贝塞尔参数 t 经缓动映射 -> 得到「前密后疏」的空间分布。
     * 单段手势里系统按弧长匀速插值，所以这一步主要保证曲率；
     * 真正的变速由 [splitSegments] 的分段时长承担。
     *
     * @param jitterPx 每个中间点的随机抖动幅度（像素），模拟生理震颤；首尾点不抖动
     */
    fun samplePath(
        start: Pt,
        end: Pt,
        samples: Int = DEFAULT_SAMPLES,
        curveRatio: Float = 0.04f,
        jitterPx: Float = 2.5f,
        rnd: Random = Random.Default
    ): List<Pt> {
        val n = samples.coerceAtLeast(2)
        val (c1, c2) = controlPoints(start, end, curveRatio, rnd)
        val out = ArrayList<Pt>(n)
        for (i in 0 until n) {
            val timeT = i.toFloat() / (n - 1)
            val curveT = easeOutCubic(timeT)
            val p = bezierAt(start, c1, c2, end, curveT)
            // 首尾必须精确落在起终点：起点偏了会导致 continueStroke 衔接失败
            if (i == 0) {
                out.add(start)
            } else if (i == n - 1) {
                out.add(end)
            } else {
                val jx = (rnd.nextFloat() - 0.5f) * 2f * jitterPx
                val jy = (rnd.nextFloat() - 0.5f) * 2f * jitterPx
                out.add(Pt(p.x + jx, p.y + jy))
            }
        }
        return out
    }

    /**
     * 把采样点切成 [segments] 段，并为每段计算时长占比。
     *
     * 关键点：采样点在空间上已是「前密后疏」（因为按缓动取的），
     * 若各段时长均分，则前段走得慢、后段走得快——**方向反了**。
     * 所以这里反过来：按各段的**实际路径长度**占比分配时长的倒数权重，
     * 使「长位移段用更短时间」，最终呈现快起慢收。
     *
     * 每段首点与上一段末点重合，保证 continueStroke 能无缝衔接。
     */
    fun splitSegments(
        points: List<Pt>,
        segments: Int = DEFAULT_SEGMENTS
    ): List<Segment> {
        if (points.size < 2) return emptyList()
        val segCount = segments.coerceIn(1, points.size - 1)
        if (segCount == 1) return listOf(Segment(points, 1f))

        // 均分采样点索引
        val chunks = ArrayList<List<Pt>>(segCount)
        val per = (points.size - 1).toFloat() / segCount
        for (i in 0 until segCount) {
            val from = (i * per).toInt()
            val to = if (i == segCount - 1) points.size - 1 else ((i + 1) * per).toInt()
            chunks.add(points.subList(from, to + 1).toList())
        }

        // 每段路径长度
        val lens = chunks.map { seg ->
            var s = 0f
            for (i in 1 until seg.size) {
                s += hypot((seg[i].x - seg[i - 1].x).toDouble(), (seg[i].y - seg[i - 1].y).toDouble())
                    .toFloat()
            }
            maxOf(s, 0.01f)
        }
        val totalLen = lens.sum()

        // 时长权重 = sqrt(长度占比) 的归一化。
        // 用 sqrt 而非线性：完全按长度分配会退化成匀速；
        // 完全均分又会让长段过快。sqrt 是两者之间的折中，
        // 效果是「长段稍快、短段稍慢」，即快起慢收。
        val weights = lens.map { sqrt((it / totalLen).toDouble()).toFloat() }
        val wSum = weights.sum()

        return chunks.mapIndexed { i, pts -> Segment(pts, weights[i] / wSum) }
    }

    /**
     * 把时长占比换算成毫秒，并保证每段至少 [minMs]、总和精确等于 [totalMs]。
     * 系统对 stroke duration<1ms 会直接拒绝，必须兜底。
     */
    fun allocateDurations(
        segments: List<Segment>,
        totalMs: Long,
        minMs: Long = 16L
    ): List<Long> {
        if (segments.isEmpty()) return emptyList()
        if (segments.size == 1) return listOf(totalMs.coerceAtLeast(minMs))

        val raw = segments.map { (totalMs * it.durationRatio).toLong().coerceAtLeast(minMs) }
        val sum = raw.sum()
        if (sum == totalMs) return raw

        // 差额补到最后一段（最后一段最长，吸收误差不易被察觉）
        val out = raw.toMutableList()
        val diff = totalMs - sum
        val lastIdx = out.size - 1
        out[lastIdx] = (out[lastIdx] + diff).coerceAtLeast(minMs)
        return out
    }

    /** 两点距离，供调用方判断是否为「点按」（距离过小无需曲线） */
    fun distance(a: Pt, b: Pt): Float =
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

    /** 判断是否近似点按（起终点重合） */
    fun isTap(a: Pt, b: Pt, thresholdPx: Float = 3f): Boolean =
        abs(a.x - b.x) < thresholdPx && abs(a.y - b.y) < thresholdPx
}

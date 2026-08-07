package cn.ggdoc.autoscroll.human

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 拟人节奏：把「均匀分布的随机间隔」换成更接近真人的**长尾分布**，
 * 并叠加疲劳曲线与偶发长驻留。
 *
 * 为什么均匀分布不像人：
 * `Random.nextInt(3, 20)` 生成的间隔在 3~20 秒内等概率，
 * 意味着「停 3 秒」和「停 19 秒」一样常见。但真人刷信息流是
 * **大量快速划过 + 偶尔停下细看**，分布高度右偏——
 * 中位数可能只有 4 秒，但偶尔会出现 30 秒的长停留。
 *
 * 本文件全部为纯函数，可直接单元测试。
 */
object HumanTiming {

    /** 偶发长驻留的触发概率（%）：模拟「刷到感兴趣的内容停下细看」 */
    const val LONG_DWELL_PROBABILITY = 8

    /** 长驻留的倍率区间 */
    const val LONG_DWELL_MIN_FACTOR = 1.8f
    const val LONG_DWELL_MAX_FACTOR = 3.2f

    /** 疲劳曲线：达到最大减速所需的运行分钟数 */
    const val FATIGUE_FULL_MINUTES = 90f

    /** 疲劳导致的最大减速倍率（1.45 = 节奏放慢 45%） */
    const val FATIGUE_MAX_FACTOR = 1.45f

    /**
     * 标准正态随机数（Box-Muller 变换）。
     * Kotlin 标准库的 Random 没有 nextGaussian，这里自己实现。
     */
    fun nextGaussian(rnd: Random): Double {
        // 避免 ln(0)
        var u1 = rnd.nextDouble()
        while (u1 <= 1e-12) u1 = rnd.nextDouble()
        val u2 = rnd.nextDouble()
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    /**
     * 对数正态分布采样，落在 [minSec, maxSec] 区间内。
     *
     * 参数选取思路：把区间的**几何中心偏下的位置**作为中位数
     * （取 min 与 max 的加权几何平均，权重偏向 min），
     * sigma 控制右尾长度。结果是：多数值靠近 min，少数值拉到 max。
     *
     * @return 秒数（Double），已裁剪到 [minSec, maxSec]
     */
    fun logNormalSeconds(
        minSec: Double,
        maxSec: Double,
        sigma: Double = 0.55,
        rnd: Random = Random.Default
    ): Double {
        if (maxSec <= minSec) return minSec
        // 中位数取「偏向下限」的几何平均：min^0.68 * max^0.32
        val median = Math.pow(minSec.coerceAtLeast(0.1), 0.68) *
                Math.pow(maxSec, 0.32)
        val mu = ln(median.coerceAtLeast(0.1))
        val v = exp(mu + sigma * nextGaussian(rnd))
        return v.coerceIn(minSec, maxSec)
    }

    /**
     * 疲劳因子：运行越久，节奏越慢。
     *
     * 真人连续刷 1 小时后，翻页速度会明显下降。全程恒定节奏
     * 反而是自动化最容易被识别的特征之一。
     *
     * @param runningMinutes 本次已运行分钟数
     * @return 1.0（刚开始）~ [FATIGUE_MAX_FACTOR]（长时间运行后）
     */
    fun fatigueFactor(runningMinutes: Float): Float {
        if (runningMinutes <= 0f) return 1f
        val p = (runningMinutes / FATIGUE_FULL_MINUTES).coerceIn(0f, 1f)
        // 用平方根曲线：前期衰减快，后期趋于平缓，比线性更自然
        return 1f + (FATIGUE_MAX_FACTOR - 1f) * sqrt(p)
    }

    /**
     * 按概率把间隔放大成一次「长驻留」。
     * 这是长尾分布之外额外的一层偶发性——对应真人「被某条内容抓住」的行为。
     */
    fun applyOccasionalLongDwell(baseMs: Long, rnd: Random = Random.Default): Long {
        if (rnd.nextInt(100) >= LONG_DWELL_PROBABILITY) return baseMs
        val factor = LONG_DWELL_MIN_FACTOR +
                rnd.nextFloat() * (LONG_DWELL_MAX_FACTOR - LONG_DWELL_MIN_FACTOR)
        return (baseMs * factor).toLong()
    }

    /**
     * 计算下一次滑动的间隔（毫秒）——对外主入口。
     *
     * 三层叠加：对数正态基础值 -> 疲劳减速 -> 偶发长驻留。
     *
     * @param minSec 用户配置的最小间隔（秒）
     * @param maxSec 用户配置的最大间隔（秒）
     * @param runningMinutes 本次已运行分钟数，用于疲劳曲线
     */
    fun nextIntervalMs(
        minSec: Int,
        maxSec: Int,
        runningMinutes: Float = 0f,
        rnd: Random = Random.Default
    ): Long {
        val lo = minSec.coerceAtLeast(1).toDouble()
        val hi = maxSec.coerceAtLeast(minSec + 1).toDouble()
        val baseSec = logNormalSeconds(lo, hi, rnd = rnd)
        val fatigued = baseSec * fatigueFactor(runningMinutes)
        // 疲劳可以突破用户设定的上限，但不超过 1.6 倍，避免看起来像卡死
        val cappedSec = fatigued.coerceAtMost(hi * 1.6)
        return applyOccasionalLongDwell((cappedSec * 1000).toLong(), rnd)
    }

    /**
     * 手势时长的拟人采样。
     *
     * 滑动时长同样不该均匀分布——真人快速划过占多数，
     * 慢速拖动是少数。这里用轻度右偏（sigma 较小）。
     */
    fun nextDurationMs(
        minMs: Int,
        maxMs: Int,
        rnd: Random = Random.Default
    ): Long {
        val lo = minMs.coerceAtLeast(30).toDouble()
        val hi = maxMs.coerceAtLeast(minMs + 1).toDouble()
        return logNormalSeconds(lo, hi, sigma = 0.32, rnd = rnd).toLong()
    }
}

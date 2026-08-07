package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 拟人节奏分布的单元测试。
 *
 * 这里测的不只是「不崩溃」，更重要的是**统计特征**：
 * 长尾分布的中位数必须明显低于区间中点，否则和均匀分布没区别。
 */
class HumanTimingTest {

    // ---------- nextGaussian ----------

    @Test
    fun `高斯采样均值接近 0 标准差接近 1`() {
        val rnd = Random(20260807)
        val n = 20000
        val values = DoubleArray(n) { HumanTiming.nextGaussian(rnd) }
        val mean = values.average()
        val sd = kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / n)
        assertEquals("均值偏离 0", 0.0, mean, 0.05)
        assertEquals("标准差偏离 1", 1.0, sd, 0.05)
    }

    @Test
    fun `高斯采样不产生 NaN 或无穷`() {
        val rnd = Random(42)
        repeat(50000) {
            val v = HumanTiming.nextGaussian(rnd)
            assertTrue("产生了非法值 $v", v.isFinite())
        }
    }

    // ---------- logNormalSeconds ----------

    @Test
    fun `对数正态采样始终落在区间内`() {
        val rnd = Random(7)
        repeat(10000) {
            val v = HumanTiming.logNormalSeconds(3.0, 20.0, rnd = rnd)
            assertTrue("越界值 $v", v in 3.0..20.0)
        }
    }

    @Test
    fun `对数正态中位数明显低于区间中点`() {
        // 这是长尾分布的核心特征：多数值靠近下限，少数拉到上限。
        // 若中位数在区间中点附近，说明退化成了均匀分布。
        val rnd = Random(20260807)
        val samples = List(20000) { HumanTiming.logNormalSeconds(3.0, 20.0, rnd = rnd) }.sorted()
        val median = samples[samples.size / 2]
        val midpoint = (3.0 + 20.0) / 2
        assertTrue("中位数 $median 未明显低于区间中点 $midpoint", median < midpoint * 0.8)
        assertTrue("中位数 $median 低于下限，异常", median >= 3.0)
    }

    @Test
    fun `对数正态存在右尾长停留`() {
        val rnd = Random(20260807)
        val samples = List(20000) { HumanTiming.logNormalSeconds(3.0, 20.0, rnd = rnd) }
        // 应当有一定比例的样本落在区间上半部分——真人偶尔会停下细看
        val longOnes = samples.count { it > 12.0 }
        assertTrue("缺少长停留样本（$longOnes 个），右尾丢失", longOnes > 100)
    }

    @Test
    fun `上限不大于下限时返回下限`() {
        assertEquals(5.0, HumanTiming.logNormalSeconds(5.0, 5.0), 1e-9)
        assertEquals(5.0, HumanTiming.logNormalSeconds(5.0, 3.0), 1e-9)
    }

    // ---------- fatigueFactor ----------

    @Test
    fun `疲劳因子起点为 1`() {
        assertEquals(1f, HumanTiming.fatigueFactor(0f), 1e-6f)
        assertEquals(1f, HumanTiming.fatigueFactor(-10f), 1e-6f)
    }

    @Test
    fun `疲劳因子随时间单调递增并封顶`() {
        var prev = 0f
        for (m in 0..200 step 5) {
            val f = HumanTiming.fatigueFactor(m.toFloat())
            assertTrue("第 $m 分钟疲劳因子回退", f >= prev)
            assertTrue("疲劳因子超过上限：$f", f <= HumanTiming.FATIGUE_MAX_FACTOR + 1e-5f)
            prev = f
        }
        // 满 90 分钟应达到上限
        assertEquals(
            HumanTiming.FATIGUE_MAX_FACTOR,
            HumanTiming.fatigueFactor(HumanTiming.FATIGUE_FULL_MINUTES),
            1e-4f
        )
    }

    @Test
    fun `疲劳曲线前期变化快于后期`() {
        // sqrt 曲线的特征：前 25% 时间的增量应大于后 25%
        val early = HumanTiming.fatigueFactor(22.5f) - HumanTiming.fatigueFactor(0f)
        val late = HumanTiming.fatigueFactor(90f) - HumanTiming.fatigueFactor(67.5f)
        assertTrue("疲劳曲线不是前快后慢（early=$early late=$late）", early > late)
    }

    // ---------- applyOccasionalLongDwell ----------

    @Test
    fun `长驻留按概率触发且倍率在区间内`() {
        val rnd = Random(20260807)
        val base = 5000L
        var triggered = 0
        val n = 20000
        repeat(n) {
            val v = HumanTiming.applyOccasionalLongDwell(base, rnd)
            if (v != base) {
                triggered++
                val factor = v.toDouble() / base
                assertTrue(
                    "倍率 $factor 超出 [${HumanTiming.LONG_DWELL_MIN_FACTOR}, ${HumanTiming.LONG_DWELL_MAX_FACTOR}]",
                    factor >= HumanTiming.LONG_DWELL_MIN_FACTOR - 0.01 &&
                        factor <= HumanTiming.LONG_DWELL_MAX_FACTOR + 0.01
                )
            }
        }
        val rate = triggered * 100.0 / n
        assertEquals("触发率偏离设定值", HumanTiming.LONG_DWELL_PROBABILITY.toDouble(), rate, 1.5)
    }

    // ---------- nextIntervalMs ----------

    @Test
    fun `间隔采样为正且不超过疲劳上限`() {
        val rnd = Random(20260807)
        val maxAllowed = (20 * 1.6 * HumanTiming.LONG_DWELL_MAX_FACTOR * 1000).toLong()
        repeat(20000) {
            val v = HumanTiming.nextIntervalMs(3, 20, runningMinutes = 45f, rnd = rnd)
            assertTrue("间隔非正：$v", v > 0)
            assertTrue("间隔过大：$v", v <= maxAllowed)
        }
    }

    @Test
    fun `疲劳状态下平均间隔明显变长`() {
        val fresh = List(5000) {
            HumanTiming.nextIntervalMs(3, 20, runningMinutes = 0f, rnd = Random(it))
        }.average()
        val tired = List(5000) {
            HumanTiming.nextIntervalMs(3, 20, runningMinutes = 90f, rnd = Random(it))
        }.average()
        assertTrue("疲劳未导致节奏变慢（fresh=$fresh tired=$tired）", tired > fresh * 1.15)
    }

    @Test
    fun `最大值小于最小值时自动纠正不崩溃`() {
        val v = HumanTiming.nextIntervalMs(10, 5, rnd = Random(1))
        assertTrue("异常配置下应仍返回正值，实际 $v", v > 0)
    }

    // ---------- nextDurationMs ----------

    @Test
    fun `手势时长落在配置区间内`() {
        val rnd = Random(20260807)
        repeat(10000) {
            val v = HumanTiming.nextDurationMs(300, 500, rnd)
            assertTrue("时长越界：$v", v in 300L..500L)
        }
    }

    @Test
    fun `手势时长下限保护`() {
        // 传入过小的值应被抬到 30ms 以上，否则系统会拒绝该手势
        val v = HumanTiming.nextDurationMs(1, 2, Random(1))
        assertTrue("时长过短：$v", v >= 30L)
    }

    @Test
    fun `手势时长偏向下限`() {
        val rnd = Random(20260807)
        val samples = List(10000) { HumanTiming.nextDurationMs(300, 800, rnd) }.sorted()
        val median = samples[samples.size / 2]
        assertTrue("时长中位数 $median 未偏向下限", median < 550)
    }
}

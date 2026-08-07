package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * 手势轨迹几何计算的单元测试。
 *
 * 重点验证「和真人的差异点」是否真的被建模出来了：
 * 轨迹是否有弧度、时间分配是否快起慢收、首尾点是否精确。
 */
class GestureMathTest {

    /** 固定种子，保证测试可重复 */
    private fun rnd() = Random(20260807)

    // ---------- easeOutCubic ----------

    @Test
    fun `easeOutCubic 端点精确`() {
        assertEquals(0f, GestureMath.easeOutCubic(0f), 1e-6f)
        assertEquals(1f, GestureMath.easeOutCubic(1f), 1e-6f)
    }

    @Test
    fun `easeOutCubic 越界输入被裁剪`() {
        assertEquals(0f, GestureMath.easeOutCubic(-5f), 1e-6f)
        assertEquals(1f, GestureMath.easeOutCubic(9f), 1e-6f)
    }

    @Test
    fun `easeOutCubic 前段推进明显快于匀速`() {
        // 这是「快起慢收」的核心断言：走完 30% 的时间应远超 30% 的进度
        val at30 = GestureMath.easeOutCubic(0.3f)
        assertTrue("30%时间应走完超过60%进度，实际=$at30", at30 > 0.6f)
    }

    @Test
    fun `easeOutCubic 单调递增`() {
        var prev = -1f
        for (i in 0..100) {
            val v = GestureMath.easeOutCubic(i / 100f)
            assertTrue("t=$i 处出现回退", v >= prev)
            prev = v
        }
    }

    // ---------- controlPoints ----------

    @Test
    fun `控制点偏离直线形成弧度`() {
        val start = GestureMath.Pt(500f, 1600f)
        val end = GestureMath.Pt(500f, 400f)
        val (c1, c2) = GestureMath.controlPoints(start, end, 0.05f, rnd())
        // 竖直滑动的控制点必须有横向偏移，否则就退化成直线了
        assertTrue("c1 无横向偏移", abs(c1.x - 500f) > 1f)
        assertTrue("c2 无横向偏移", abs(c2.x - 500f) > 1f)
    }

    @Test
    fun `控制点弓向一致不产生 S 形`() {
        // 两个控制点若一左一右会形成 S 形，那不是真人手势
        repeat(20) { seed ->
            val start = GestureMath.Pt(300f, 1500f)
            val end = GestureMath.Pt(300f, 300f)
            val (c1, c2) = GestureMath.controlPoints(start, end, 0.05f, Random(seed))
            val d1 = c1.x - 300f
            val d2 = c2.x - 300f
            assertTrue("seed=$seed 出现 S 形弯曲", d1 * d2 >= 0f)
        }
    }

    @Test
    fun `起终点重合时控制点退化不崩溃`() {
        val p = GestureMath.Pt(100f, 100f)
        val (c1, c2) = GestureMath.controlPoints(p, p, 0.05f, rnd())
        assertEquals(100f, c1.x, 1e-3f)
        assertEquals(100f, c2.y, 1e-3f)
    }

    // ---------- bezierAt ----------

    @Test
    fun `贝塞尔端点精确落在起终点`() {
        val p0 = GestureMath.Pt(0f, 0f)
        val c1 = GestureMath.Pt(30f, 80f)
        val c2 = GestureMath.Pt(70f, 20f)
        val p3 = GestureMath.Pt(100f, 100f)
        val at0 = GestureMath.bezierAt(p0, c1, c2, p3, 0f)
        val at1 = GestureMath.bezierAt(p0, c1, c2, p3, 1f)
        assertEquals(0f, at0.x, 1e-4f)
        assertEquals(100f, at1.x, 1e-4f)
        assertEquals(100f, at1.y, 1e-4f)
    }

    // ---------- samplePath ----------

    @Test
    fun `采样点数量正确且首尾精确`() {
        val start = GestureMath.Pt(540f, 1700f)
        val end = GestureMath.Pt(540f, 400f)
        val pts = GestureMath.samplePath(start, end, samples = 24, rnd = rnd())
        assertEquals(24, pts.size)
        // 首尾必须精确——起点偏了会导致 continueStroke 衔接失败
        assertEquals(start, pts.first())
        assertEquals(end, pts.last())
    }

    @Test
    fun `采样轨迹并非直线`() {
        val start = GestureMath.Pt(540f, 1700f)
        val end = GestureMath.Pt(540f, 400f)
        val pts = GestureMath.samplePath(start, end, curveRatio = 0.06f, rnd = rnd())
        // 中间点应偏离 x=540 这条直线
        val maxDeviation = pts.drop(1).dropLast(1).maxOf { abs(it.x - 540f) }
        assertTrue("轨迹仍是直线，偏移=$maxDeviation", maxDeviation > 2f)
    }

    @Test
    fun `两次采样轨迹不完全相同`() {
        val start = GestureMath.Pt(540f, 1700f)
        val end = GestureMath.Pt(540f, 400f)
        val a = GestureMath.samplePath(start, end, rnd = Random(1))
        val b = GestureMath.samplePath(start, end, rnd = Random(2))
        assertNotEquals("不同种子应产生不同轨迹", a, b)
    }

    @Test
    fun `采样点数下限保护`() {
        val start = GestureMath.Pt(0f, 0f)
        val end = GestureMath.Pt(10f, 10f)
        // 传入 0 或 1 都应至少产出 2 个点，否则无法构成路径
        assertTrue(GestureMath.samplePath(start, end, samples = 0).size >= 2)
        assertTrue(GestureMath.samplePath(start, end, samples = 1).size >= 2)
    }

    // ---------- splitSegments ----------

    @Test
    fun `分段数量与时长占比归一`() {
        val pts = GestureMath.samplePath(
            GestureMath.Pt(540f, 1700f), GestureMath.Pt(540f, 400f), rnd = rnd()
        )
        val segs = GestureMath.splitSegments(pts, 4)
        assertEquals(4, segs.size)
        val sum = segs.sumOf { it.durationRatio.toDouble() }
        assertEquals("时长占比之和应为 1", 1.0, sum, 1e-4)
    }

    @Test
    fun `相邻分段首尾点重合保证衔接`() {
        val pts = GestureMath.samplePath(
            GestureMath.Pt(540f, 1700f), GestureMath.Pt(540f, 400f), rnd = rnd()
        )
        val segs = GestureMath.splitSegments(pts, 4)
        for (i in 1 until segs.size) {
            assertEquals(
                "第 $i 段起点与上一段终点不重合，continueStroke 会失败",
                segs[i - 1].points.last(), segs[i].points.first()
            )
        }
    }

    @Test
    fun `位移大的段分到更短的单位时长即变速生效`() {
        val pts = GestureMath.samplePath(
            GestureMath.Pt(540f, 1700f), GestureMath.Pt(540f, 400f),
            jitterPx = 0f, rnd = rnd()
        )
        val segs = GestureMath.splitSegments(pts, 4)
        fun segLen(s: GestureMath.Segment): Float {
            var t = 0f
            for (i in 1 until s.points.size) t += GestureMath.distance(s.points[i - 1], s.points[i])
            return t
        }
        // 第一段（缓动下位移最大）的「单位距离耗时」应小于最后一段
        val firstSpeed = segLen(segs.first()) / segs.first().durationRatio
        val lastSpeed = segLen(segs.last()) / segs.last().durationRatio
        assertTrue("首段未比末段快，变速未生效", firstSpeed > lastSpeed)
    }

    @Test
    fun `分段数为 1 时返回整条轨迹`() {
        val pts = GestureMath.samplePath(GestureMath.Pt(0f, 0f), GestureMath.Pt(100f, 100f))
        val segs = GestureMath.splitSegments(pts, 1)
        assertEquals(1, segs.size)
        assertEquals(1f, segs[0].durationRatio, 1e-6f)
    }

    @Test
    fun `点数不足时返回空分段`() {
        assertTrue(GestureMath.splitSegments(emptyList()).isEmpty())
        assertTrue(GestureMath.splitSegments(listOf(GestureMath.Pt(0f, 0f))).isEmpty())
    }

    // ---------- allocateDurations ----------

    @Test
    fun `时长分配总和精确等于目标`() {
        val pts = GestureMath.samplePath(
            GestureMath.Pt(540f, 1700f), GestureMath.Pt(540f, 400f), rnd = rnd()
        )
        val segs = GestureMath.splitSegments(pts, 4)
        val durations = GestureMath.allocateDurations(segs, 480L)
        assertEquals(480L, durations.sum())
    }

    @Test
    fun `每段时长不低于最小值`() {
        val pts = GestureMath.samplePath(
            GestureMath.Pt(540f, 1700f), GestureMath.Pt(540f, 400f), rnd = rnd()
        )
        val segs = GestureMath.splitSegments(pts, 4)
        // 极短总时长下仍需保证每段 >= minMs，否则系统会拒绝该 stroke
        val durations = GestureMath.allocateDurations(segs, 20L, minMs = 16L)
        assertTrue("存在低于 16ms 的分段：$durations", durations.all { it >= 16L })
    }

    @Test
    fun `空分段返回空时长列表`() {
        assertTrue(GestureMath.allocateDurations(emptyList(), 300L).isEmpty())
    }

    // ---------- distance / isTap ----------

    @Test
    fun `距离计算正确`() {
        assertEquals(
            5f,
            GestureMath.distance(GestureMath.Pt(0f, 0f), GestureMath.Pt(3f, 4f)),
            1e-4f
        )
    }

    @Test
    fun `点按判定阈值生效`() {
        assertTrue(GestureMath.isTap(GestureMath.Pt(10f, 10f), GestureMath.Pt(11f, 11f)))
        assertTrue(!GestureMath.isTap(GestureMath.Pt(10f, 10f), GestureMath.Pt(30f, 10f)))
    }
}

package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡死检测状态机的单元测试。
 *
 * 重点验证「分级触发只发生一次」——如果写成 `>=` 判断，
 * 第 5、6、7 次都会疯狂按返回键，那才是真的把用户手机搞乱。
 */
class StuckDetectorTest {

    // ---------- fingerprint ----------

    @Test
    fun `相同文本产生相同指纹`() {
        val a = StuckDetector.fingerprint(listOf("头条推荐", "军事", "体育"))
        val b = StuckDetector.fingerprint(listOf("头条推荐", "军事", "体育"))
        assertEquals(a, b)
    }

    @Test
    fun `不同文本产生不同指纹`() {
        val a = StuckDetector.fingerprint(listOf("头条推荐", "军事"))
        val b = StuckDetector.fingerprint(listOf("头条推荐", "财经"))
        assertNotEquals(a, b)
    }

    @Test
    fun `顺序变化会改变指纹`() {
        // 列表项顺序变了说明内容刷新了，必须被识别为「有变化」
        val a = StuckDetector.fingerprint(listOf("A", "B", "C"))
        val b = StuckDetector.fingerprint(listOf("C", "B", "A"))
        assertNotEquals(a, b)
    }

    @Test
    fun `分隔符避免拼接歧义`() {
        // ["ab","c"] 与 ["a","bc"] 拼起来都是 "abc"，没有分隔符就会撞值
        val a = StuckDetector.fingerprint(listOf("ab", "c"))
        val b = StuckDetector.fingerprint(listOf("a", "bc"))
        assertNotEquals(a, b)
    }

    @Test
    fun `空列表返回哨兵值`() {
        assertEquals(StuckDetector.NO_HASH, StuckDetector.fingerprint(emptyList()))
    }

    @Test
    fun `大量不同输入不产生哈希碰撞`() {
        val hashes = HashSet<Long>()
        repeat(5000) { i ->
            hashes.add(StuckDetector.fingerprint(listOf("新闻标题第${i}条", "作者$i")))
        }
        assertEquals("出现哈希碰撞", 5000, hashes.size)
    }

    // ---------- submit 状态机 ----------

    @Test
    fun `内容持续变化时不触发任何动作`() {
        val d = StuckDetector()
        repeat(50) { i ->
            assertEquals(StuckDetector.Action.NONE, d.submit(i.toLong()))
        }
        assertEquals(0, d.consecutiveSame)
    }

    @Test
    fun `连续 3 次无变化触发关弹窗`() {
        val d = StuckDetector()
        val h = 12345L
        assertEquals(StuckDetector.Action.NONE, d.submit(h))   // 首次记录
        assertEquals(StuckDetector.Action.NONE, d.submit(h))   // same=1
        assertEquals(StuckDetector.Action.NONE, d.submit(h))   // same=2
        assertEquals(StuckDetector.Action.CLOSE_POPUP, d.submit(h)) // same=3
    }

    @Test
    fun `分级动作依次触发且每级只触发一次`() {
        val d = StuckDetector()
        val h = 999L
        val actions = mutableListOf<StuckDetector.Action>()
        repeat(9) { actions.add(d.submit(h)) }
        // 首次记录 + 8 次累计
        assertEquals(StuckDetector.Action.CLOSE_POPUP, actions[3])   // same=3
        assertEquals(StuckDetector.Action.NONE, actions[4])          // same=4
        assertEquals(StuckDetector.Action.PRESS_BACK, actions[5])    // same=5
        assertEquals(StuckDetector.Action.NONE, actions[6])          // same=6
        assertEquals(StuckDetector.Action.NONE, actions[7])          // same=7
        assertEquals(StuckDetector.Action.RESTART_APP, actions[8])   // same=8

        // 关键：每个阈值只触发一次，不能连续按返回键
        assertEquals(1, actions.count { it == StuckDetector.Action.PRESS_BACK })
        assertEquals(1, actions.count { it == StuckDetector.Action.CLOSE_POPUP })
    }

    @Test
    fun `长期卡死周期性重试重启`() {
        val d = StuckDetector()
        val h = 777L
        val actions = mutableListOf<StuckDetector.Action>()
        repeat(20) { actions.add(d.submit(h)) }
        val restarts = actions.count { it == StuckDetector.Action.RESTART_APP }
        assertTrue("长期卡死应周期性重试重启，实际 $restarts 次", restarts >= 2)
    }

    @Test
    fun `内容恢复变化后计数清零`() {
        val d = StuckDetector()
        repeat(4) { d.submit(100L) }
        assertTrue(d.consecutiveSame > 0)
        d.submit(200L)
        assertEquals(0, d.consecutiveSame)
    }

    @Test
    fun `无效指纹不计入避免误判`() {
        val d = StuckDetector()
        // root 为 null 时会传 NO_HASH，此时不该累计——否则息屏几秒就误判卡死
        repeat(20) {
            assertEquals(StuckDetector.Action.NONE, d.submit(StuckDetector.NO_HASH))
        }
        assertEquals(0, d.consecutiveSame)
    }

    @Test
    fun `恢复后重新观察不立刻重复触发`() {
        val d = StuckDetector()
        val h = 555L
        repeat(4) { d.submit(h) } // 已触发 CLOSE_POPUP
        d.onRecoveryAttempted()
        // 恢复后第一次提交相当于重新记录基准，不该立刻再触发
        assertEquals(StuckDetector.Action.NONE, d.submit(h))
    }

    @Test
    fun `reset 完全清空状态`() {
        val d = StuckDetector()
        repeat(6) { d.submit(1L) }
        d.reset()
        assertEquals(0, d.consecutiveSame)
        assertEquals(StuckDetector.Action.NONE, d.submit(1L))
    }

    @Test
    fun `自定义阈值生效`() {
        val d = StuckDetector(adBlockAt = 1, backAt = 2, restartAt = 3)
        val h = 88L
        d.submit(h)
        assertEquals(StuckDetector.Action.CLOSE_POPUP, d.submit(h))
        assertEquals(StuckDetector.Action.PRESS_BACK, d.submit(h))
        assertEquals(StuckDetector.Action.RESTART_APP, d.submit(h))
    }
}

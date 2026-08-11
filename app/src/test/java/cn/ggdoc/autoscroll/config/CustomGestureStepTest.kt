package cn.ggdoc.autoscroll.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CustomGestureStep 序列化 / 反序列化测试。
 *
 * 覆盖：往返一致性、畸形输入、越界值 clamp、非法手势过滤、默认值。
 */
class CustomGestureStepTest {

    @Test
    fun `序列化反序列化往返一致`() {
        val steps = listOf(
            CustomGestureStep(CustomGestureStep.TYPE_TAP, waitSec = 3, xPct = 50, yPct = 60, distPct = 30),
            CustomGestureStep(CustomGestureStep.TYPE_SWIPE_UP, waitSec = 5, xPct = 40, yPct = 70, distPct = 80),
            CustomGestureStep(CustomGestureStep.TYPE_WAIT, waitSec = 10)
        )
        val restored = CustomGestureStep.deserialize(CustomGestureStep.serialize(steps))
        assertEquals(steps, restored)
    }

    @Test
    fun `空串与空白返回空列表`() {
        assertEquals(emptyList<CustomGestureStep>(), CustomGestureStep.deserialize(null))
        assertEquals(emptyList<CustomGestureStep>(), CustomGestureStep.deserialize(""))
        assertEquals(emptyList<CustomGestureStep>(), CustomGestureStep.deserialize("   "))
    }

    @Test
    fun `字段不足的条目被跳过`() {
        // 正常 5 字段；不足 5 字段的条目丢弃，但合法条目保留
        val raw = "tap,3,50,50,70;broken;swipe_up,5,40,70,80"
        val list = CustomGestureStep.deserialize(raw)
        assertEquals(2, list.size)
        assertEquals(CustomGestureStep.TYPE_TAP, list[0].gesture)
        assertEquals(CustomGestureStep.TYPE_SWIPE_UP, list[1].gesture)
    }

    @Test
    fun `非数字字段回退默认值`() {
        val list = CustomGestureStep.deserialize("tap,abc,xyz,50,70")
        assertEquals(1, list.size)
        assertEquals(2, list[0].waitSec)          // 默认 2
        assertEquals(50, list[0].xPct)            // 默认 50
        assertEquals(70, list[0].distPct)         // 默认 70
    }

    @Test
    fun `越界值被 clamp 到合法范围`() {
        val list = CustomGestureStep.deserialize("tap,999,1,99,5")
        assertEquals(1, list.size)
        assertEquals(600, list[0].waitSec)        // 上限 600（与反序列化定义一致）
        assertEquals(5, list[0].xPct)             // 下限 5
        assertEquals(95, list[0].yPct)            // 上限 95
        assertEquals(5, list[0].distPct)          // 下限 5
    }

    @Test
    fun `非法手势类型被过滤`() {
        val list = CustomGestureStep.deserialize("tap,2,50,50,70;nonexistent,2,50,50,70;wait,4,50,50,70")
        assertEquals(2, list.size)
        assertEquals(listOf(CustomGestureStep.TYPE_TAP, CustomGestureStep.TYPE_WAIT), list.map { it.gesture })
    }

    @Test
    fun `空序列序列化结果为空串`() {
        assertEquals("", CustomGestureStep.serialize(emptyList()))
    }

    @Test
    fun `isWaitOnly 与 isSwipe 语义正确`() {
        assertTrue(CustomGestureStep(CustomGestureStep.TYPE_WAIT).isWaitOnly())
        assertFalse(CustomGestureStep(CustomGestureStep.TYPE_TAP).isWaitOnly())
        assertTrue(CustomGestureStep(CustomGestureStep.TYPE_SWIPE_LEFT).isSwipe())
        assertFalse(CustomGestureStep(CustomGestureStep.TYPE_TAP).isSwipe())
    }

    @Test
    fun `summary 文案`() {
        val wait = CustomGestureStep(CustomGestureStep.TYPE_WAIT, waitSec = 8)
        assertTrue(wait.summary().contains("8"))
        val tap = CustomGestureStep(CustomGestureStep.TYPE_TAP, waitSec = 3)
        assertTrue(tap.summary().contains("单击"))
        assertTrue(tap.summary().contains("3"))
    }

    // ---------- 点击文本（tap_text）步骤 ----------

    @Test
    fun `点击文本步骤序列化往返`() {
        val steps = listOf(
            CustomGestureStep(CustomGestureStep.TYPE_TAP_TEXT, waitSec = 4, textKeyword = "立即下载"),
            CustomGestureStep(CustomGestureStep.TYPE_TAP, waitSec = 2)
        )
        val restored = CustomGestureStep.deserialize(CustomGestureStep.serialize(steps))
        assertEquals(steps, restored)
        assertTrue(restored[0].isTapText())
        assertFalse(restored[1].isTapText())
    }

    @Test
    fun `旧版 5 字段存档兼容`() {
        // 旧格式没有第 6 字段，textKeyword 应为空，其余字段正常
        val list = CustomGestureStep.deserialize("tap,3,50,50,70")
        assertEquals(1, list.size)
        assertEquals("", list[0].textKeyword)
    }

    @Test
    fun `关键词中的英文逗号分号被转义避免破坏分隔符`() {
        val step = CustomGestureStep(CustomGestureStep.TYPE_TAP_TEXT, waitSec = 2, textKeyword = "a,b;c")
        val raw = CustomGestureStep.serialize(listOf(step))
        // 序列化后字段数仍是 6（逗号/分号已转全角），反序列化不会错位
        val restored = CustomGestureStep.deserialize(raw)
        assertEquals(1, restored.size)
        assertEquals(CustomGestureStep.TYPE_TAP_TEXT, restored[0].gesture)
        assertTrue(restored[0].textKeyword.isNotEmpty())
    }

    @Test
    fun `点击文本步骤摘要展示关键词`() {
        val s = CustomGestureStep(CustomGestureStep.TYPE_TAP_TEXT, waitSec = 3, textKeyword = "领取")
        assertTrue(s.summary().contains("领取"))
        assertTrue(s.summary().contains("3"))
    }
}

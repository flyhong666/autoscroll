package cn.ggdoc.autoscroll.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

/**
 * RecordedAction / RecordedScript 序列化测试（Robolectric：org.json 在纯 JVM 单测不可用）。
 *
 * 覆盖：条件分支字段往返、旧脚本无 condition 字段兼容、readable 展示。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordedActionJsonTest {

    @Test
    fun `条件分支字段序列化往返`() {
        val action = RecordedAction(
            type = RecordedAction.TYPE_CLICK,
            x = 100, y = 200,
            duration = 60L, delay = 500L,
            desc = "点赞",
            condition = "加载完成"
        )
        val restored = RecordedAction.fromJson(action.toJson())
        assertEquals(action, restored)
        assertEquals("加载完成", restored.condition)
    }

    @Test
    fun `无条件步骤不写 condition 字段`() {
        val action = RecordedAction(type = RecordedAction.TYPE_CLICK, x = 1, y = 2)
        val json = action.toJson()
        assertFalse(json.has("condition"))
        assertEquals("", RecordedAction.fromJson(json).condition)
    }

    @Test
    fun `旧脚本缺 condition 字段兼容为空`() {
        val old = JSONObject()
            .put("type", "click")
            .put("x", 10)
            .put("y", 20)
        val restored = RecordedAction.fromJson(old)
        assertEquals("", restored.condition)
        assertEquals(10, restored.x)
    }

    @Test
    fun `readable 展示条件标记`() {
        val withCond = RecordedAction(type = RecordedAction.TYPE_CLICK, x = 1, y = 2, condition = "广告")
        assertTrue(withCond.readable(0).contains("若见「广告」"))
        val plain = RecordedAction(type = RecordedAction.TYPE_CLICK, x = 1, y = 2)
        assertFalse(plain.readable(0).contains("若见"))
    }

    @Test
    fun `完整脚本往返保持条件字段`() {
        val script = RecordedScript(
            name = "测试脚本",
            createdAt = 123456789L,
            pkg = "com.example.app",
            actions = listOf(
                RecordedAction(type = RecordedAction.TYPE_CLICK, x = 1, y = 2, condition = "弹窗出现"),
                RecordedAction(type = RecordedAction.TYPE_WAIT, duration = 1000L)
            )
        )
        val restored = RecordedScript.fromJson(JSONObject(script.toPrettyString()))
        assertEquals(script, restored)
        assertEquals("弹窗出现", restored.actions[0].condition)
    }
}

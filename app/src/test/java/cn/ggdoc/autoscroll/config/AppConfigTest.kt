package cn.ggdoc.autoscroll.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppConfig 配置层测试（Robolectric）。
 *
 * 覆盖：多时段解析与旧键回退、关键词解析（中英文分隔符）、
 * 自定义手势序列旧配置回退、参数校验、轮换间隔 clamp。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppConfigTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `多时段字符串解析正确`() {
        val prefs = context.getSharedPreferences("autoscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("schedule_windows", "480-1200;1320-1380").apply()
        val windows = AppConfig.getScheduleWindows(context)
        assertEquals(listOf(480 to 1200, 1320 to 1380), windows)
    }

    @Test
    fun `多时段解析忽略非法段`() {
        val prefs = context.getSharedPreferences("autoscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("schedule_windows", "480-1200;bad;900").apply()
        assertEquals(listOf(480 to 1200), AppConfig.getScheduleWindows(context))
    }

    @Test
    fun `新键缺失时回退旧版单窗口`() {
        val prefs = context.getSharedPreferences("autoscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("schedule_windows")
            .putInt("schedule_start_min", 600)
            .putInt("schedule_end_min", 1080)
            .apply()
        assertEquals(listOf(600 to 1080), AppConfig.getScheduleWindows(context))
    }

    @Test
    fun `关键词解析支持中英文逗号顿号换行`() {
        val parsed = AppConfig.parseKeywords("跳过,关闭，知道了、暂不\n取消;稍后；No thanks")
        assertEquals(setOf("跳过", "关闭", "知道了", "暂不", "取消", "稍后", "No thanks"), parsed)
    }

    @Test
    fun `空白关键词返回空集合`() {
        assertTrue(AppConfig.parseKeywords("").isEmpty())
        assertTrue(AppConfig.parseKeywords("  ,，、  ").isEmpty())
    }

    @Test
    fun `自定义手势序列为空时回退旧版单手势配置`() {
        val prefs = context.getSharedPreferences("autoscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("custom_gesture_sequence")
            .putString("custom_gesture_type", "swipe_up")
            .putInt("custom_tap_x_pct", 40)
            .putInt("custom_tap_y_pct", 60)
            .putInt("custom_swipe_distance_pct", 80)
            .apply()
        val steps = AppConfig.getCustomGestureSequence(context)
        assertEquals(1, steps.size)
        assertEquals(CustomGestureStep.TYPE_SWIPE_UP, steps[0].gesture)
        assertEquals(40, steps[0].xPct)
        assertEquals(80, steps[0].distPct)
    }

    @Test
    fun `参数校验规则`() {
        assertTrue(AppConfig.validate(3, 20, 300, 500).first)
        assertFalse(AppConfig.validate(0, 20, 300, 500).first)       // 间隔非正
        assertFalse(AppConfig.validate(20, 3, 300, 500).first)       // 最小 ≥ 最大
        assertFalse(AppConfig.validate(3, 20, 500, 300).first)       // 时长倒置
        assertFalse(AppConfig.validate(3, 20, 300, 4000).first)      // 时长过大
    }

    @Test
    fun `轮换间隔被 clamp 防止忙循环`() {
        val prefs = context.getSharedPreferences("autoscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("rotation_minutes", 0).apply()
        assertTrue(AppConfig.getRotationMinutes(context) >= 1)
        prefs.edit().putInt("rotation_minutes", 9999).apply()
        assertTrue(AppConfig.getRotationMinutes(context) <= 24 * 60)
    }

    @Test
    fun `看广告得金币需要开关加风险确认双条件`() {
        val prefs = context.getSharedPreferences("autoscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("ad_reward_enabled", true)
            .putBoolean("ad_reward_risk_acked", false)
            .apply()
        assertFalse("未确认风险时开关无效", AppConfig.isAdReward(context))
        prefs.edit().putBoolean("ad_reward_risk_acked", true).apply()
        assertTrue("双条件满足后生效", AppConfig.isAdReward(context))
    }

    @Test
    fun `方案预设保存读取往返一致`() {
        AppConfig.setMinInterval(context, 3)
        AppConfig.setMaxInterval(context, 20)
        AppConfig.setMinDuration(context, 300)
        AppConfig.setMaxDuration(context, 500)
        AppConfig.setAutoLike(context, true)
        AppConfig.setLikeProbability(context, 40)
        assertTrue(AppConfig.saveCurrentAsPreset(context, "通用方案"))

        val p = AppConfig.getPreset(context, "通用方案")
        assertNotNull(p)
        assertEquals(3, p!!.minInterval)
        assertEquals(20, p.maxInterval)
        assertEquals(300, p.minDuration)
        assertEquals(500, p.maxDuration)
        assertTrue(p.autoLike)
        assertEquals(40, p.likeProbability)
        assertTrue("方案应出现在列表", AppConfig.listPresets(context).contains("通用方案"))
    }

    @Test
    fun `应用方案会写回配置并允许删除`() {
        AppConfig.saveCurrentAsPreset(context, "临时方案")
        // 改乱当前配置，再应用方案
        AppConfig.setMinInterval(context, 1)
        AppConfig.setMaxInterval(context, 2)
        val p = AppConfig.getPreset(context, "临时方案")!!
        AppConfig.applyPreset(context, p.copy(minInterval = 3, maxInterval = 25))
        assertEquals(3, AppConfig.getMinInterval(context))
        assertEquals(25, AppConfig.getMaxInterval(context))

        AppConfig.deletePreset(context, "临时方案")
        assertFalse(AppConfig.listPresets(context).contains("临时方案"))
        assertNull(AppConfig.getPreset(context, "临时方案"))
    }
}

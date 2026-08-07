package cn.ggdoc.autoscroll.human

import cn.ggdoc.autoscroll.config.StatsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * StatsStore 纯逻辑层测试。
 *
 * 只覆盖不带 Android Context 的部分：
 * - Stats 的 plus / isEmpty / formatDuration（纯函数）
 * - dayKey(millis, timeZone) 跨天边界（纯函数，可注入固定时区与毫秒）
 *
 * 涉及 SharedPreferences 的持久化方法需要真实 Context，留待真机/插桩测试。
 */
class StatsStoreTest {

    private val UTC = TimeZone.getTimeZone("UTC")

    /** 构造某天某时刻在指定时区的毫秒数（默认 UTC） */
    private fun millis(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0, s: Int = 0, tz: TimeZone = UTC): Long {
        val cal = Calendar.getInstance(tz).apply {
            set(y, mo - 1, d, h, mi, s)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // ---------- Stats.plus ----------

    @Test
    fun plusAddsEveryField() {
        val a = StatsStore.Stats(scrolls = 1, likes = 2, adBlocks = 3, adRewards = 4, details = 5, seconds = 6L)
        val b = StatsStore.Stats(scrolls = 10, likes = 20, adBlocks = 30, adRewards = 40, details = 50, seconds = 60L)
        val r = a + b
        assertEquals(11, r.scrolls)
        assertEquals(22, r.likes)
        assertEquals(33, r.adBlocks)
        assertEquals(44, r.adRewards)
        assertEquals(55, r.details)
        assertEquals(66L, r.seconds)
    }

    @Test
    fun plusWithZeroIsIdentity() {
        val a = StatsStore.Stats(scrolls = 7, seconds = 100L)
        assertEquals(a, a + StatsStore.Stats())
    }

    @Test
    fun plusIsAssociative() {
        val a = StatsStore.Stats(scrolls = 1, seconds = 10L)
        val b = StatsStore.Stats(scrolls = 2, seconds = 20L)
        val c = StatsStore.Stats(scrolls = 3, seconds = 30L)
        assertEquals((a + b) + c, a + (b + c))
    }

    // ---------- Stats.isEmpty ----------

    @Test
    fun emptyWhenAllZero() {
        assertTrue(StatsStore.Stats().isEmpty)
    }

    @Test
    fun notEmptyWhenAnyNonZero() {
        assertFalse(StatsStore.Stats(scrolls = 1).isEmpty)
        assertFalse(StatsStore.Stats(likes = 1).isEmpty)
        assertFalse(StatsStore.Stats(adBlocks = 1).isEmpty)
        assertFalse(StatsStore.Stats(adRewards = 1).isEmpty)
        assertFalse(StatsStore.Stats(details = 1).isEmpty)
        assertFalse(StatsStore.Stats(seconds = 1L).isEmpty)
    }

    // ---------- Stats.formatDuration ----------

    @Test
    fun formatDurationZero() {
        assertEquals("0秒", StatsStore.Stats().formatDuration())
    }

    @Test
    fun formatDurationSecondsOnly() {
        assertEquals("5秒", StatsStore.Stats(seconds = 5L).formatDuration())
    }

    @Test
    fun formatDurationMinutesAndSeconds() {
        assertEquals("1分5秒", StatsStore.Stats(seconds = 65L).formatDuration())
    }

    @Test
    fun formatDurationHoursAndMinutes() {
        assertEquals("1小时1分", StatsStore.Stats(seconds = 3661L).formatDuration())
    }

    @Test
    fun formatDurationExactHour() {
        assertEquals("1小时0分", StatsStore.Stats(seconds = 3600L).formatDuration())
    }

    @Test
    fun formatDurationLarge() {
        assertEquals("2小时0分", StatsStore.Stats(seconds = 7200L).formatDuration())
    }

    // ---------- dayKey 跨天边界 ----------

    @Test
    fun dayKeyMidDay() {
        assertEquals(20260807, StatsStore.dayKey(millis(2026, 8, 7, 12, 0, 0), UTC))
    }

    @Test
    fun dayKeyJustBeforeMidnight() {
        // 23:59:59.999 仍属当天
        val t = millis(2026, 8, 7, 23, 59, 59) + 999L
        assertEquals(20260807, StatsStore.dayKey(t, UTC))
    }

    @Test
    fun dayKeyAtMidnightNextDay() {
        // 00:00:00.000 已是次日
        val t = millis(2026, 8, 8, 0, 0, 0)
        assertEquals(20260808, StatsStore.dayKey(t, UTC))
    }

    @Test
    fun dayKeyCrossMonth() {
        assertEquals(20260301, StatsStore.dayKey(millis(2026, 3, 1, 0, 0, 0), UTC))
        assertEquals(20260331, StatsStore.dayKey(millis(2026, 3, 31, 23, 0, 0), UTC))
        assertEquals(20260401, StatsStore.dayKey(millis(2026, 4, 1, 0, 0, 0), UTC))
    }

    @Test
    fun dayKeyCrossYear() {
        assertEquals(20251231, StatsStore.dayKey(millis(2025, 12, 31, 23, 59, 59), UTC))
        assertEquals(20260101, StatsStore.dayKey(millis(2026, 1, 1, 0, 0, 0), UTC))
    }

    @Test
    fun dayKeyLeadingZeroMonthDay() {
        // 1月5日 -> 20260105（月/日补零，避免 202615 这类歧义）
        assertEquals(20260105, StatsStore.dayKey(millis(2026, 1, 5, 10, 0, 0), UTC))
    }

    @Test
    fun dayKeyDifferentTimeZoneSameInstant() {
        // 同一瞬间在 UTC+8 可能是次日，在 UTC 仍是当天
        val instant = millis(2026, 8, 8, 2, 0, 0, UTC) // UTC 02:00 = 北京 10:00
        assertEquals(20260808, StatsStore.dayKey(instant, UTC))
        assertEquals(20260808, StatsStore.dayKey(instant, TimeZone.getTimeZone("Asia/Shanghai")))
    }
}

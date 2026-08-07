package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 定时运行时间计算的单元测试。
 *
 * 跨午夜窗口是这类代码最经典的 bug 源——
 * 「22:00 到次日 06:00」用常规的 start<=now<=end 判断会恒为 false。
 */
class ScheduleUtilsTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /** 构造指定日期时刻的毫秒时间戳 */
    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    // ---------- isWithinWindow ----------

    @Test
    fun `常规窗口内判定正确`() {
        // 08:00 ~ 20:00
        assertTrue(ScheduleUtils.isWithinWindow(600, 480, 1200))   // 10:00
        assertTrue(ScheduleUtils.isWithinWindow(480, 480, 1200))   // 边界 08:00
        assertTrue(ScheduleUtils.isWithinWindow(1200, 480, 1200))  // 边界 20:00
    }

    @Test
    fun `常规窗口外判定正确`() {
        assertFalse(ScheduleUtils.isWithinWindow(300, 480, 1200))   // 05:00
        assertFalse(ScheduleUtils.isWithinWindow(1300, 480, 1200))  // 21:40
    }

    @Test
    fun `跨午夜窗口内判定正确`() {
        // 22:00 ~ 次日 06:00
        val start = 22 * 60
        val end = 6 * 60
        assertTrue("23:00 应在窗口内", ScheduleUtils.isWithinWindow(23 * 60, start, end))
        assertTrue("00:30 应在窗口内", ScheduleUtils.isWithinWindow(30, start, end))
        assertTrue("05:59 应在窗口内", ScheduleUtils.isWithinWindow(5 * 60 + 59, start, end))
        assertTrue("边界 22:00", ScheduleUtils.isWithinWindow(start, start, end))
        assertTrue("边界 06:00", ScheduleUtils.isWithinWindow(end, start, end))
    }

    @Test
    fun `跨午夜窗口外判定正确`() {
        val start = 22 * 60
        val end = 6 * 60
        assertFalse("12:00 不该在窗口内", ScheduleUtils.isWithinWindow(12 * 60, start, end))
        assertFalse("21:59 不该在窗口内", ScheduleUtils.isWithinWindow(21 * 60 + 59, start, end))
        assertFalse("06:01 不该在窗口内", ScheduleUtils.isWithinWindow(6 * 60 + 1, start, end))
    }

    @Test
    fun `起止相同视为全天生效`() {
        // 反直觉行为规避：不该变成「只有一分钟能跑」
        for (m in 0 until 1440 step 97) {
            assertTrue("$m 分钟应在全天窗口内", ScheduleUtils.isWithinWindow(m, 480, 480))
        }
    }

    @Test
    fun `越界分钟数被规范化`() {
        // 1500 分钟 = 次日 01:00
        assertTrue(ScheduleUtils.isWithinWindow(1500, 22 * 60, 6 * 60))
        assertFalse(ScheduleUtils.isWithinWindow(-60, 8 * 60, 20 * 60)) // -60 => 23:00
    }

    // ---------- nextAlarmMillis ----------

    @Test
    fun `今日尚未到达时安排在今天`() {
        val now = millisAt(2026, 8, 7, 6, 0)
        val next = ScheduleUtils.nextAlarmMillis(8 * 60, now, tz)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `今日已过时顺延到明天`() {
        val now = millisAt(2026, 8, 7, 10, 0)
        val next = ScheduleUtils.nextAlarmMillis(8 * 60, now, tz)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals("应顺延到 8 号", 8, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `恰好等于当前时刻时顺延到明天`() {
        // 若不顺延，闹钟会立刻触发形成抖动
        val now = millisAt(2026, 8, 7, 8, 0)
        val next = ScheduleUtils.nextAlarmMillis(8 * 60, now, tz)
        assertTrue("必须严格晚于当前时刻", next > now)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(8, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `跨月边界正确顺延`() {
        val now = millisAt(2026, 8, 31, 23, 30)
        val next = ScheduleUtils.nextAlarmMillis(8 * 60, now, tz)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals("应进入 9 月", Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `秒与毫秒被清零避免漂移`() {
        val now = millisAt(2026, 8, 7, 6, 0) + 37_531L
        val next = ScheduleUtils.nextAlarmMillis(8 * 60, now, tz)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    // ---------- nowMinute / formatMinute ----------

    @Test
    fun `当前分钟计算正确`() {
        val now = millisAt(2026, 8, 7, 14, 35)
        assertEquals(14 * 60 + 35, ScheduleUtils.nowMinute(now, tz))
    }

    @Test
    fun `时间格式化正确补零`() {
        assertEquals("00:00", ScheduleUtils.formatMinute(0))
        assertEquals("08:05", ScheduleUtils.formatMinute(485))
        assertEquals("23:59", ScheduleUtils.formatMinute(1439))
    }

    @Test
    fun `格式化时越界分钟被规范化`() {
        assertEquals("00:00", ScheduleUtils.formatMinute(1440))
        assertEquals("01:00", ScheduleUtils.formatMinute(1500))
        assertEquals("23:00", ScheduleUtils.formatMinute(-60))
    }
}

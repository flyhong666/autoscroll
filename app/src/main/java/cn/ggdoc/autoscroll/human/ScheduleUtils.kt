package cn.ggdoc.autoscroll.human

import java.util.Calendar
import java.util.TimeZone

/**
 * 定时运行相关的纯时间计算。
 *
 * 从 `AutoScrollAccessibilityService` 里抽出来的原因：
 * 这些是**纯逻辑且容易算错**的代码（尤其跨午夜窗口），
 * 但原先是 service 的 private 方法，没法写单元测试。
 * 抽成 object 后可以直接在 JVM 上验证边界条件。
 */
object ScheduleUtils {

    /** 一天的分钟总数 */
    const val MINUTES_PER_DAY = 24 * 60

    /**
     * 计算下一次触发时刻（毫秒时间戳）。
     * 今天的该时刻若已过去，则顺延到明天。
     *
     * @param targetMin 目标时刻，以「当天第几分钟」表示（如 8:30 = 510）
     * @param nowMillis 当前时间，便于测试注入
     */
    fun nextAlarmMillis(
        targetMin: Int,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val m = targetMin.mod(MINUTES_PER_DAY)
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, m / 60)
            set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 判断某个时刻是否落在运行窗口内，**支持跨午夜**。
     *
     * 跨午夜是最容易写错的地方：当 start=22:00、end=06:00 时，
     * 条件必须是「now >= start 或 now <= end」，
     * 而不是常规的「start <= now <= end」（后者恒为 false）。
     *
     * @param nowMin 当前时刻（当天第几分钟）
     */
    fun isWithinWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean {
        val now = nowMin.mod(MINUTES_PER_DAY)
        val s = startMin.mod(MINUTES_PER_DAY)
        val e = endMin.mod(MINUTES_PER_DAY)
        // 起止相同视为「全天生效」，避免出现只有一分钟的窗口这种反直觉行为
        if (s == e) return true
        return if (s < e) now in s..e else (now >= s || now <= e)
    }

    /** 取当前时刻对应的「当天第几分钟」 */
    fun nowMinute(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** 格式化为 HH:mm */
    fun formatMinute(min: Int): String {
        val m = min.mod(MINUTES_PER_DAY)
        return String.format("%02d:%02d", m / 60, m % 60)
    }
}

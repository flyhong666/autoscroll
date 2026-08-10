package cn.ggdoc.autoscroll.config

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.TimeZone

/**
 * 运行统计持久化。
 *
 * 解决的问题：原实现的 4 个计数器（滚动/点赞/广告/激励）都是
 * `companion object` 里的内存变量，且 `startScrolling()` 一上来就清零。
 * 后果是：
 * - 无障碍服务被系统回收 -> 数据全丢
 * - 用户关掉 App 再打开 -> 一切归零
 * - 「今天一共刷了多少」这个用户最想看的数字，根本不存在
 *
 * 这里落到 SharedPreferences，维护「今日」与「累计」两组数据，
 * 跨天自动滚动归档（今日清零，累计保留）。
 */
object StatsStore {

    private const val PREFS_NAME = "autoscroll_stats"

    private const val KEY_DAY = "stats_day"

    // 今日
    private const val KEY_TODAY_SCROLLS = "today_scrolls"
    private const val KEY_TODAY_LIKES = "today_likes"
    private const val KEY_TODAY_AD_BLOCKS = "today_ad_blocks"
    private const val KEY_TODAY_AD_REWARDS = "today_ad_rewards"
    private const val KEY_TODAY_DETAILS = "today_details"
    private const val KEY_TODAY_SECONDS = "today_seconds"

    // 累计
    private const val KEY_TOTAL_SCROLLS = "total_scrolls"
    private const val KEY_TOTAL_LIKES = "total_likes"
    private const val KEY_TOTAL_AD_BLOCKS = "total_ad_blocks"
    private const val KEY_TOTAL_AD_REWARDS = "total_ad_rewards"
    private const val KEY_TOTAL_DETAILS = "total_details"
    private const val KEY_TOTAL_SECONDS = "total_seconds"

    /** 一组统计数值 */
    data class Stats(
        val scrolls: Int = 0,
        val likes: Int = 0,
        val adBlocks: Int = 0,
        val adRewards: Int = 0,
        val details: Int = 0,
        val seconds: Long = 0L
    ) {
        /** 累加（纯函数，便于单元测试） */
        operator fun plus(o: Stats) = Stats(
            scrolls = scrolls + o.scrolls,
            likes = likes + o.likes,
            adBlocks = adBlocks + o.adBlocks,
            adRewards = adRewards + o.adRewards,
            details = details + o.details,
            seconds = seconds + o.seconds
        )

        val isEmpty: Boolean
            get() = scrolls == 0 && likes == 0 && adBlocks == 0 &&
                    adRewards == 0 && details == 0 && seconds == 0L

        /** 运行时长的人类可读格式 */
        fun formatDuration(): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) "${h}小时${m}分" else if (m > 0) "${m}分${s}秒" else "${s}秒"
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 生成「日期键」，形如 20260807。
     *
     * 纯函数，抽出来是为了能单独测试跨天判定——
     * 这类基于本地时区的日期计算很容易在夏令时/跨时区场景下出错。
     */
    fun dayKey(
        millis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return cal.get(Calendar.YEAR) * 10000 +
                (cal.get(Calendar.MONTH) + 1) * 100 +
                cal.get(Calendar.DAY_OF_MONTH)
    }

    /**
     * 跨天检查：若存储的日期不是今天，把「今日」清零。
     * 累计数据不受影响。
     */
    private fun rollOverIfNeeded(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val today = dayKey(nowMillis)
        if (p.getInt(KEY_DAY, 0) == today) return
        p.edit()
            .putInt(KEY_DAY, today)
            .putInt(KEY_TODAY_SCROLLS, 0)
            .putInt(KEY_TODAY_LIKES, 0)
            .putInt(KEY_TODAY_AD_BLOCKS, 0)
            .putInt(KEY_TODAY_AD_REWARDS, 0)
            .putInt(KEY_TODAY_DETAILS, 0)
            .putLong(KEY_TODAY_SECONDS, 0L)
            .apply()
    }

    /**
     * 累加一次增量（本次运行产生的数据）。
     *
     * 调用时机：停止滚动时、以及运行期间每分钟落盘一次——
     * 后者是为了防止服务被系统杀死导致整段数据丢失。
     */
    fun accumulate(context: Context, delta: Stats, nowMillis: Long = System.currentTimeMillis()) {
        if (delta.isEmpty) return
        rollOverIfNeeded(context, nowMillis)
        val p = prefs(context)
        val e = p.edit()
        // 今日 / 累计 共用同一组增量，仅 key 前缀不同
        val intFields = listOf(
            KEY_TODAY_SCROLLS to delta.scrolls,
            KEY_TODAY_LIKES to delta.likes,
            KEY_TODAY_AD_BLOCKS to delta.adBlocks,
            KEY_TODAY_AD_REWARDS to delta.adRewards,
            KEY_TODAY_DETAILS to delta.details,
            KEY_TOTAL_SCROLLS to delta.scrolls,
            KEY_TOTAL_LIKES to delta.likes,
            KEY_TOTAL_AD_BLOCKS to delta.adBlocks,
            KEY_TOTAL_AD_REWARDS to delta.adRewards,
            KEY_TOTAL_DETAILS to delta.details
        )
        for ((key, value) in intFields) {
            e.putInt(key, p.getInt(key, 0) + value)
        }
        e.putLong(KEY_TODAY_SECONDS, p.getLong(KEY_TODAY_SECONDS, 0L) + delta.seconds)
        e.putLong(KEY_TOTAL_SECONDS, p.getLong(KEY_TOTAL_SECONDS, 0L) + delta.seconds)
        e.apply()
    }

    /** 今日统计 */
    fun today(context: Context): Stats {
        rollOverIfNeeded(context)
        val p = prefs(context)
        return Stats(
            scrolls = p.getInt(KEY_TODAY_SCROLLS, 0),
            likes = p.getInt(KEY_TODAY_LIKES, 0),
            adBlocks = p.getInt(KEY_TODAY_AD_BLOCKS, 0),
            adRewards = p.getInt(KEY_TODAY_AD_REWARDS, 0),
            details = p.getInt(KEY_TODAY_DETAILS, 0),
            seconds = p.getLong(KEY_TODAY_SECONDS, 0L)
        )
    }

    /** 历史累计统计 */
    fun total(context: Context): Stats {
        val p = prefs(context)
        return Stats(
            scrolls = p.getInt(KEY_TOTAL_SCROLLS, 0),
            likes = p.getInt(KEY_TOTAL_LIKES, 0),
            adBlocks = p.getInt(KEY_TOTAL_AD_BLOCKS, 0),
            adRewards = p.getInt(KEY_TOTAL_AD_REWARDS, 0),
            details = p.getInt(KEY_TOTAL_DETAILS, 0),
            seconds = p.getLong(KEY_TOTAL_SECONDS, 0L)
        )
    }

    /** 清空全部统计（供设置页「重置数据」使用） */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

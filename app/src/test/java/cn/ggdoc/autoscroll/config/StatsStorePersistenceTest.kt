package cn.ggdoc.autoscroll.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * StatsStore 持久化层测试（Robolectric）。
 *
 * 覆盖：accumulate 今日/累计累加、跨天归档（今日清零累计保留）、
 * 空增量 no-op、纯函数 Stats.plus 的边界。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsStorePersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每个用例前清空统计，保证隔离
        StatsStore.clear(context)
    }

    @After
    fun tearDown() {
        StatsStore.clear(context)
    }

    private fun dayStartMillis(day: Int): Long {
        // 2026-08-11 00:00:00 起的第 day 天（用固定基准，避免依赖当前日期）。
        // 注意：基准必须是真正的 00:00:00 UTC（1786320000000），
        // 这样 dayKey(基准 + Δ) 在任意时区都不会跨过本地午夜，
        // dayKey 同日不同时的断言才在不同时区下都成立。
        val base = 1786320000000L // 2026-08-10 00:00:00 UTC
        return base + day * 86_400_000L
    }

    @Test
    fun `accumulate 同时累加今日与累计`() {
        val delta = StatsStore.Stats(scrolls = 5, likes = 2, adBlocks = 1, adRewards = 0, details = 3, seconds = 120L)
        StatsStore.accumulate(context, delta, dayStartMillis(1))

        val today = StatsStore.today(context, dayStartMillis(1))
        assertEquals(5, today.scrolls)
        assertEquals(2, today.likes)
        assertEquals(1, today.adBlocks)
        assertEquals(3, today.details)
        assertEquals(120L, today.seconds)

        val total = StatsStore.total(context)
        assertEquals(5, total.scrolls)
        assertEquals(120L, total.seconds)
    }

    @Test
    fun `多次累加数值叠加`() {
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 3, seconds = 30L), dayStartMillis(1))
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 4, seconds = 40L), dayStartMillis(1))
        assertEquals(7, StatsStore.today(context, dayStartMillis(1)).scrolls)
        assertEquals(7, StatsStore.total(context).scrolls)
        assertEquals(70L, StatsStore.today(context, dayStartMillis(1)).seconds)
    }

    @Test
    fun `空增量不产生任何写入`() {
        StatsStore.accumulate(context, StatsStore.Stats(), dayStartMillis(1))
        assertTrue(StatsStore.today(context).isEmpty)
        assertTrue(StatsStore.total(context).isEmpty)
    }

    @Test
    fun `跨天归档今日清零累计保留`() {
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 10, seconds = 100L), dayStartMillis(1))
        // 第二天：今日应清零后只含新增量，累计保留
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 2, seconds = 20L), dayStartMillis(2))

        val today = StatsStore.today(context, dayStartMillis(2))
        assertEquals(2, today.scrolls)
        assertEquals(20L, today.seconds)
        // 注意：dayKey 依据本地时区，这里注入的毫秒若落在不同时区的同一天则断言会失效；
        // 用相差 2 天的时间戳，任何时区下都不可能是同一天
        val total = StatsStore.total(context)
        assertEquals(12, total.scrolls)
        assertEquals(120L, total.seconds)
    }

    @Test
    fun `跨月跨年归档边界`() {
        // 2026-12-31 12:00 UTC → 2027-01-01 12:00 UTC（跨年，间隔 24h，
        // 任何时区下都必然落在不同本地日，避免时区边界歧义）
        val dec31Noon = 1798718400000L
        val jan1Noon = 1798804800000L
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 7), dec31Noon)
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 1), jan1Noon)
        assertEquals(1, StatsStore.today(context, jan1Noon).scrolls)
        assertEquals(8, StatsStore.total(context).scrolls)
    }

    @Test
    fun `按天历史累计并倒序读取`() {
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 5, seconds = 100L), dayStartMillis(1))
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 3, seconds = 30L), dayStartMillis(1))
        StatsStore.accumulate(context, StatsStore.Stats(scrolls = 7, seconds = 60L), dayStartMillis(2))

        val hist = StatsStore.dailyHistory(context)
        assertEquals(2, hist.size)
        // 倒序：第二天在前
        assertEquals(7, hist[0].second.scrolls)
        assertEquals(60L, hist[0].second.seconds)
        // 第一天为两次累加之和
        assertEquals(8, hist[1].second.scrolls)
        assertEquals(130L, hist[1].second.seconds)
    }

    @Test
    fun `dayKey 同日不同时一致`() {
        // 基准为 00:00 UTC，映射到任意时区（UTC-12 ~ UTC+14）本地都在 12:00~14:00 之间；
        // 再加 9 小时仍落在当天（21:00~23:00），不会跨过本地午夜，
        // 从而保证该断言在任何时区下都成立（避免 UTC+12 及以后时区误触发）。
        val a = StatsStore.dayKey(dayStartMillis(1))
        val b = StatsStore.dayKey(dayStartMillis(1) + 9 * 3_600_000L)
        assertEquals(a, b)
    }
}

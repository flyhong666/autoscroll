package cn.ggdoc.autoscroll.config

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用配置管理，使用 SharedPreferences 持久化参数
 */
object AppConfig {

    private const val PREFS_NAME = "autoscroll_prefs"

    // ===== 基础滑动参数 =====
    private const val KEY_MIN_INTERVAL = "min_interval_seconds"
    private const val KEY_MAX_INTERVAL = "max_interval_seconds"
    private const val KEY_MIN_DURATION = "min_duration_ms"
    private const val KEY_MAX_DURATION = "max_duration_ms"

    // ===== 场景 =====
    private const val KEY_CURRENT_SCENE = "current_scene"

    // ===== 任务调度 =====
    private const val KEY_AUTO_LIKE = "auto_like"
    private const val KEY_LIKE_PROBABILITY = "like_probability"
    private const val KEY_AD_BLOCK = "ad_block"
    private const val KEY_TIMED_STOP = "timed_stop"
    private const val KEY_TIMED_STOP_MINUTES = "timed_stop_minutes"
    private const val KEY_APP_ROTATION = "app_rotation"
    private const val KEY_ROTATION_MINUTES = "rotation_minutes"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_ALLOWED_APPS = "allowed_apps"
    private const val KEY_AD_KEYWORDS = "ad_block_keywords"

    // ===== 看广告得金币（高风险独立任务） =====
    private const val KEY_AD_REWARD = "ad_reward_enabled"
    private const val KEY_AD_REWARD_ACK = "ad_reward_risk_acked"
    private const val KEY_AD_REWARD_INTERVAL = "ad_reward_interval_minutes"
    private const val KEY_AD_REWARD_KEYWORDS = "ad_reward_keywords"

    // ===== 定时运行 / 保护 =====
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_SCHEDULE_START_MIN = "schedule_start_min"
    private const val KEY_SCHEDULE_END_MIN = "schedule_end_min"
    private const val KEY_BATTERY_GUARD = "battery_guard"
    private const val KEY_BATTERY_THRESHOLD = "battery_threshold"
    private const val KEY_WIFI_ONLY = "wifi_only"

    // ===== 详情流（新闻 / 社交场景：点开→浏览→返回） =====
    private const val KEY_DETAIL_DWELL_MIN = "detail_dwell_min_seconds"
    private const val KEY_DETAIL_DWELL_MAX = "detail_dwell_max_seconds"
    private const val KEY_DETAIL_READ_ALL_PROBABILITY = "detail_read_all_probability"
    private const val KEY_DETAIL_MAX_SCROLLS = "detail_max_scrolls"

    // ===== 默认值 =====
    const val DEFAULT_MIN_INTERVAL = 3
    const val DEFAULT_MAX_INTERVAL = 20
    const val DEFAULT_MIN_DURATION = 300
    const val DEFAULT_MAX_DURATION = 500
    const val DEFAULT_AUTO_LIKE = false
    const val DEFAULT_LIKE_PROBABILITY = 30
    const val DEFAULT_AD_BLOCK = true
    const val DEFAULT_TIMED_STOP = false
    const val DEFAULT_TIMED_STOP_MINUTES = 30
    const val DEFAULT_APP_ROTATION = false
    const val DEFAULT_ROTATION_MINUTES = 10
    const val DEFAULT_KEEP_SCREEN_ON = true
    const val DEFAULT_SCHEDULE_ENABLED = false
    const val DEFAULT_SCHEDULE_START_MIN = 480        // 08:00
    const val DEFAULT_SCHEDULE_END_MIN = 1200         // 20:00
    const val DEFAULT_BATTERY_GUARD = false
    const val DEFAULT_BATTERY_THRESHOLD = 15          // %
    const val DEFAULT_WIFI_ONLY = false
    const val DEFAULT_AD_REWARD = false
    const val DEFAULT_AD_REWARD_INTERVAL = 5          // 分钟
    const val MIN_AD_REWARD_INTERVAL = 2
    const val MAX_AD_REWARD_INTERVAL = 60

    // 详情流默认值
    const val DEFAULT_DETAIL_DWELL_MIN = 6            // 秒：详情页最短停留
    const val DEFAULT_DETAIL_DWELL_MAX = 30           // 秒：详情页最长停留
    const val DEFAULT_DETAIL_READ_ALL_PROBABILITY = 60 // %：进入详情页后「完整读完」的概率
    const val DEFAULT_DETAIL_MAX_SCROLLS = 8          // 单篇详情最多滚动次数
    const val MAX_DETAIL_MAX_SCROLLS = 20

    /** 「看广告得金币」入口默认关键词（可在任务页自行增删） */
    val DEFAULT_AD_REWARD_KEYWORDS: Set<String> = setOf(
        "看广告得金币", "看视频得金币", "看广告赚金币", "看视频赚金币",
        "看广告", "看视频领", "领金币", "得金币", "赚金币",
        "免费领取", "观看视频", "领取奖励", "翻倍领取", "视频翻倍"
    )

    /** 广告屏蔽默认关键词（原写死在 SceneConfig.AD_BLOCK_KEYWORDS，现已可设置） */
    val DEFAULT_AD_KEYWORDS: Set<String> = setOf(
        "跳过", "关闭", "广告", "立即领取", "知道了", "确定",
        "Skip", "skip", "Close", "close", "Ad", "Got it",
        "不再提醒", "暂不", "取消", "稍后", "No thanks"
    )

    // ===== 场景 ID =====
    const val SCENE_SHORT_VIDEO = "short_video"
    const val SCENE_NEWS = "news"
    const val SCENE_NOVEL = "novel"
    const val SCENE_SOCIAL = "social"
    const val SCENE_LIVE = "live"
    const val SCENE_CUSTOM = "custom"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- 基础参数 ----------
    fun getMinInterval(context: Context): Int =
        prefs(context).getInt(KEY_MIN_INTERVAL, DEFAULT_MIN_INTERVAL)
    fun setMinInterval(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_MIN_INTERVAL, value).apply()

    fun getMaxInterval(context: Context): Int =
        prefs(context).getInt(KEY_MAX_INTERVAL, DEFAULT_MAX_INTERVAL)
    fun setMaxInterval(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_MAX_INTERVAL, value).apply()

    fun getMinDuration(context: Context): Int =
        prefs(context).getInt(KEY_MIN_DURATION, DEFAULT_MIN_DURATION)
    fun setMinDuration(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_MIN_DURATION, value).apply()

    fun getMaxDuration(context: Context): Int =
        prefs(context).getInt(KEY_MAX_DURATION, DEFAULT_MAX_DURATION)
    fun setMaxDuration(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_MAX_DURATION, value).apply()

    // ---------- 场景 ----------
    fun getCurrentScene(context: Context): String =
        prefs(context).getString(KEY_CURRENT_SCENE, SCENE_SHORT_VIDEO) ?: SCENE_SHORT_VIDEO
    fun setCurrentScene(context: Context, scene: String) =
        prefs(context).edit().putString(KEY_CURRENT_SCENE, scene).apply()

    // ---------- 高级功能开关 ----------
    fun isAutoLike(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_LIKE, DEFAULT_AUTO_LIKE)
    fun setAutoLike(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_LIKE, value).apply()

    fun getLikeProbability(context: Context): Int =
        prefs(context).getInt(KEY_LIKE_PROBABILITY, DEFAULT_LIKE_PROBABILITY)
    fun setLikeProbability(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_LIKE_PROBABILITY, value).apply()

    fun isAdBlock(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AD_BLOCK, DEFAULT_AD_BLOCK)
    fun setAdBlock(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AD_BLOCK, value).apply()

    fun isTimedStop(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIMED_STOP, DEFAULT_TIMED_STOP)
    fun setTimedStop(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_TIMED_STOP, value).apply()

    fun getTimedStopMinutes(context: Context): Int =
        prefs(context).getInt(KEY_TIMED_STOP_MINUTES, DEFAULT_TIMED_STOP_MINUTES)
    fun setTimedStopMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_TIMED_STOP_MINUTES, value).apply()

    fun isAppRotation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_ROTATION, DEFAULT_APP_ROTATION)
    fun setAppRotation(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_APP_ROTATION, value).apply()

    fun getRotationMinutes(context: Context): Int =
        prefs(context).getInt(KEY_ROTATION_MINUTES, DEFAULT_ROTATION_MINUTES)
    fun setRotationMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_ROTATION_MINUTES, value).apply()

    fun isKeepScreenOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)
    fun setKeepScreenOn(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    // ---------- 定时运行 ----------
    fun isScheduleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCHEDULE_ENABLED, DEFAULT_SCHEDULE_ENABLED)
    fun setScheduleEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    fun getScheduleStartMin(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_START_MIN, DEFAULT_SCHEDULE_START_MIN)
    fun setScheduleStartMin(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_START_MIN, value).apply()

    fun getScheduleEndMin(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_END_MIN, DEFAULT_SCHEDULE_END_MIN)
    fun setScheduleEndMin(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_END_MIN, value).apply()

    // ---------- 电量保护 ----------
    fun isBatteryGuard(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BATTERY_GUARD, DEFAULT_BATTERY_GUARD)
    fun setBatteryGuard(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BATTERY_GUARD, value).apply()

    fun getBatteryThreshold(context: Context): Int =
        prefs(context).getInt(KEY_BATTERY_THRESHOLD, DEFAULT_BATTERY_THRESHOLD)
    fun setBatteryThreshold(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_BATTERY_THRESHOLD, value).apply()

    // ---------- 仅 Wi-Fi ----------
    fun isWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)
    fun setWifiOnly(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    // ---------- 详情流（新闻 / 社交） ----------
    fun getDetailDwellMin(context: Context): Int =
        prefs(context).getInt(KEY_DETAIL_DWELL_MIN, DEFAULT_DETAIL_DWELL_MIN)
    fun setDetailDwellMin(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_DETAIL_DWELL_MIN, value).apply()

    fun getDetailDwellMax(context: Context): Int =
        prefs(context).getInt(KEY_DETAIL_DWELL_MAX, DEFAULT_DETAIL_DWELL_MAX)
    fun setDetailDwellMax(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_DETAIL_DWELL_MAX, value).apply()

    fun getDetailReadAllProbability(context: Context): Int =
        prefs(context).getInt(KEY_DETAIL_READ_ALL_PROBABILITY, DEFAULT_DETAIL_READ_ALL_PROBABILITY)
    fun setDetailReadAllProbability(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_DETAIL_READ_ALL_PROBABILITY, value).apply()

    fun getDetailMaxScrolls(context: Context): Int =
        prefs(context).getInt(KEY_DETAIL_MAX_SCROLLS, DEFAULT_DETAIL_MAX_SCROLLS)
            .coerceIn(1, MAX_DETAIL_MAX_SCROLLS)
    fun setDetailMaxScrolls(context: Context, value: Int) =
        prefs(context).edit()
            .putInt(KEY_DETAIL_MAX_SCROLLS, value.coerceIn(1, MAX_DETAIL_MAX_SCROLLS))
            .apply()

    fun getAllowedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

    fun setAllowedApps(context: Context, apps: Set<String>) =
        prefs(context).edit().putStringSet(KEY_ALLOWED_APPS, LinkedHashSet(apps)).apply()

    // ---------- 广告屏蔽关键词 ----------
    fun getAdKeywords(context: Context): Set<String> {
        val def = DEFAULT_AD_KEYWORDS.joinToString(",")
        val raw = prefs(context).getString(KEY_AD_KEYWORDS, def).orEmpty()
        return parseKeywords(raw)
    }

    fun setAdKeywords(context: Context, keywords: Set<String>) =
        prefs(context).edit()
            .putString(KEY_AD_KEYWORDS, keywords.joinToString(","))
            .apply()

    // ---------- 看广告得金币（高风险） ----------
    /** 只有「已确认风险」且「开关打开」才算真正开启 */
    fun isAdReward(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AD_REWARD, DEFAULT_AD_REWARD) && isAdRewardAcked(context)

    fun setAdReward(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AD_REWARD, value).apply()

    /** 仅读取「开关」原始值（不受风险确认影响），用于任务页还原 UI */
    fun getAdRewardEnabledRaw(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AD_REWARD, DEFAULT_AD_REWARD)

    /** 用户是否已阅读并同意封号风险提示 */
    fun isAdRewardAcked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AD_REWARD_ACK, false)

    fun setAdRewardAcked(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AD_REWARD_ACK, value).apply()

    fun getAdRewardInterval(context: Context): Int =
        prefs(context).getInt(KEY_AD_REWARD_INTERVAL, DEFAULT_AD_REWARD_INTERVAL)
            .coerceIn(MIN_AD_REWARD_INTERVAL, MAX_AD_REWARD_INTERVAL)

    fun setAdRewardInterval(context: Context, value: Int) =
        prefs(context).edit()
            .putInt(KEY_AD_REWARD_INTERVAL, value.coerceIn(MIN_AD_REWARD_INTERVAL, MAX_AD_REWARD_INTERVAL))
            .apply()

    fun getAdRewardKeywords(context: Context): Set<String> {
        val def = DEFAULT_AD_REWARD_KEYWORDS.joinToString(",")
        return parseKeywords(prefs(context).getString(KEY_AD_REWARD_KEYWORDS, def).orEmpty())
    }

    fun setAdRewardKeywords(context: Context, keywords: Set<String>) =
        prefs(context).edit()
            .putString(KEY_AD_REWARD_KEYWORDS, keywords.joinToString(","))
            .apply()

    /** 解析用户输入的关键词：支持逗号(中/英)、顿号、换行分隔 */
    fun parseKeywords(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return raw.split(",", "，", "、", "\n", ";", "；")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * 判断某个前台包名是否允许自动滚动：
     * 清单为空 = 不限制（对所有应用生效）；否则仅允许清单内的应用。
     */
    fun isAppAllowed(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val apps = getAllowedApps(context)
        if (apps.isEmpty()) return true
        return apps.contains(packageName)
    }

    // ---------- 校验 ----------
    fun validate(
        minInterval: Int, maxInterval: Int,
        minDuration: Int, maxDuration: Int
    ): Pair<Boolean, String> {
        if (minInterval <= 0 || maxInterval <= 0) return false to "间隔必须为正数"
        if (minInterval >= maxInterval) return false to "最小间隔应小于最大间隔"
        if (minDuration <= 0 || maxDuration <= 0) return false to "时长必须为正数"
        if (minDuration >= maxDuration) return false to "最小时长应小于最大时长"
        if (maxDuration > 3000) return false to "滑动时长过大"
        return true to ""
    }

}

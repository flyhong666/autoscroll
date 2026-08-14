package cn.ggdoc.autoscroll.config

import android.content.Context
import android.content.SharedPreferences
import cn.ggdoc.autoscroll.human.SceneIds

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
    private const val KEY_INTERVAL_CUSTOMIZED = "interval_customized"

    // ===== 卡死自恢复 =====
    private const val KEY_AUTO_RECOVER = "auto_recover_when_stuck"

    // ===== 任务调度 =====
    private const val KEY_AUTO_LIKE = "auto_like"
    private const val KEY_LIKE_PROBABILITY = "like_probability"
    private const val KEY_AD_BLOCK = "ad_block"
    private const val KEY_TIMED_STOP = "timed_stop"
    private const val KEY_TIMED_STOP_MINUTES = "timed_stop_minutes"
    private const val KEY_APP_ROTATION = "app_rotation"
    private const val KEY_ROTATION_MINUTES = "rotation_minutes"
    private const val KEY_ROTATION_APPS = "rotation_apps"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
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
    private const val KEY_SCHEDULE_WINDOWS = "schedule_windows"
    private const val KEY_RECOVER_ENABLED = "recover_enabled"
    private const val KEY_RECOVER_RUNNING = "recover_running"
    private const val KEY_BATTERY_GUARD = "battery_guard"
    private const val KEY_BATTERY_THRESHOLD = "battery_threshold"
    private const val KEY_WIFI_ONLY = "wifi_only"

    // ===== 应用黑白名单 =====
    private const val KEY_APP_FILTER_MODE = "app_filter_mode"
    private const val KEY_APP_FILTER_LIST = "app_filter_list"

    // ===== 详情流（新闻 / 社交场景：点开→浏览→返回） =====
    private const val KEY_DETAIL_FLOW_ENABLED = "detail_flow_enabled"
    private const val KEY_DETAIL_DWELL_MIN = "detail_dwell_min_seconds"
    private const val KEY_DETAIL_DWELL_MAX = "detail_dwell_max_seconds"
    private const val KEY_DETAIL_READ_ALL_PROBABILITY = "detail_read_all_probability"
    private const val KEY_DETAIL_MAX_SCROLLS = "detail_max_scrolls"

    // ===== 自定义手势（自定义场景） =====
    private const val KEY_CUSTOM_GESTURE_TYPE = "custom_gesture_type"
    private const val KEY_CUSTOM_TAP_X = "custom_tap_x_pct"
    private const val KEY_CUSTOM_TAP_Y = "custom_tap_y_pct"
    private const val KEY_CUSTOM_SWIPE_DISTANCE = "custom_swipe_distance_pct"
    private const val KEY_CUSTOM_GESTURE_SEQUENCE = "custom_gesture_sequence"

    // ===== 默认值 =====
    const val DEFAULT_MIN_INTERVAL = 3
    const val DEFAULT_MAX_INTERVAL = 20
    const val DEFAULT_MIN_DURATION = 300
    const val DEFAULT_MAX_DURATION = 500
    const val DEFAULT_AUTO_LIKE = false
    const val DEFAULT_LIKE_PROBABILITY = 30


    /** 卡死自恢复：默认开启（避免刷到底后无脑空转） */
    const val DEFAULT_AUTO_RECOVER = true
    const val DEFAULT_AD_BLOCK = true
    const val DEFAULT_TIMED_STOP = false
    const val DEFAULT_TIMED_STOP_MINUTES = 30
    const val DEFAULT_APP_ROTATION = false
    const val DEFAULT_ROTATION_MINUTES = 10
    const val DEFAULT_KEEP_SCREEN_ON = true
    const val DEFAULT_SCHEDULE_ENABLED = false
    const val DEFAULT_SCHEDULE_START_MIN = 480        // 08:00
    const val DEFAULT_SCHEDULE_END_MIN = 1200         // 20:00

    /**
     * 定时窗口最大数量。与 [cn.ggdoc.autoscroll.service.ScheduleController.cancelAlarms]
     * 的取消防护上限保持一致：窗口数超过此值会导致多余窗口的闹钟永远无法被取消
     * （残留闹钟在错误时刻触发），故读取时统一截断。
     */
    const val MAX_SCHEDULE_WINDOWS = 8
    const val DEFAULT_RECOVER_ENABLED = true
    const val DEFAULT_RECOVER_RUNNING = false
    const val DEFAULT_BATTERY_GUARD = false
    const val DEFAULT_BATTERY_THRESHOLD = 15          // %
    const val DEFAULT_WIFI_ONLY = false

    // 应用黑白名单默认值
    const val FILTER_OFF = "off"
    const val FILTER_WHITELIST = "whitelist"
    const val FILTER_BLACKLIST = "blacklist"
    const val DEFAULT_APP_FILTER_MODE = FILTER_OFF
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

    // 自定义手势默认值
    const val DEFAULT_CUSTOM_GESTURE_TYPE = "swipe_up"
    const val DEFAULT_CUSTOM_TAP_X = 50               // 屏幕宽度百分比
    const val DEFAULT_CUSTOM_TAP_Y = 50               // 屏幕高度百分比
    const val DEFAULT_CUSTOM_SWIPE_DISTANCE = 70      // 滑动距离占屏幕百分比
    const val MIN_CUSTOM_PCT = 5
    const val MAX_CUSTOM_PCT = 95

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
    // 场景 ID 的唯一真源在 SceneIds（不依赖 Android，便于纯 JVM 单元测试）；
    // 这里保留同名常量作为别名，避免改动所有既有调用点。
    const val SCENE_SHORT_VIDEO = SceneIds.SHORT_VIDEO
    const val SCENE_NEWS = SceneIds.NEWS
    const val SCENE_NOVEL = SceneIds.NOVEL
    const val SCENE_SOCIAL = SceneIds.SOCIAL
    const val SCENE_LIVE = SceneIds.LIVE
    const val SCENE_CUSTOM = SceneIds.CUSTOM
    const val SCENE_AUTO = SceneIds.AUTO

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


    /**
     * 用户是否手工改过滑动节奏。
     * 一旦改过，场景自动切换就不再覆盖 interval / duration，尊重用户设置。
     */
    fun isIntervalCustomized(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INTERVAL_CUSTOMIZED, false)
    fun markIntervalCustomized(context: Context) =
        prefs(context).edit().putBoolean(KEY_INTERVAL_CUSTOMIZED, true).apply()
    fun clearIntervalCustomized(context: Context) =
        prefs(context).edit().putBoolean(KEY_INTERVAL_CUSTOMIZED, false).apply()

    // ---------- 卡死自恢复（O2） ----------
    /** 是否启用「内容无变化自恢复」。默认开启，可在设置里关掉。 */
    fun isAutoRecover(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_RECOVER, DEFAULT_AUTO_RECOVER)
    fun setAutoRecover(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_RECOVER, value).apply()

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
            // 修复：prefs 被写坏为 0/负数时，轮换协程会忙循环连续启动 Activity，强制 clamp
            .coerceIn(1, 24 * 60)
    fun setRotationMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_ROTATION_MINUTES, value).apply()

    // ---------- 多 APP 轮换：用户自选的待轮换应用包名池 ----------
    fun getRotationApps(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_ROTATION_APPS, null).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    fun setRotationApps(context: Context, apps: Set<String>) =
        prefs(context).edit()
            .putString(KEY_ROTATION_APPS, apps.joinToString(","))
            .apply()

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

    /**
     * 多个运行时段（每天生效），每个元素为 (开始分钟, 结束分钟)。
     * 存储格式："480-1200;1320-1380"（分号分隔，每段"起-止"）。
     *
     * 兼容旧版单窗口：新键缺失时，回退到旧的 schedule_start_min / schedule_end_min
     * 拼成单窗口，保证升级用户配置不丢。
     */
    fun getScheduleWindows(context: Context): List<Pair<Int, Int>> {
        val raw = prefs(context).getString(KEY_SCHEDULE_WINDOWS, null)
        if (!raw.isNullOrBlank()) {
            val list = raw.split(";").mapNotNull { seg ->
                val parts = seg.split("-")
                if (parts.size == 2) {
                    val s = parts[0].toIntOrNull()?.coerceIn(0, 1439)
                    val e = parts[1].toIntOrNull()?.coerceIn(0, 1439)
                    if (s != null && e != null) s to e else null
                } else null
            }
            if (list.isNotEmpty()) return list.take(MAX_SCHEDULE_WINDOWS)
        }
        val s = prefs(context).getInt(KEY_SCHEDULE_START_MIN, DEFAULT_SCHEDULE_START_MIN).coerceIn(0, 1439)
        val e = prefs(context).getInt(KEY_SCHEDULE_END_MIN, DEFAULT_SCHEDULE_END_MIN).coerceIn(0, 1439)
        return listOf(s to e)
    }

    fun setScheduleWindows(context: Context, windows: List<Pair<Int, Int>>) {
        val str = windows.joinToString(";") { "${it.first}-${it.second}" }
        prefs(context).edit().putString(KEY_SCHEDULE_WINDOWS, str).apply()
    }

    // ---------- 进程恢复 ----------
    /** 无障碍服务被系统回收后，是否自动恢复此前正在进行的滚动（尊重定时窗口） */
    fun isRecoverEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECOVER_ENABLED, DEFAULT_RECOVER_ENABLED)
    fun setRecoverEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECOVER_ENABLED, value).apply()

    /** 进程被回收前是否正在滚动：startScrolling 置 true，stopScrolling 置 false */
    fun isRecoverRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECOVER_RUNNING, DEFAULT_RECOVER_RUNNING)
    fun setRecoverRunning(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECOVER_RUNNING, value).apply()

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

    // ---------- 应用黑白名单 ----------
    /** 包名过滤模式：off / whitelist / blacklist */
    fun getAppFilterMode(context: Context): String =
        prefs(context).getString(KEY_APP_FILTER_MODE, DEFAULT_APP_FILTER_MODE)
            ?: DEFAULT_APP_FILTER_MODE

    fun setAppFilterMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_APP_FILTER_MODE, mode).apply()

    /** 过滤列表（包名集合），与 mode 配合使用 */
    fun getAppFilterList(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_APP_FILTER_LIST, null).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setAppFilterList(context: Context, apps: Set<String>) =
        prefs(context).edit()
            .putString(KEY_APP_FILTER_LIST, apps.joinToString(","))
            .apply()

    // ---------- 详情流（新闻 / 社交） ----------
    /** 详情流开关：默认开启；关闭后新闻 / 社交场景退化为纯滑动 */
    fun isDetailFlowEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DETAIL_FLOW_ENABLED, true)
    fun setDetailFlowEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_DETAIL_FLOW_ENABLED, value).apply()

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

    // ---------- 自定义手势 ----------
    fun getCustomGestureType(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_GESTURE_TYPE, DEFAULT_CUSTOM_GESTURE_TYPE)
            ?: DEFAULT_CUSTOM_GESTURE_TYPE

    fun setCustomGestureType(context: Context, value: String) =
        prefs(context).edit().putString(KEY_CUSTOM_GESTURE_TYPE, value).apply()

    fun getCustomTapX(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_TAP_X, DEFAULT_CUSTOM_TAP_X)
            .coerceIn(MIN_CUSTOM_PCT, MAX_CUSTOM_PCT)

    fun setCustomTapX(context: Context, value: Int) =
        prefs(context).edit()
            .putInt(KEY_CUSTOM_TAP_X, value.coerceIn(MIN_CUSTOM_PCT, MAX_CUSTOM_PCT))
            .apply()

    fun getCustomTapY(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_TAP_Y, DEFAULT_CUSTOM_TAP_Y)
            .coerceIn(MIN_CUSTOM_PCT, MAX_CUSTOM_PCT)

    fun setCustomTapY(context: Context, value: Int) =
        prefs(context).edit()
            .putInt(KEY_CUSTOM_TAP_Y, value.coerceIn(MIN_CUSTOM_PCT, MAX_CUSTOM_PCT))
            .apply()

    fun getCustomSwipeDistance(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_SWIPE_DISTANCE, DEFAULT_CUSTOM_SWIPE_DISTANCE)
            .coerceIn(MIN_CUSTOM_PCT, MAX_CUSTOM_PCT)

    fun setCustomSwipeDistance(context: Context, value: Int) =
        prefs(context).edit()
            .putInt(KEY_CUSTOM_SWIPE_DISTANCE, value.coerceIn(MIN_CUSTOM_PCT, MAX_CUSTOM_PCT))
            .apply()

    // ---------- 自定义手势序列（可编排：手势 + 等待秒数 循环） ----------
    fun getCustomGestureSequence(context: Context): List<CustomGestureStep> {
        val raw = prefs(context).getString(KEY_CUSTOM_GESTURE_SEQUENCE, null)
        val list = CustomGestureStep.deserialize(raw)
        if (list.isNotEmpty()) return list
        // 兼容旧版单手势配置：降级为「单步手势 + 默认 3 秒等待」
        return listOf(
            CustomGestureStep(
                gesture = getCustomGestureType(context),
                waitSec = 3,
                xPct = getCustomTapX(context),
                yPct = getCustomTapY(context),
                distPct = getCustomSwipeDistance(context)
            )
        )
    }

    fun setCustomGestureSequence(context: Context, steps: List<CustomGestureStep>) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_GESTURE_SEQUENCE, CustomGestureStep.serialize(steps))
            .apply()
    }

    /** 序列是否为空（仅等待、无任何手势） */
    fun hasCustomGestureSequence(context: Context): Boolean =
        getCustomGestureSequence(context).any { !it.isWaitOnly() }


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

    // ---------- 参数方案预设 ----------
    /**
     * 一套可复用的运行方案（节奏 + 核心任务开关）。
     * 用于「一键保存 / 切换」整套参数，避免每次手动调一堆开关。
     */
    data class Preset(
        val scene: String,
        val minInterval: Int,
        val maxInterval: Int,
        val minDuration: Int,
        val maxDuration: Int,
        val autoLike: Boolean,
        val likeProbability: Int,
        val adBlock: Boolean,
        val detailFlow: Boolean
    )

    private const val PRESET_PREFIX = "preset_"
    private const val PRESET_SEP = "|"

    /** 由当前配置生成方案快照 */
    fun currentPreset(context: Context): Preset = Preset(
        scene = getCurrentScene(context),
        minInterval = getMinInterval(context),
        maxInterval = getMaxInterval(context),
        minDuration = getMinDuration(context),
        maxDuration = getMaxDuration(context),
        autoLike = isAutoLike(context),
        likeProbability = getLikeProbability(context),
        adBlock = isAdBlock(context),
        detailFlow = isDetailFlowEnabled(context)
    )

    /** 把方案写回配置并同步到运行中的服务。 */
    fun applyPreset(context: Context, p: Preset) {
        setCurrentScene(context, p.scene)
        setMinInterval(context, p.minInterval)
        setMaxInterval(context, p.maxInterval)
        setMinDuration(context, p.minDuration)
        setMaxDuration(context, p.maxDuration)
        setAutoLike(context, p.autoLike)
        setLikeProbability(context, p.likeProbability)
        setAdBlock(context, p.adBlock)
        setDetailFlowEnabled(context, p.detailFlow)
    }

    /** 保存当前配置为名为 [name] 的方案。返回是否成功（空名返回 false）。 */
    fun saveCurrentAsPreset(context: Context, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val p = currentPreset(context)
        val serialized = listOf(
            p.scene, p.minInterval, p.maxInterval, p.minDuration, p.maxDuration,
            p.autoLike, p.likeProbability, p.adBlock, p.detailFlow
        ).joinToString(PRESET_SEP) { it.toString() }
        prefs(context).edit().putString(PRESET_PREFIX + n, serialized).apply()
        return true
    }

    /** 读取名为 [name] 的方案，解析失败返回 null。 */
    fun getPreset(context: Context, name: String): Preset? {
        val raw = prefs(context).getString(PRESET_PREFIX + name, null) ?: return null
        val f = raw.split(PRESET_SEP)
        if (f.size != 9) return null
        return try {
            Preset(
                scene = f[0],
                minInterval = f[1].toInt(),
                maxInterval = f[2].toInt(),
                minDuration = f[3].toInt(),
                maxDuration = f[4].toInt(),
                autoLike = f[5].toBoolean(),
                likeProbability = f[6].toInt(),
                adBlock = f[7].toBoolean(),
                detailFlow = f[8].toBoolean()
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** 已保存的方案名列表（按保存顺序） */
    fun listPresets(context: Context): List<String> =
        prefs(context).all.keys
            .filter { it.startsWith(PRESET_PREFIX) }
            .map { it.removePrefix(PRESET_PREFIX) }
            .sorted()

    fun deletePreset(context: Context, name: String) {
        prefs(context).edit().remove(PRESET_PREFIX + name).apply()
    }

}

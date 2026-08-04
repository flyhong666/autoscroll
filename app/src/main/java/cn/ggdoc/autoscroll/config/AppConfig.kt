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
    private const val KEY_FILTER_APP = "filter_short_video_app"

    // ===== 默认值 =====
    const val DEFAULT_MIN_INTERVAL = 3
    const val DEFAULT_MAX_INTERVAL = 20
    const val DEFAULT_MIN_DURATION = 300
    const val DEFAULT_MAX_DURATION = 500
    const val DEFAULT_FILTER_APP = true
    const val DEFAULT_AUTO_LIKE = false
    const val DEFAULT_LIKE_PROBABILITY = 30
    const val DEFAULT_AD_BLOCK = true
    const val DEFAULT_TIMED_STOP = false
    const val DEFAULT_TIMED_STOP_MINUTES = 30
    const val DEFAULT_APP_ROTATION = false
    const val DEFAULT_ROTATION_MINUTES = 10
    const val DEFAULT_KEEP_SCREEN_ON = true

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

    fun isFilterShortVideoApp(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FILTER_APP, DEFAULT_FILTER_APP)
    fun setFilterShortVideoApp(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_FILTER_APP, value).apply()

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

    /**
     * 判断某个包名是否属于当前场景的应用列表
     */
    fun isAppInScene(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val scene = getCurrentScene(context)
        val packages = SceneConfig.getScenePackages(scene)
        return packages.any { packageName.startsWith(it, ignoreCase = true) }
    }
}

package cn.ggdoc.autoscroll.config

import cn.ggdoc.autoscroll.R

/**
 * 场景模板定义：6 大场景，每个场景拥有独立的手势策略。
 *
 * 手势维度说明（详见 [ScrollMode]）：
 * - mode：核心滑动/翻页/挂机策略
 * - supportAutoLike：是否执行“点赞”类手势（短视频 / 社交动态）
 * - 滑动比例字段（swipeStartYRatio / swipeEndYRatio / swipeCrossXRatio）：
 *   细粒度控制滑动幅度、起始位置与双列交叉手感
 * - doubleColumn：双列瀑布流卡片，向中心滑动并左右交叉命中两列
 */
object SceneConfig {

    // ============ 兼容旧调用方的流类型常量 ============
    const val FLOW_DETAIL = "detail"
    const val FLOW_IDLE = "idle"
    const val FLOW_PAGE_TAP = "page_tap"
    const val FLOW_CUSTOM = "custom"

    enum class ScrollMode {
        /** 全屏竖向滑动（短视频、信息流） */
        VERTICAL,
        /** 双列瀑布流卡片，向中心滑动并交叉命中两列 */
        DOUBLE_COLUMN,
        /** 小说 / 图文翻页（幅度小、无回弹） */
        PAGE,
        /** 直播 / 长内容挂机：不滑动，仅维持亮屏与保活 */
        IDLE
    }

    data class Scene(
        val id: String,
        val nameRes: Int,
        val iconRes: Int,
        val descRes: Int,
        val recommendMinInterval: Int,
        val recommendMaxInterval: Int,
        /** 是否在当前场景启用“点赞”手势（供 UI 展示与策略判断） */
        val supportAutoLike: Boolean,
        val mode: ScrollMode,
        /** 滑动起始点在屏幕高度的比例（0.82=偏下） */
        val swipeStartYRatio: Float,
        /** 滑动结束点在屏幕高度的比例（越小滑得越远） */
        val swipeEndYRatio: Float,
        /** 横向偏移比例，用于双列交叉滑动制造左右位移手感 */
        val swipeCrossXRatio: Float,
        /** 双列场景：每次循环在左右列之间交叉 */
        val doubleColumn: Boolean,
        /** 挂机模式：完全不滑动 */
        val idle: Boolean,
        /** 推荐的单次手势滑动时长（毫秒），供设置面板 Slider 预设 */
        val recommendMinDuration: Int = 300,
        /** 推荐的单次手势滑动时长上限（毫秒） */
        val recommendMaxDuration: Int = 500
    ) {
        /** 兼容旧代码：返回该场景对应的流类型 */
        fun flow(): String = when (id) {
            "news", "social" -> FLOW_DETAIL
            "live" -> FLOW_IDLE
            "novel" -> FLOW_PAGE_TAP
            else -> FLOW_CUSTOM
        }
    }

    val SCENES = listOf(
        Scene(
            id = "short_video",
            nameRes = R.string.scene_short_video,
            iconRes = R.drawable.ic_scene_video,
            descRes = R.string.scene_short_video_desc,
            recommendMinInterval = 3,
            recommendMaxInterval = 20,
            supportAutoLike = true,
            mode = ScrollMode.VERTICAL,
            swipeStartYRatio = 0.82f,
            swipeEndYRatio = 0.18f,
            swipeCrossXRatio = 0f,
            doubleColumn = false,
            idle = false,
            recommendMinDuration = 200,
            recommendMaxDuration = 400
        ),
        Scene(
            id = "news",
            nameRes = R.string.scene_news,
            iconRes = R.drawable.ic_scene_news,
            descRes = R.string.scene_news_desc,
            recommendMinInterval = 5,
            recommendMaxInterval = 25,
            supportAutoLike = false,
            mode = ScrollMode.VERTICAL,
            swipeStartYRatio = 0.78f,
            swipeEndYRatio = 0.22f,
            swipeCrossXRatio = 0f,
            doubleColumn = false,
            idle = false,
            recommendMinDuration = 300,
            recommendMaxDuration = 600
        ),
        Scene(
            id = "novel",
            nameRes = R.string.scene_novel,
            iconRes = R.drawable.ic_scene_book,
            descRes = R.string.scene_novel_desc,
            recommendMinInterval = 8,
            recommendMaxInterval = 30,
            supportAutoLike = false,
            mode = ScrollMode.PAGE,
            swipeStartYRatio = 0.88f,
            swipeEndYRatio = 0.12f,
            swipeCrossXRatio = 0f,
            doubleColumn = false,
            idle = false,
            recommendMinDuration = 150,
            recommendMaxDuration = 350
        ),
        Scene(
            id = "social",
            nameRes = R.string.scene_social,
            iconRes = R.drawable.ic_scene_social,
            descRes = R.string.scene_social_desc,
            recommendMinInterval = 4,
            recommendMaxInterval = 18,
            supportAutoLike = true,
            mode = ScrollMode.DOUBLE_COLUMN,
            swipeStartYRatio = 0.80f,
            swipeEndYRatio = 0.20f,
            swipeCrossXRatio = 0.12f,
            doubleColumn = true,
            idle = false,
            recommendMinDuration = 250,
            recommendMaxDuration = 500
        ),
        Scene(
            id = "live",
            nameRes = R.string.scene_live,
            iconRes = R.drawable.ic_scene_live,
            descRes = R.string.scene_live_desc,
            recommendMinInterval = 30,
            recommendMaxInterval = 90,
            supportAutoLike = false,
            mode = ScrollMode.IDLE,
            swipeStartYRatio = 0f,
            swipeEndYRatio = 0f,
            swipeCrossXRatio = 0f,
            doubleColumn = false,
            idle = true,
            recommendMinDuration = 300,
            recommendMaxDuration = 500
        ),
        Scene(
            id = "custom",
            nameRes = R.string.scene_custom,
            iconRes = R.drawable.ic_scene_custom,
            descRes = R.string.scene_custom_desc,
            recommendMinInterval = 3,
            recommendMaxInterval = 20,
            supportAutoLike = false,
            mode = ScrollMode.VERTICAL,
            swipeStartYRatio = 0.80f,
            swipeEndYRatio = 0.20f,
            swipeCrossXRatio = 0f,
            doubleColumn = false,
            idle = false,
            recommendMinDuration = 200,
            recommendMaxDuration = 500
        )
    )

    private val SCENE_MAP = SCENES.associateBy { it.id }

    fun getScene(id: String): Scene = SCENE_MAP[id] ?: SCENES.last()

    /** 兼容旧调用方 */
    fun getSceneFlow(id: String): String = getScene(id).flow()

    /** 兼容旧调用方：返回该场景适用的包名前缀（无限制时返回空列表） */
    fun getScenePackages(id: String): List<String> = emptyList()

    fun allSceneIds(): List<String> = SCENES.map { it.id }

    /** 返回全部场景列表（供 UI 展示） */
    fun getAllScenes(): List<Scene> = SCENES
}

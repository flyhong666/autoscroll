package cn.ggdoc.autoscroll.config

import cn.ggdoc.autoscroll.R

/**
 * 场景模板定义：6 大场景，每个场景拥有独立的手势策略。
 *
 * 手势维度说明（详见 [ScrollMode]）：
 * - mode：核心滑动/翻页/挂机策略
 * - supportAutoLike：是否执行“点赞”类手势（短视频 / 社交动态）
 * - 滑动比例字段（swipeStartYRatio / swipeEndYRatio）：
 *   细粒度控制滑动幅度与起始位置
 */
object SceneConfig {

    enum class ScrollMode {
        /** 全屏竖向滑动（短视频、信息流） */
        VERTICAL,
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
        /** 是否走「列表-详情」拟人浏览闭环（新闻 / 社交场景） */
        val useDetailFlow: Boolean = false,
        /** 推荐的单次手势滑动时长（毫秒），供设置面板 Slider 预设 */
        val recommendMinDuration: Int = 300,
        /** 推荐的单次手势滑动时长上限（毫秒） */
        val recommendMaxDuration: Int = 500
    )

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
            swipeEndYRatio = 0.18f,            recommendMinDuration = 200,
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
            swipeEndYRatio = 0.22f,            useDetailFlow = true,
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
            swipeEndYRatio = 0.12f,            recommendMinDuration = 150,
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
            mode = ScrollMode.VERTICAL,
            swipeStartYRatio = 0.80f,
            swipeEndYRatio = 0.20f,            useDetailFlow = true,
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
            swipeEndYRatio = 0f,            recommendMinDuration = 300,
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
            swipeEndYRatio = 0.20f,            recommendMinDuration = 200,
            recommendMaxDuration = 500
        ),
        Scene(
            id = SceneIds.AUTO,
            nameRes = R.string.scene_auto,
            iconRes = R.drawable.ic_scene_custom,
            descRes = R.string.scene_auto_desc,
            recommendMinInterval = 3,
            recommendMaxInterval = 20,
            supportAutoLike = false,
            mode = ScrollMode.VERTICAL,
            swipeStartYRatio = 0.80f,
            swipeEndYRatio = 0.20f,            recommendMinDuration = 200,
            recommendMaxDuration = 500
        )
    )

    /** 内置包名 → 场景 ID 映射（「自动识别」场景使用） */
    private val PKG_SCENE_MAP = mapOf(
        // 短视频
        "com.ss.android.ugc.aweme" to SceneIds.SHORT_VIDEO,        // 抖音
        "com.ss.android.ugc.aweme.lite" to SceneIds.SHORT_VIDEO,   // 抖音极速版
        "com.smile.gifmaker" to SceneIds.SHORT_VIDEO,              // 快手
        "com.kuaishou.nebula" to SceneIds.SHORT_VIDEO,             // 快手极速版
        "com.zhiliaoapp.musically" to SceneIds.SHORT_VIDEO,        // TikTok
        // 新闻资讯
        "com.ss.android.article.news" to SceneIds.NEWS,            // 今日头条
        "com.ss.android.article.lite" to SceneIds.NEWS,            // 今日头条极速版
        "com.tencent.news" to SceneIds.NEWS,                       // 腾讯新闻
        "com.netease.newsreader.activity" to SceneIds.NEWS,        // 网易新闻
        "com.ss.android.article.video" to SceneIds.NEWS,           // 西瓜视频
        // 小说阅读
        "com.dragon.read" to SceneIds.NOVEL,                       // 番茄小说
        "com.qidian.QDReader" to SceneIds.NOVEL,                   // 起点读书
        "com.tencent.weread" to SceneIds.NOVEL,                    // 微信读书
        "com.chaozh.iReaderFree" to SceneIds.NOVEL,                // 掌阅
        // 社交动态
        "com.sina.weibo" to SceneIds.SOCIAL,                       // 微博
        "com.xingin.xhs" to SceneIds.SOCIAL,                       // 小红书
        "com.zhihu.android" to SceneIds.SOCIAL,                    // 知乎
        "com.tencent.mm" to SceneIds.SOCIAL,                       // 微信（朋友圈/视频号）
        // 直播挂机
        "com.ss.android.ugc.live" to SceneIds.LIVE,                // 抖音直播
        "com.duowan.mobile" to SceneIds.LIVE,                      // 虎牙直播
        "air.tv.douyu.android" to SceneIds.LIVE                    // 斗鱼直播
    )

    /**
     * 解析当前实际生效的场景模板。
     *
     * - [SceneIds.AUTO]：按 [foregroundPkg] 查内置映射，未命中回退 [SceneIds.CUSTOM]（通用滑动）；
     * - 其他场景：原样返回。
     */
    fun resolveScene(sceneId: String, foregroundPkg: String?): Scene =
        if (sceneId == SceneIds.AUTO) {
            val mapped = foregroundPkg?.let { PKG_SCENE_MAP[it] } ?: SceneIds.CUSTOM
            getScene(mapped)
        } else {
            getScene(sceneId)
        }

    private val SCENE_MAP = SCENES.associateBy { it.id }

    fun getScene(id: String): Scene =
        SCENE_MAP[id] ?: SCENES.first { it.id == SceneIds.CUSTOM }

    fun allSceneIds(): List<String> = SCENES.map { it.id }

    /** 返回全部场景列表（供 UI 展示） */
    fun getAllScenes(): List<Scene> = SCENES
}

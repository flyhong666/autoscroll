package cn.ggdoc.autoscroll.config

/**
 * 场景配置：定义 6 大场景的应用包名白名单 + 推荐参数 + 描述
 *
 * 每个场景对应一类 APP：
 *  - 短视频：抖音、快手、TikTok 等
 *  - 新闻：今日头条、腾讯新闻、网易新闻等
 *  - 小说：番茄小说、起点读书等
 *  - 社交：微博、小红书等
 *  - 直播：抖音直播、快手直播等
 *  - 自定义：用户自选所有 APP
 */
object SceneConfig {

    // ===== 场景操作方式（流类型） =====
    /** 全屏上滑（短视频、自定义通用） */
    const val FLOW_SWIPE = "swipe"
    /** 列表详情流：顺序点开 → 浏览 → 返回（新闻、社交） */
    const val FLOW_DETAIL = "detail"
    /** 点按屏幕右侧翻页（小说阅读） */
    const val FLOW_PAGE_TAP = "page_tap"
    /** 只挂机不操作（直播） */
    const val FLOW_IDLE = "idle"

    data class Scene(
        val id: String,
        val nameRes: Int,
        val descRes: Int,
        val iconRes: Int,
        val packages: List<String>,
        val recommendMinInterval: Int,
        val recommendMaxInterval: Int,
        val recommendMinDuration: Int,
        val recommendMaxDuration: Int,
        val supportAutoLike: Boolean,
        val flow: String = FLOW_SWIPE
    )

    val SHORT_VIDEO_PACKAGES = listOf(
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.smile.gifmaker",
        "com.gif.gifshow",
        "com.kuaishou.nebula",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.smile.gifmaker.lite"
    )

    val NEWS_PACKAGES = listOf(
        "com.ss.android.article.news",
        "com.ss.android.article.lite",
        "com.tencent.news",
        "com.netease.news",
        "com.sohu.newsclient",
        "com.ifeng.news2",
        "cn.mil.news",
        "com.ss.android.article.video"
    )

    val NOVEL_PACKAGES = listOf(
        "com.dragon.read",
        "com.dragon.read.lite",
        "com.qidian.QDReader",
        "com.qidian.QDReader.lite",
        "com.changdu",
        "com.huawei.himovie",
        "com.esea.readbook",
        "com.mianfei.read"
    )

    val SOCIAL_PACKAGES = listOf(
        "com.xingin.xhs",
        "com.xingin.xhs.lite",
        "com.sina.weibo",
        "com.sina.weibolite",
        "com.tencent.mm",
        "com.qzone",
        "com.zhihu.android",
        "com.jingdong.app.mall"
    )

    val LIVE_PACKAGES = listOf(
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.huya.live",
        "com.douyu.group",
        "com.netease.cc",
        "com.panda.android",
        "com.huawei.himovie"
    )

    /**
     * 自定义场景：常见可滑动 APP 的合集
     */
    val CUSTOM_PACKAGES = listOf(
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.ss.android.article.news",
        "com.dragon.read",
        "com.xingin.xhs",
        "com.sina.weibo",
        "com.tencent.mm",
        "tv.danmaku.bili",
        "com.huya.live",
        "com.douyu.group"
    )

    /**
     * 广告关闭关键词已下放至设置（AppConfig.getAdKeywords），不再写死于此。
     */

    /**
     * 获取所有场景列表
     * 注：nameRes / descRes / iconRes 在调用方填充，这里直接引用 R 资源
     */
    fun getAllScenes(): List<Scene> = listOf(
        Scene(
            id = AppConfig.SCENE_SHORT_VIDEO,
            nameRes = cn.ggdoc.autoscroll.R.string.scene_short_video,
            descRes = cn.ggdoc.autoscroll.R.string.scene_short_video_desc,
            iconRes = cn.ggdoc.autoscroll.R.drawable.ic_scene_video,
            packages = SHORT_VIDEO_PACKAGES,
            recommendMinInterval = 3,
            recommendMaxInterval = 20,
            recommendMinDuration = 300,
            recommendMaxDuration = 500,
            supportAutoLike = true
        ),
        Scene(
            id = AppConfig.SCENE_NEWS,
            nameRes = cn.ggdoc.autoscroll.R.string.scene_news,
            descRes = cn.ggdoc.autoscroll.R.string.scene_news_desc,
            iconRes = cn.ggdoc.autoscroll.R.drawable.ic_scene_news,
            packages = NEWS_PACKAGES,
            recommendMinInterval = 5,
            recommendMaxInterval = 25,
            recommendMinDuration = 350,
            recommendMaxDuration = 600,
            supportAutoLike = false,
            flow = FLOW_DETAIL
        ),
        Scene(
            id = AppConfig.SCENE_NOVEL,
            nameRes = cn.ggdoc.autoscroll.R.string.scene_novel,
            descRes = cn.ggdoc.autoscroll.R.string.scene_novel_desc,
            iconRes = cn.ggdoc.autoscroll.R.drawable.ic_scene_book,
            packages = NOVEL_PACKAGES,
            recommendMinInterval = 8,
            recommendMaxInterval = 30,
            recommendMinDuration = 400,
            recommendMaxDuration = 800,
            supportAutoLike = false,
            flow = FLOW_PAGE_TAP
        ),
        Scene(
            id = AppConfig.SCENE_SOCIAL,
            nameRes = cn.ggdoc.autoscroll.R.string.scene_social,
            descRes = cn.ggdoc.autoscroll.R.string.scene_social_desc,
            iconRes = cn.ggdoc.autoscroll.R.drawable.ic_scene_social,
            packages = SOCIAL_PACKAGES,
            recommendMinInterval = 4,
            recommendMaxInterval = 18,
            recommendMinDuration = 320,
            recommendMaxDuration = 550,
            supportAutoLike = true,
            flow = FLOW_DETAIL
        ),
        Scene(
            id = AppConfig.SCENE_LIVE,
            nameRes = cn.ggdoc.autoscroll.R.string.scene_live,
            descRes = cn.ggdoc.autoscroll.R.string.scene_live_desc,
            iconRes = cn.ggdoc.autoscroll.R.drawable.ic_scene_live,
            packages = LIVE_PACKAGES,
            recommendMinInterval = 30,
            recommendMaxInterval = 90,
            recommendMinDuration = 0,
            recommendMaxDuration = 0,
            supportAutoLike = true,
            flow = FLOW_IDLE
        ),
        Scene(
            id = AppConfig.SCENE_CUSTOM,
            nameRes = cn.ggdoc.autoscroll.R.string.scene_custom,
            descRes = cn.ggdoc.autoscroll.R.string.scene_custom_desc,
            iconRes = cn.ggdoc.autoscroll.R.drawable.ic_scene_custom,
            packages = CUSTOM_PACKAGES,
            recommendMinInterval = 3,
            recommendMaxInterval = 20,
            recommendMinDuration = 300,
            recommendMaxDuration = 500,
            supportAutoLike = false
        )
    )

    fun getScene(sceneId: String): Scene =
        getAllScenes().firstOrNull { it.id == sceneId }
            ?: getAllScenes().first()

    fun getScenePackages(sceneId: String): List<String> =
        getScene(sceneId).packages

    /** 场景对应的操作方式 */
    fun getSceneFlow(sceneId: String): String = getScene(sceneId).flow

    /** 流类型对应的展示文案资源 */
    fun flowLabelRes(flow: String): Int = when (flow) {
        FLOW_DETAIL -> cn.ggdoc.autoscroll.R.string.flow_detail
        FLOW_PAGE_TAP -> cn.ggdoc.autoscroll.R.string.flow_page_tap
        FLOW_IDLE -> cn.ggdoc.autoscroll.R.string.flow_idle
        else -> cn.ggdoc.autoscroll.R.string.flow_swipe
    }
}

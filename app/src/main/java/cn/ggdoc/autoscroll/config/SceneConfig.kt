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
        val supportAutoLike: Boolean
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
     * 广告 / 弹窗常见关闭文本（用于 AdBlocker）
     */
    val AD_BLOCK_KEYWORDS = listOf(
        "跳过", "关闭", "广告", "立即领取", "知道了", "确定",
        "Skip", "skip", "Close", "close", "Ad", "Got it",
        "不再提醒", "暂不", "取消", "稍后", "No thanks"
    )

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
            supportAutoLike = false
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
            supportAutoLike = false
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
            supportAutoLike = true
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
            supportAutoLike = true
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
}

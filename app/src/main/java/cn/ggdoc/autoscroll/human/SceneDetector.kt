package cn.ggdoc.autoscroll.human


/**
 * 场景自动识别（纯逻辑，可单元测试）。
 *
 * 解决的问题：用户切到抖音还得手动去 UI 选「短视频」场景，
 * 切到今日头条又得手动改成「新闻」。而 `onAccessibilityEvent` 本来
 * 就在监听 `TYPE_WINDOW_STATE_CHANGED`，前台包名是现成的——
 * 接上映射表即可自动切换，成本极低。
 *
 * 匹配策略：先精确匹配，再前缀匹配（覆盖极速版/海外版等变体包名）。
 */
object SceneDetector {

    /** 精确包名 -> 场景 ID */
    private val EXACT: Map<String, String> = buildMap {
        // ---- 短视频 ----
        put("com.ss.android.ugc.aweme", SceneIds.SHORT_VIDEO)          // 抖音
        put("com.ss.android.ugc.aweme.lite", SceneIds.SHORT_VIDEO)     // 抖音极速版
        put("com.smile.gifmaker", SceneIds.SHORT_VIDEO)                // 快手
        put("com.kuaishou.nebula", SceneIds.SHORT_VIDEO)               // 快手极速版
        put("com.zhiliaoapp.musically", SceneIds.SHORT_VIDEO)          // TikTok
        put("com.tencent.weishi", SceneIds.SHORT_VIDEO)                // 微视
        put("com.google.android.youtube", SceneIds.SHORT_VIDEO)        // YouTube

        // ---- 新闻资讯 ----
        put("com.ss.android.article.news", SceneIds.NEWS)              // 今日头条
        put("com.ss.android.article.lite", SceneIds.NEWS)              // 头条极速版
        put("com.tencent.news", SceneIds.NEWS)                         // 腾讯新闻
        put("com.netease.newsreader.activity", SceneIds.NEWS)          // 网易新闻
        put("com.sina.news", SceneIds.NEWS)                            // 新浪新闻
        put("com.ifeng.news2", SceneIds.NEWS)                          // 凤凰新闻
        put("com.hipu.yidian", SceneIds.NEWS)                          // 一点资讯
        put("com.sohu.newsclient", SceneIds.NEWS)                      // 搜狐新闻
        put("com.tencent.reading", SceneIds.NEWS)                      // 天天快报

        // ---- 小说阅读 ----
        put("com.dragon.read", SceneIds.NOVEL)                         // 番茄小说
        put("com.qidian.QDReader", SceneIds.NOVEL)                     // 起点读书
        put("com.kmxs.reader", SceneIds.NOVEL)                         // 七猫小说
        put("com.chaozh.iReaderFree", SceneIds.NOVEL)                  // 掌阅
        put("com.zhangyue.read", SceneIds.NOVEL)                       // 掌阅 iReader
        put("com.biquge.ebook.app", SceneIds.NOVEL)                    // 笔趣阁类

        // ---- 社交动态（双列/图文流）----
        put("com.xingin.xhs", SceneIds.SOCIAL)                         // 小红书
        put("com.sina.weibo", SceneIds.SOCIAL)                         // 微博
        put("com.zhihu.android", SceneIds.SOCIAL)                      // 知乎
        put("com.tencent.mm", SceneIds.SOCIAL)                         // 微信
        put("com.douban.frodo", SceneIds.SOCIAL)                       // 豆瓣
        put("com.taobao.idlefish", SceneIds.SOCIAL)                    // 闲鱼（双列）

        // ---- 直播 ----
        put("com.duowan.kiwi", SceneIds.LIVE)                          // 虎牙
        put("air.tv.douyu.android", SceneIds.LIVE)                     // 斗鱼
        put("com.bilibili.app.blue", SceneIds.LIVE)                    // 哔哩哔哩（蓝版）
    }

    /**
     * 前缀匹配表（按长度降序使用，避免短前缀抢先命中）。
     * 覆盖各家的马甲包：`com.ss.android.ugc.aweme.xxx`、`com.kuaishou.xxx` 等。
     */
    private val PREFIX: List<Pair<String, String>> = listOf(
        "com.ss.android.ugc.aweme" to SceneIds.SHORT_VIDEO,
        "com.kuaishou" to SceneIds.SHORT_VIDEO,
        "com.ss.android.article" to SceneIds.NEWS,
        "com.netease.newsreader" to SceneIds.NEWS,
        "com.qidian" to SceneIds.NOVEL,
        "com.zhangyue" to SceneIds.NOVEL,
        "com.xingin" to SceneIds.SOCIAL,
        "com.sina.weibo" to SceneIds.SOCIAL,
        "com.zhihu" to SceneIds.SOCIAL,
        "air.tv.douyu" to SceneIds.LIVE,
        "com.duowan" to SceneIds.LIVE
    ).sortedByDescending { it.first.length }

    /**
     * 系统级包名：这些不是内容 APP，识别到时不应切换场景。
     * （桌面、系统 UI、设置、本应用自身）
     */
    private val IGNORED_PREFIXES = listOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.bbk.launcher",
        "com.sec.android.app.launcher",
        "cn.ggdoc.autoscroll"
    )

    /** 该包名是否应被忽略（系统 UI / 桌面 / 本应用） */
    fun isIgnored(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        return IGNORED_PREFIXES.any { packageName.startsWith(it) }
    }

    /**
     * 根据前台包名推断场景 ID。
     *
     * @return 场景 ID；无法识别或应忽略时返回 null（调用方保持当前场景不变）
     */
    fun sceneOf(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        if (isIgnored(packageName)) return null
        EXACT[packageName]?.let { return it }
        return PREFIX.firstOrNull { packageName.startsWith(it.first) }?.second
    }

    /**
     * 判断是否需要切换场景。
     *
     * 额外守卫：用户手动选了「自定义」场景说明有特殊编排，
     * 此时不应被自动识别覆盖，否则用户配置的手势序列会失效。
     */
    fun shouldSwitch(currentScene: String, packageName: String?): Boolean {
        if (currentScene == SceneIds.CUSTOM) return false
        val detected = sceneOf(packageName) ?: return false
        return detected != currentScene
    }
}

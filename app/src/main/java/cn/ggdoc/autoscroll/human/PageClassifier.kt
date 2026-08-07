package cn.ggdoc.autoscroll.human

/**
 * 页面类型判定（列表页 / 详情页），纯逻辑，可单元测试。
 *
 * 为什么不能只看 WebView：
 * 原实现用 `className.contains("WebView")` 判断是否在详情页。
 * 但今日头条、腾讯新闻、知乎等主流 APP 的正文页是**原生 RecyclerView 渲染**的，
 * 根本没有 WebView。结果详情页被误判成列表页，详情流会继续在正文里
 * 「挑一条可点条目点开」——点到的是评论、关注、举报、相关推荐，
 * 行为完全失控。
 *
 * 改为多信号加权：单个强信号即可定案，否则需要两个中等信号互相印证。
 */
object PageClassifier {

    enum class PageType {
        /** 明确是列表/信息流页，可以安全地挑条目点开 */
        LIST,

        /** 明确是详情/正文页，应当执行返回而不是继续点 */
        DETAIL,

        /** 信号不足，调用方应保守处理（一般按「不点」处理） */
        UNKNOWN
    }

    /**
     * 从页面采集到的判定信号。
     * 采集逻辑（依赖 AccessibilityNodeInfo）在 service 层，这里只做判定。
     */
    data class Signals(
        /** 可滚动容器是否为 WebView —— 强信号 */
        val isWebViewContainer: Boolean = false,
        /** 页面可见文本总长度（字符数） */
        val totalTextLength: Int = 0,
        /** 单个文本节点的最大长度 —— 正文段落通常很长 */
        val maxSingleTextLength: Int = 0,
        /** 是否存在「评论 / 分享 / 收藏 / 写评论」等详情页专属控件 */
        val hasDetailActionWords: Boolean = false,
        /** 是否存在「返回」按钮/箭头 */
        val hasBackAffordance: Boolean = false,
        /** 满足列表项尺寸的候选条目数量 */
        val listItemCount: Int = 0,
        /** 可点击节点总数 */
        val clickableCount: Int = 0
    )

    /** 正文长度阈值：超过即视为中等信号 */
    const val LONG_TEXT_THRESHOLD = 500

    /** 单段文本长度阈值：正文段落一般远超列表标题 */
    const val LONG_PARAGRAPH_THRESHOLD = 120

    /** 列表项数量下限：低于此值不像列表页 */
    const val FEW_ITEMS_THRESHOLD = 5

    /** 详情页动作词（用于 service 层采集时匹配） */
    val DETAIL_ACTION_WORDS = listOf(
        "写评论", "说点什么", "发表评论", "全部评论", "查看全部评论",
        "评论", "分享", "收藏", "转发", "点赞", "相关推荐", "阅读原文",
        "展开全文", "正文", "作者", "责任编辑"
    )

    /** 列表页特征词（出现即削弱详情页判定） */
    val LIST_HINT_WORDS = listOf(
        "推荐", "关注", "热榜", "热点", "视频", "刷新", "换一批",
        "为你推荐", "最新", "同城", "频道"
    )

    /**
     * 综合判定页面类型。
     *
     * 规则设计原则：**宁可 UNKNOWN，不可误判为 LIST**。
     * 误判成 DETAIL 最多是少刷一条（返回后重来）；
     * 误判成 LIST 会导致在正文里乱点，后果严重得多。
     */
    fun classify(s: Signals): PageType {
        // ---- 强信号：WebView 容器几乎必然是详情正文 ----
        if (s.isWebViewContainer) return PageType.DETAIL

        // ---- 中等信号计分 ----
        var detailScore = 0
        // 正文总量大
        if (s.totalTextLength >= LONG_TEXT_THRESHOLD) detailScore++
        // 存在长段落（列表标题很少超过 120 字）
        if (s.maxSingleTextLength >= LONG_PARAGRAPH_THRESHOLD) detailScore++
        // 存在详情页专属动作控件
        if (s.hasDetailActionWords) detailScore++
        // 可点条目很少（正文页几乎没有等高卡片）
        if (s.listItemCount < FEW_ITEMS_THRESHOLD) detailScore++

        // 任意两个中等信号互相印证即判详情页
        if (detailScore >= 2) return PageType.DETAIL

        // ---- 列表页正向判定：条目充足且没有明显正文特征 ----
        if (s.listItemCount >= FEW_ITEMS_THRESHOLD &&
            s.maxSingleTextLength < LONG_PARAGRAPH_THRESHOLD
        ) {
            return PageType.LIST
        }

        return PageType.UNKNOWN
    }

    /** 便捷判定：是否可以安全地在当前页面挑条目点开 */
    fun isSafeToPickItem(s: Signals): Boolean = classify(s) == PageType.LIST

    /**
     * 文本是否命中详情页动作词。
     * 供 service 层采集屏幕时逐节点匹配，避免把词表复制到采集代码里。
     */
    fun containsDetailAction(text: String): Boolean {
        if (text.isEmpty()) return false
        return DETAIL_ACTION_WORDS.any { text.contains(it) }
    }

    /** 文本是否命中列表页特征词 */
    fun containsListHint(text: String): Boolean {
        if (text.isEmpty()) return false
        return LIST_HINT_WORDS.any { text.contains(it) }
    }
}

package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面类型判定的单元测试。
 *
 * 这批用例直接对应真实 APP 的页面形态——尤其是
 * 「原生 RecyclerView 渲染的新闻正文页」，这正是原来只看 WebView 会误判的场景。
 */
class PageClassifierTest {

    // ---------- 强信号 ----------

    @Test
    fun `WebView 容器直接判为详情页`() {
        val s = PageClassifier.Signals(isWebViewContainer = true, listItemCount = 20)
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(s))
    }

    // ---------- 真实场景还原 ----------

    @Test
    fun `今日头条原生正文页判为详情页`() {
        // 这是原实现的致命误判点：没有 WebView，但确实是正文页
        val s = PageClassifier.Signals(
            isWebViewContainer = false,
            totalTextLength = 2400,
            maxSingleTextLength = 380,
            hasDetailActionWords = true,
            hasBackAffordance = true,
            listItemCount = 2,
        )
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(s))
        assertFalse("不该在正文页里挑条目点", PageClassifier.isSafeToPickItem(s))
    }

    @Test
    fun `新闻列表页判为列表页`() {
        val s = PageClassifier.Signals(
            isWebViewContainer = false,
            totalTextLength = 420,
            maxSingleTextLength = 32,
            hasDetailActionWords = false,
            hasBackAffordance = false,
            listItemCount = 9,
        )
        assertEquals(PageClassifier.PageType.LIST, PageClassifier.classify(s))
        assertTrue(PageClassifier.isSafeToPickItem(s))
    }

    @Test
    fun `小红书双列瀑布流判为列表页`() {
        val s = PageClassifier.Signals(
            totalTextLength = 380,
            maxSingleTextLength = 26,
            listItemCount = 12,
        )
        assertEquals(PageClassifier.PageType.LIST, PageClassifier.classify(s))
    }

    @Test
    fun `知乎回答详情页判为详情页`() {
        val s = PageClassifier.Signals(
            totalTextLength = 5200,
            maxSingleTextLength = 900,
            hasDetailActionWords = true,
            listItemCount = 3
        )
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(s))
    }

    // ---------- 中等信号计分 ----------

    @Test
    fun `单个中等信号不足以定案`() {
        // 只有「条目少」一个信号：可能是刚进页面还没加载完，不该草率判定
        val s = PageClassifier.Signals(
            totalTextLength = 120,
            maxSingleTextLength = 20,
            listItemCount = 2
        )
        // listItemCount<5 得 1 分，不足 2 分；也不满足 LIST 的条目数要求
        assertEquals(PageClassifier.PageType.UNKNOWN, PageClassifier.classify(s))
    }

    @Test
    fun `正文量大加长段落判详情页`() {
        // 条目充足时「正文总量大」信号不再计分（H4：信息流卡片标题+摘要很容易超 500 字），
        // 因此这里用「条目少」来体现正文形态
        val s = PageClassifier.Signals(
            totalTextLength = 800,          // 正文量大（且条目少，信号有效）
            maxSingleTextLength = 200,      // +1 长段落
            listItemCount = 3               // +1 条目少
        )
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(s))
    }

    @Test
    fun `长段落加详情动作词判详情页`() {
        val s = PageClassifier.Signals(
            totalTextLength = 300,
            maxSingleTextLength = 160,      // +1
            hasDetailActionWords = true,    // +1
            listItemCount = 7
        )
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(s))
    }

    // ---------- 保守策略 ----------

    @Test
    fun `空白页判为未知而非列表`() {
        // 宁可 UNKNOWN 不点，也不要误判成 LIST 在未知页面乱点
        val s = PageClassifier.Signals()
        assertEquals(PageClassifier.PageType.UNKNOWN, PageClassifier.classify(s))
        assertFalse(PageClassifier.isSafeToPickItem(s))
    }

    @Test
    fun `条目充足但有长段落时不判列表页`() {
        val s = PageClassifier.Signals(
            totalTextLength = 300,
            maxSingleTextLength = 400,   // 长段落，像正文
            listItemCount = 10
        )
        // 两个中等信号（长段落 + ...）不足时至少不能是 LIST
        assertTrue(PageClassifier.classify(s) != PageClassifier.PageType.LIST)
    }

    @Test
    fun `阈值边界处行为明确`() {
        val below = PageClassifier.Signals(
            totalTextLength = PageClassifier.LONG_TEXT_THRESHOLD - 1,
            maxSingleTextLength = PageClassifier.LONG_PARAGRAPH_THRESHOLD - 1,
            listItemCount = PageClassifier.FEW_ITEMS_THRESHOLD
        )
        assertEquals(PageClassifier.PageType.LIST, PageClassifier.classify(below))

        // 恰好到阈值：长段落 +1；但条目充足时「正文总量大」不计分（H4），
        // 且 maxSingleTextLength 恰好等于阈值不满足 < 阈值，因此保守判 UNKNOWN
        val atThreshold = PageClassifier.Signals(
            totalTextLength = PageClassifier.LONG_TEXT_THRESHOLD,
            maxSingleTextLength = PageClassifier.LONG_PARAGRAPH_THRESHOLD,
            listItemCount = PageClassifier.FEW_ITEMS_THRESHOLD
        )
        assertEquals(PageClassifier.PageType.UNKNOWN, PageClassifier.classify(atThreshold))

        // 同样阈值但条目少：正文量大 + 长段落 + 条目少 = 3 分，判详情页
        val atThresholdFewItems = PageClassifier.Signals(
            totalTextLength = PageClassifier.LONG_TEXT_THRESHOLD,
            maxSingleTextLength = PageClassifier.LONG_PARAGRAPH_THRESHOLD,
            listItemCount = PageClassifier.FEW_ITEMS_THRESHOLD - 1
        )
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(atThresholdFewItems))
    }

    // ---------- H4 回归：列表页不得被误判为详情页 ----------

    @Test
    fun `长列表页带评论标签不得误判为详情页`() {
        // H4 核心回归：信息流列表 12 张卡片，标题+摘要合计超过 500 字，
        // 卡片上普遍带「评论 328」「点赞」等弱词——修复前会凑齐 2 分误判 DETAIL，
        // 导致详情流在列表页空转。
        val s = PageClassifier.Signals(
            isWebViewContainer = false,
            totalTextLength = 560,          // ≥500，但条目充足（H4：不计分）
            maxSingleTextLength = 46,
            hasDetailActionWords = false,   // 弱词不再计入详情信号
            hasBackAffordance = false,
            hasListHint = true,             // 含「推荐/关注」等列表特征词
            listItemCount = 12
        )
        assertEquals(PageClassifier.PageType.LIST, PageClassifier.classify(s))
        assertTrue(PageClassifier.isSafeToPickItem(s))
    }

    @Test
    fun `列表页无返回键无强词即使字数多也判列表`() {
        val s = PageClassifier.Signals(
            totalTextLength = 900,
            maxSingleTextLength = 40,
            hasDetailActionWords = false,
            listItemCount = 15
        )
        assertEquals(PageClassifier.PageType.LIST, PageClassifier.classify(s))
    }

    @Test
    fun `短段落详情页底部有推荐卡片时不得判为列表`() {
        // L4 回归：短正文详情页 + 底部「相关推荐」卡片 ≥5 张，若无详情强词会误判 LIST
        // 在正文里乱点；有强词（写评论/展开全文）时绝不允许判 LIST。
        val s = PageClassifier.Signals(
            totalTextLength = 260,
            maxSingleTextLength = 40,       // 段落都偏短
            hasDetailActionWords = true,    // 「写评论」等强词
            hasBackAffordance = true,
            listItemCount = 7               // 底部推荐卡片
        )
        assertTrue(PageClassifier.classify(s) != PageClassifier.PageType.LIST)
        assertFalse(PageClassifier.isSafeToPickItem(s))
    }

    @Test
    fun `列表特征词抵消详情倾向`() {
        // hasListHint 与详情判定互斥：即便字数偏多，带「为你推荐」的列表也不判 DETAIL
        val s = PageClassifier.Signals(
            totalTextLength = 520,
            maxSingleTextLength = 90,
            hasDetailActionWords = false,
            hasListHint = true,
            listItemCount = 8
        )
        assertTrue(PageClassifier.classify(s) != PageClassifier.PageType.DETAIL)
    }

    // ---------- 词表匹配 ----------

    @Test
    fun `详情动作词匹配正确`() {
        assertTrue(PageClassifier.containsDetailAction("写评论"))
        assertTrue(PageClassifier.containsDetailAction("说点什么..."))
        assertTrue(PageClassifier.containsDetailAction("查看全部评论 328"))
        assertFalse(PageClassifier.containsDetailAction("推荐"))
        assertFalse(PageClassifier.containsDetailAction(""))
    }

    @Test
    fun `列表特征词匹配正确`() {
        assertTrue(PageClassifier.containsListHint("为你推荐"))
        assertTrue(PageClassifier.containsListHint("换一批"))
        assertFalse(PageClassifier.containsListHint("责任编辑：张三"))
        assertFalse(PageClassifier.containsListHint(""))
    }
}

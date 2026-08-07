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
            clickableCount = 14
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
            clickableCount = 25
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
            clickableCount = 30
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
    fun `两个中等信号即可判详情页`() {
        val s = PageClassifier.Signals(
            totalTextLength = 800,          // +1 正文量大
            maxSingleTextLength = 200,      // +1 长段落
            listItemCount = 8               // 条目充足，不加分
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

        val atThreshold = PageClassifier.Signals(
            totalTextLength = PageClassifier.LONG_TEXT_THRESHOLD,
            maxSingleTextLength = PageClassifier.LONG_PARAGRAPH_THRESHOLD,
            listItemCount = PageClassifier.FEW_ITEMS_THRESHOLD
        )
        assertEquals(PageClassifier.PageType.DETAIL, PageClassifier.classify(atThreshold))
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

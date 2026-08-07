package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景自动识别的单元测试。
 */
class SceneDetectorTest {

    // ---------- 精确匹配 ----------

    @Test
    fun `主流短视频应用识别为短视频场景`() {
        assertEquals(SceneIds.SHORT_VIDEO, SceneDetector.sceneOf("com.ss.android.ugc.aweme"))
        assertEquals(SceneIds.SHORT_VIDEO, SceneDetector.sceneOf("com.smile.gifmaker"))
        assertEquals(SceneIds.SHORT_VIDEO, SceneDetector.sceneOf("com.kuaishou.nebula"))
    }

    @Test
    fun `主流新闻应用识别为新闻场景`() {
        assertEquals(SceneIds.NEWS, SceneDetector.sceneOf("com.ss.android.article.news"))
        assertEquals(SceneIds.NEWS, SceneDetector.sceneOf("com.tencent.news"))
        assertEquals(SceneIds.NEWS, SceneDetector.sceneOf("com.sina.news"))
    }

    @Test
    fun `小说阅读应用识别为小说场景`() {
        assertEquals(SceneIds.NOVEL, SceneDetector.sceneOf("com.dragon.read"))
        assertEquals(SceneIds.NOVEL, SceneDetector.sceneOf("com.qidian.QDReader"))
    }

    @Test
    fun `社交应用识别为社交场景`() {
        assertEquals(SceneIds.SOCIAL, SceneDetector.sceneOf("com.xingin.xhs"))
        assertEquals(SceneIds.SOCIAL, SceneDetector.sceneOf("com.sina.weibo"))
        assertEquals(SceneIds.SOCIAL, SceneDetector.sceneOf("com.zhihu.android"))
    }

    @Test
    fun `直播应用识别为直播场景`() {
        assertEquals(SceneIds.LIVE, SceneDetector.sceneOf("com.duowan.kiwi"))
        assertEquals(SceneIds.LIVE, SceneDetector.sceneOf("air.tv.douyu.android"))
    }

    // ---------- 前缀匹配 ----------

    @Test
    fun `马甲包通过前缀命中`() {
        // 抖音火山版之类的变体包名，精确表里没有但前缀能兜住
        assertEquals(
            SceneIds.SHORT_VIDEO,
            SceneDetector.sceneOf("com.ss.android.ugc.aweme.huoshan")
        )
        assertEquals(SceneIds.SHORT_VIDEO, SceneDetector.sceneOf("com.kuaishou.something"))
        assertEquals(SceneIds.NEWS, SceneDetector.sceneOf("com.ss.android.article.video"))
    }

    @Test
    fun `新浪微博前缀不误伤新浪新闻`() {
        // com.sina.news 精确命中新闻，com.sina.weibo 前缀命中社交
        assertEquals(SceneIds.NEWS, SceneDetector.sceneOf("com.sina.news"))
        assertEquals(SceneIds.SOCIAL, SceneDetector.sceneOf("com.sina.weibo.lite"))
    }

    // ---------- 忽略规则 ----------

    @Test
    fun `系统界面被忽略`() {
        assertTrue(SceneDetector.isIgnored("com.android.systemui"))
        assertTrue(SceneDetector.isIgnored("com.android.settings"))
        assertNull(SceneDetector.sceneOf("com.android.systemui"))
    }

    @Test
    fun `各家桌面被忽略`() {
        listOf(
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.bbk.launcher2",
            "com.sec.android.app.launcher",
            "com.android.launcher3"
        ).forEach {
            assertTrue("$it 应被忽略", SceneDetector.isIgnored(it))
        }
    }

    @Test
    fun `本应用自身被忽略`() {
        assertTrue(SceneDetector.isIgnored("cn.ggdoc.autoscroll"))
        assertNull(SceneDetector.sceneOf("cn.ggdoc.autoscroll"))
    }

    @Test
    fun `空包名被忽略`() {
        assertTrue(SceneDetector.isIgnored(null))
        assertTrue(SceneDetector.isIgnored(""))
        assertNull(SceneDetector.sceneOf(null))
        assertNull(SceneDetector.sceneOf(""))
    }

    @Test
    fun `未知应用返回 null 保持当前场景`() {
        assertNull(SceneDetector.sceneOf("com.some.unknown.app"))
    }

    // ---------- shouldSwitch ----------

    @Test
    fun `识别到不同场景时应切换`() {
        assertTrue(SceneDetector.shouldSwitch(SceneIds.SHORT_VIDEO, "com.tencent.news"))
    }

    @Test
    fun `已经是目标场景时不重复切换`() {
        assertFalse(SceneDetector.shouldSwitch(SceneIds.NEWS, "com.tencent.news"))
    }

    @Test
    fun `自定义场景永不被自动覆盖`() {
        // 用户手工编排了手势序列，自动切换会让配置失效
        assertFalse(SceneDetector.shouldSwitch(SceneIds.CUSTOM, "com.tencent.news"))
        assertFalse(SceneDetector.shouldSwitch(SceneIds.CUSTOM, "com.ss.android.ugc.aweme"))
    }

    @Test
    fun `无法识别的包名不触发切换`() {
        assertFalse(SceneDetector.shouldSwitch(SceneIds.NEWS, "com.unknown.app"))
        assertFalse(SceneDetector.shouldSwitch(SceneIds.NEWS, "com.android.systemui"))
        assertFalse(SceneDetector.shouldSwitch(SceneIds.NEWS, null))
    }

    // ---------- SceneIds ----------

    @Test
    fun `场景 ID 常量集合完整`() {
        assertEquals(6, SceneIds.ALL.size)
        assertTrue(SceneIds.isValid(SceneIds.SHORT_VIDEO))
        assertTrue(SceneIds.isValid("news"))
        assertFalse(SceneIds.isValid("not_a_scene"))
        assertFalse(SceneIds.isValid(null))
    }

    @Test
    fun `映射表中的场景 ID 全部合法`() {
        // 防止映射表里写了错别字导致场景查不到
        listOf(
            "com.ss.android.ugc.aweme", "com.tencent.news", "com.dragon.read",
            "com.xingin.xhs", "com.duowan.kiwi", "com.kuaishou.nebula"
        ).forEach { pkg ->
            val scene = SceneDetector.sceneOf(pkg)
            assertTrue("$pkg 映射到非法场景 $scene", SceneIds.isValid(scene))
        }
    }
}

package cn.ggdoc.autoscroll.config

import cn.ggdoc.autoscroll.human.SceneIds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 场景模板与「自动识别」解析逻辑测试（纯 JVM）。
 *
 * 覆盖：内置包名映射、未命中回退、非自动场景原样返回。
 */
class SceneConfigTest {

    @Test
    fun `自动识别命中短视频包名`() {
        val scene = SceneConfig.resolveScene(SceneIds.AUTO, "com.ss.android.ugc.aweme")
        assertEquals(SceneIds.SHORT_VIDEO, scene.id)
    }

    @Test
    fun `自动识别命中新闻与小说包名`() {
        assertEquals(SceneIds.NEWS, SceneConfig.resolveScene(SceneIds.AUTO, "com.ss.android.article.news").id)
        assertEquals(SceneIds.NOVEL, SceneConfig.resolveScene(SceneIds.AUTO, "com.dragon.read").id)
        assertEquals(SceneIds.SOCIAL, SceneConfig.resolveScene(SceneIds.AUTO, "com.xingin.xhs").id)
        assertEquals(SceneIds.LIVE, SceneConfig.resolveScene(SceneIds.AUTO, "com.duowan.mobile").id)
    }

    @Test
    fun `自动识别未命中包名回退通用场景`() {
        val scene = SceneConfig.resolveScene(SceneIds.AUTO, "com.example.unknown")
        assertEquals(SceneIds.CUSTOM, scene.id)
        // 前台包名为空同样回退
        assertEquals(SceneIds.CUSTOM, SceneConfig.resolveScene(SceneIds.AUTO, null).id)
    }

    @Test
    fun `非自动场景原样返回不受包名影响`() {
        assertEquals(
            SceneIds.SHORT_VIDEO,
            SceneConfig.resolveScene(SceneIds.SHORT_VIDEO, "com.ss.android.article.news").id
        )
        assertEquals(
            SceneIds.NOVEL,
            SceneConfig.resolveScene(SceneIds.NOVEL, "com.ss.android.ugc.aweme").id
        )
    }

    @Test
    fun `自动识别场景存在于场景列表且默认行为安全`() {
        val auto = SceneConfig.getAllScenes().first { it.id == SceneIds.AUTO }
        // 自动识别场景本身不启用点赞、不做详情流（具体行为由解析后的实际场景决定）
        assertEquals(false, auto.supportAutoLike)
        assertEquals(false, auto.useDetailFlow)
    }

    @Test
    fun `未知场景 id 回退自定义场景`() {
        assertEquals(SceneIds.CUSTOM, SceneConfig.getScene("nonexistent").id)
    }
}

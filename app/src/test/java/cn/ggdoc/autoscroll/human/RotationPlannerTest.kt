package cn.ggdoc.autoscroll.human

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP 轮换规划器的单元测试。
 *
 * 核心场景：某个 APP 没装 / 起不来时，不能一直浪费轮换周期去切它，
 * 但也不能因为一次网络抖动就永久放弃。
 */
class RotationPlannerTest {

    private val pkgs = listOf(
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.xingin.xhs"
    )

    @Test
    fun `按顺序循环取包名`() {
        val p = RotationPlanner(pkgs)
        assertEquals(pkgs[0], p.next())
        assertEquals(pkgs[1], p.next())
        assertEquals(pkgs[2], p.next())
        assertEquals("应回到第一个形成循环", pkgs[0], p.next())
    }

    @Test
    fun `空列表返回 null`() {
        val p = RotationPlanner(emptyList())
        assertTrue(p.isEmpty)
        assertNull(p.next())
    }

    @Test
    fun `重复包名被去重`() {
        val p = RotationPlanner(listOf("a", "a", "b"))
        assertEquals(2, p.availablePackages.size)
    }

    @Test
    fun `连续失败 3 次后临时下线`() {
        val p = RotationPlanner(pkgs)
        val bad = pkgs[1]
        assertFalse(p.markFailure(bad))  // 1 次
        assertFalse(p.markFailure(bad))  // 2 次
        assertTrue("第 3 次应下线", p.markFailure(bad))
        assertFalse("下线后不该出现在可用列表", p.availablePackages.contains(bad))
    }

    @Test
    fun `下线的包名会被跳过`() {
        val p = RotationPlanner(pkgs)
        repeat(3) { p.markFailure(pkgs[1]) }
        val picked = List(6) { p.next() }
        assertFalse("下线的包名仍被选中", picked.contains(pkgs[1]))
        assertTrue(picked.contains(pkgs[0]))
        assertTrue(picked.contains(pkgs[2]))
    }

    @Test
    fun `成功后失败计数清零`() {
        val p = RotationPlanner(pkgs)
        val target = pkgs[0]
        p.markFailure(target)
        p.markFailure(target)
        assertEquals(2, p.failureCount(target))
        p.markSuccess(target)
        assertEquals("成功后计数应清零", 0, p.failureCount(target))
        // 清零后需要重新累计 3 次才下线
        assertFalse(p.markFailure(target))
    }

    @Test
    fun `全部下线时自动复活避免彻底停摆`() {
        val p = RotationPlanner(pkgs)
        pkgs.forEach { pkg -> repeat(3) { p.markFailure(pkg) } }
        assertTrue("此时应无可用候选", p.availablePackages.isEmpty())
        // 但 next() 必须能返回值——可能只是当时系统忙，不该永久放弃
        val revived = p.next()
        assertTrue("全下线后应自动复活", revived in pkgs)
        assertEquals("复活后所有候选都应可用", pkgs.size, p.availablePackages.size)
    }

    @Test
    fun `reset 清空全部状态`() {
        val p = RotationPlanner(pkgs)
        repeat(3) { p.markFailure(pkgs[0]) }
        p.next()
        p.reset()
        assertEquals(pkgs.size, p.availablePackages.size)
        assertEquals("重置后应从头开始", pkgs[0], p.next())
    }

    // ---------- 切换成功判定 ----------

    @Test
    fun `包名完全一致视为切换成功`() {
        val p = RotationPlanner(pkgs)
        assertTrue(p.isSwitchSuccessful("com.tencent.news", "com.tencent.news"))
    }

    @Test
    fun `子包名视为切换成功`() {
        // 部分 APP 的开屏/推送页跑在子包名进程里
        val p = RotationPlanner(pkgs)
        assertTrue(p.isSwitchSuccessful("com.tencent.news", "com.tencent.news.splash"))
    }

    @Test
    fun `前台是其他应用视为失败`() {
        val p = RotationPlanner(pkgs)
        assertFalse(p.isSwitchSuccessful("com.tencent.news", "com.android.systemui"))
        assertFalse(p.isSwitchSuccessful("com.tencent.news", "com.tencent.newsreader"))
    }

    @Test
    fun `前台包名为空视为失败`() {
        val p = RotationPlanner(pkgs)
        assertFalse(p.isSwitchSuccessful("com.tencent.news", null))
        assertFalse(p.isSwitchSuccessful("com.tencent.news", ""))
        assertFalse(p.isSwitchSuccessful("com.tencent.news", "   "))
    }

    @Test
    fun `单个候选下线后仍能复活自身`() {
        val p = RotationPlanner(listOf("only.one.app"))
        repeat(3) { p.markFailure("only.one.app") }
        assertEquals("单候选全下线后应复活", "only.one.app", p.next())
    }
}

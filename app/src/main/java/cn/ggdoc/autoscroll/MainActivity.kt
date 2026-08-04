package cn.ggdoc.autoscroll

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import cn.ggdoc.autoscroll.ui.ControlFragment
import cn.ggdoc.autoscroll.ui.SceneFragment
import cn.ggdoc.autoscroll.ui.TaskFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 主界面 v3.0：TabLayout + ViewPager2 三页
 *  - 控制：状态 + 基础参数 + 权限引导
 *  - 场景：6 大内容场景选择
 *  - 任务：自动点赞 / 广告屏蔽 / 定时停止 / 多APP轮换 / 屏幕常亮
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2

    private val fragments: List<Fragment> = listOf(
        ControlFragment(),
        SceneFragment(),
        TaskFragment()
    )

    private val tabTitles = intArrayOf(
        R.string.tab_control,
        R.string.tab_scene,
        R.string.tab_task
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(tabTitles[position])
        }.attach()
    }
}

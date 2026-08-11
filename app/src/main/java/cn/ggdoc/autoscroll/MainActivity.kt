package cn.ggdoc.autoscroll

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cn.ggdoc.autoscroll.ui.ControlFragment
import cn.ggdoc.autoscroll.ui.TaskFragment
import cn.ggdoc.autoscroll.ui.RecorderFragment
import cn.ggdoc.autoscroll.util.CrashMonitor
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主界面：底部导航（控制 / 任务 / 记录器）+ ViewPager2 三页。
 * 场景选择已移入控制页的设置弹窗，不再单独占一个 Tab。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var viewPager: ViewPager2

    private val fragments: List<Fragment> = listOf(
        ControlFragment(),
        TaskFragment(),
        RecorderFragment()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashMonitor.install(applicationContext)
        setContentView(R.layout.activity_main)

        requestNotificationPermission()

        bottomNav = findViewById(R.id.bottomNav)
        viewPager = findViewById(R.id.viewPager)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        // 关闭 ViewPager 的滑动切换，完全由底部导航驱动，体验更稳
        viewPager.isUserInputEnabled = false

        bottomNav.setOnItemSelectedListener { item ->
            viewPager.currentItem = when (item.itemId) {
                R.id.nav_task -> 1
                R.id.nav_recorder -> 2
                else -> 0
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val targetId = when (position) {
                    1 -> R.id.nav_task
                    2 -> R.id.nav_recorder
                    else -> R.id.nav_control
                }
                if (bottomNav.selectedItemId != targetId) {
                    bottomNav.selectedItemId = targetId
                }
            }
        })
    }

    /** Android 13+ 需要运行时授予通知权限，否则前台服务通知不会显示 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}

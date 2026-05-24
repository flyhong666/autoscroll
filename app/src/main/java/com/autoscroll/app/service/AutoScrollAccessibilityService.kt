package cn.ggdoc.autoscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

class AutoScrollAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AutoScrollService"
        const val ACTION_TOGGLE_SCROLL = "cn.ggdoc.autoscroll.TOGGLE_SCROLL"
        
        var instance: AutoScrollAccessibilityService? = null
            private set
        
        var isScrolling = false
            private set
        
        var minIntervalSeconds = 3
        var maxIntervalSeconds = 20
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scrollTask: Runnable? = null
    private var lastScrollTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听窗口变化，确保在正确的应用中
        event?.let {
            val packageName = it.packageName?.toString() ?: return
            // 可以在这里添加特定应用的过滤
            Log.d(TAG, "当前应用：$packageName")
        }
    }

    override fun onInterrupt() {
        stopScrolling()
    }

    fun startScrolling() {
        if (isScrolling) {
            Log.w(TAG, "已经在滚动中")
            return
        }

        isScrolling = true
        Log.d(TAG, "开始自动滚动")
        
        scheduleNextScroll()
    }

    fun stopScrolling() {
        isScrolling = false
        scrollTask?.let {
            handler.removeCallbacks(it)
            scrollTask = null
        }
        Log.d(TAG, "停止自动滚动")
    }

    private fun scheduleNextScroll() {
        if (!isScrolling) return

        // 随机延迟时间（在最小和最大间隔之间）
        val delaySeconds = Random.nextInt(minIntervalSeconds, maxIntervalSeconds + 1)
        val delayMillis = delaySeconds * 1000L

        scrollTask = Runnable {
            performScroll()
            scheduleNextScroll()
        }

        handler.postDelayed(scrollTask!!, delayMillis)
    }

    private fun performScroll() {
        val currentTime = System.currentTimeMillis()
        
        // 检查是否距离上次滚动太近
        if (currentTime - lastScrollTime < 1000) {
            Log.d(TAG, "滚动间隔太短，跳过")
            return
        }

        try {
            // 获取当前窗口内容
            val rootNode = rootInActiveWindow ?: return
            
            // 查找可滚动的节点
            val scrollableNode = findScrollableNode(rootNode)
            
            if (scrollableNode != null) {
                // 在找到的节点上执行滑动
                performGestureOnNode(scrollableNode)
            } else {
                // 如果没有找到可滚动节点，使用屏幕手势
                performScreenGesture()
            }
            
            lastScrollTime = currentTime
            Log.d(TAG, "执行滑动操作")
            
        } catch (e: Exception) {
            Log.e(TAG, "滑动失败", e)
            // 如果节点操作失败，尝试使用屏幕手势
            performScreenGesture()
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 优先查找 RecyclerView、ListView 等
        val commonScrollableClasses = listOf(
            "androidx.recyclerview.widget.RecyclerView",
            "android.support.v7.widget.RecyclerView",
            "android.widget.ListView",
            "android.widget.ScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.webkit.WebView",
            "android.support.v4.view.ViewPager",
            "androidx.viewpager.widget.ViewPager"
        )

        // BFS 搜索可滚动节点
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.offer(node)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            
            // 检查是否是常见可滚动类
            val className = current.className?.toString() ?: ""
            if (commonScrollableClasses.any { className.contains(it) }) {
                return current
            }

            // 检查是否可以垂直滚动
            if (current.isScrollable) {
                return current
            }

            // 添加子节点到队列
            for (i in 0 until current.childCount) {
                val child = current.getChild(i)
                if (child != null) {
                    queue.offer(child)
                }
            }
        }

        return null
    }

    private fun performGestureOnNode(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 计算滑动的起点和终点（增加滑动距离）
        val centerX = (rect.left + rect.right) / 2.0f
        val startY = (rect.top + rect.bottom) * 0.85f  // 从更下方开始
        val endY = (rect.top + rect.bottom) * 0.15f    // 滑到更上方

        // 添加随机偏移，模拟人类行为
        val randomX = centerX + Random.nextFloat() * (rect.right - rect.left) * 0.4f - 
                      (rect.right - rect.left) * 0.2f
        val randomStartY = startY + Random.nextFloat() * 50 - 25
        val randomEndY = endY + Random.nextFloat() * 50 - 25

        // 随机滑动时间（300-450ms），更接近人类操作
        val duration = Random.nextInt(300, 451).toLong()

        val path = Path()
        path.moveTo(randomX, randomStartY)
        path.lineTo(randomX, randomEndY)

        val gestureDescription = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gestureDescription, null, null)
    }

    private fun performScreenGesture() {
        // 获取屏幕尺寸
        val display = display ?: return
        val width = display.width.toFloat()
        val height = display.height.toFloat()

        // 计算滑动路径（从下往上，增加滑动距离）
        val centerX = width / 2
        val startY = height * 0.85f  // 从更下方开始
        val endY = height * 0.15f    // 滑到更上方

        // 添加随机水平偏移，使滑动更自然
        val randomOffsetX = Random.nextFloat() * width * 0.3f - width * 0.15f
        val randomStartY = startY + Random.nextFloat() * 100 - 50
        val randomEndY = endY + Random.nextFloat() * 100 - 50

        // 随机滑动时间（350-500ms），确保滑动被识别
        val duration = Random.nextInt(350, 501).toLong()

        val path = Path()
        path.moveTo(centerX + randomOffsetX, randomStartY)
        path.lineTo(centerX + randomOffsetX, randomEndY)

        val gestureDescription = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gestureDescription, null, null)
    }

    fun updateInterval(min: Int, max: Int) {
        minIntervalSeconds = min
        maxIntervalSeconds = max
        Log.d(TAG, "更新间隔：$min - $max 秒")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScrolling()
        instance = null
    }
}

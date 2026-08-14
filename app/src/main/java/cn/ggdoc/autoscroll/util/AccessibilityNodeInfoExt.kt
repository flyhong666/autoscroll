package cn.ggdoc.autoscroll.util

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android 13 起 AccessibilityNodeInfo 不再使用对象池，recycle() 已无实际作用。
 * 低版本仍保留显式回收，避免节点池被耗尽。
 */
fun AccessibilityNodeInfo?.recycleCompat() {
    if (this != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        recycle()
    }
}

package cn.ggdoc.autoscroll.config

import cn.ggdoc.autoscroll.R

/**
 * 自定义通用场景的「手势序列」中的一步。
 *
 * 支持的手势：
 * - [TYPE_TAP] 单击
 * - [TYPE_DOUBLE_TAP] 双击
 * - [TYPE_SWIPE_UP] / [TYPE_SWIPE_DOWN] / [TYPE_SWIPE_LEFT] / [TYPE_SWIPE_RIGHT] 滑动
 * - [TYPE_WAIT] 仅等待（不执行手势，只空等若干秒）
 *
 * @param gesture 手势类型
 * @param waitSec 该步执行完后等待的秒数（下一手势前的间隔）
 * @param xPct 点击/滑动起点横向位置（屏幕宽度百分比，5-95）
 * @param yPct 点击/滑动起点纵向位置（屏幕高度百分比，5-95）
 * @param distPct 滑动距离占屏幕百分比（仅滑动手势使用，5-95）
 */
data class CustomGestureStep(
    val gesture: String,
    val waitSec: Int = 2,
    val xPct: Int = 50,
    val yPct: Int = 50,
    val distPct: Int = 70
) {
    fun isSwipe(): Boolean = gesture in setOf(
        TYPE_SWIPE_UP, TYPE_SWIPE_DOWN, TYPE_SWIPE_LEFT, TYPE_SWIPE_RIGHT
    )

    fun isWaitOnly(): Boolean = gesture == TYPE_WAIT

    /** 列表 / 详情展示用的一行文案 */
    fun summary(): String {
        val g = gestureName()
        return if (isWaitOnly()) {
            "等待 $waitSec 秒"
        } else {
            "$g · 等待 $waitSec 秒"
        }
    }

    fun gestureName(): String = when (gesture) {
        TYPE_TAP -> "单击"
        TYPE_DOUBLE_TAP -> "双击"
        TYPE_SWIPE_UP -> "上滑"
        TYPE_SWIPE_DOWN -> "下滑"
        TYPE_SWIPE_LEFT -> "左滑"
        TYPE_SWIPE_RIGHT -> "右滑"
        TYPE_WAIT -> "等待"
        else -> gesture
    }

    companion object {
        const val TYPE_TAP = "tap"
        const val TYPE_DOUBLE_TAP = "double_tap"
        const val TYPE_SWIPE_UP = "swipe_up"
        const val TYPE_SWIPE_DOWN = "swipe_down"
        const val TYPE_SWIPE_LEFT = "swipe_left"
        const val TYPE_SWIPE_RIGHT = "swipe_right"
        const val TYPE_WAIT = "wait"

        val GESTURE_TYPES = listOf(
            TYPE_TAP, TYPE_DOUBLE_TAP, TYPE_SWIPE_UP,
            TYPE_SWIPE_DOWN, TYPE_SWIPE_LEFT, TYPE_SWIPE_RIGHT, TYPE_WAIT
        )

        val GESTURE_LABELS = listOf(
            "单击", "双击", "上滑", "下滑", "左滑", "右滑", "仅等待"
        )

        /** 把序列序列化为一行文本存储（无外部依赖） */
        fun serialize(steps: List<CustomGestureStep>): String =
            steps.joinToString(";") { s ->
                "${s.gesture},${s.waitSec},${s.xPct},${s.yPct},${s.distPct}"
            }

        fun deserialize(text: String?): List<CustomGestureStep> {
            if (text.isNullOrBlank()) return emptyList()
            return text.split(";").mapNotNull { item ->
                val p = item.split(",")
                if (p.size < 5) return@mapNotNull null
                CustomGestureStep(
                    gesture = p[0],
                    waitSec = p[1].toIntOrNull()?.coerceIn(0, 600) ?: 2,
                    xPct = p[2].toIntOrNull()?.coerceIn(5, 95) ?: 50,
                    yPct = p[3].toIntOrNull()?.coerceIn(5, 95) ?: 50,
                    distPct = p[4].toIntOrNull()?.coerceIn(5, 95) ?: 70
                )
            }.filter { it.gesture in GESTURE_TYPES }
        }
    }
}

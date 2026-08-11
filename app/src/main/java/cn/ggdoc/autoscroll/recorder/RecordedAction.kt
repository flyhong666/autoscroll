package cn.ggdoc.autoscroll.recorder

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条可回放的操作步骤。
 *
 * 实现说明：无障碍服务拿不到原始触摸事件（MotionEvent），
 * 因此录制基于「视图动作」——点击/长按取控件在屏幕上的中心点，
 * 滚动取控件区域内的等效滑动轨迹；回放时统一用 dispatchGesture 还原。
 */
data class RecordedAction(
    /** click / longClick / swipe / wait */
    val type: String,
    val x: Int = 0,
    val y: Int = 0,
    /** swipe 终点 */
    val x2: Int = 0,
    val y2: Int = 0,
    /** 手势持续时间（ms） */
    val duration: Long = 0L,
    /** 距上一步的等待时间（ms） */
    val delay: Long = 0L,
    /** 可读描述（控件文字 / 类名），仅用于展示 */
    val desc: String = ""
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("x", x)
        put("y", y)
        if (type == TYPE_SWIPE) {
            put("x2", x2)
            put("y2", y2)
        }
        put("duration", duration)
        put("delay", delay)
        if (desc.isNotEmpty()) put("desc", desc)
    }

    /** 脚本详情里展示的一行摘要 */
    fun readable(index: Int): String {
        val head = when (type) {
            TYPE_CLICK -> "点击 ($x, $y)"
            TYPE_LONG_CLICK -> "长按 ($x, $y)"
            TYPE_SWIPE -> "滑动 ($x, $y) → ($x2, $y2)"
            TYPE_WAIT -> "等待 ${duration}ms"
            TYPE_DOUBLE_TAP -> "双击 ($x, $y)"
            else -> type
        }
        val tail = if (desc.isNotEmpty()) "「$desc」" else ""
        return "${index + 1}. 等待${delay}ms · $head $tail"
    }

    companion object {
        const val TYPE_CLICK = "click"
        const val TYPE_LONG_CLICK = "longClick"
        const val TYPE_SWIPE = "swipe"
        const val TYPE_WAIT = "wait"
        const val TYPE_DOUBLE_TAP = "doubleTap"

        fun fromJson(o: JSONObject): RecordedAction = RecordedAction(
            type = o.optString("type", TYPE_CLICK),
            x = o.optInt("x"),
            y = o.optInt("y"),
            x2 = o.optInt("x2"),
            y2 = o.optInt("y2"),
            duration = o.optLong("duration"),
            delay = o.optLong("delay"),
            desc = o.optString("desc", "")
        )
    }
}

/** 一份完整脚本（对应磁盘上的一个 .json 文件） */
data class RecordedScript(
    val name: String,
    val createdAt: Long,
    /** 录制时的目标应用包名 */
    val pkg: String,
    val actions: List<RecordedAction>
) {

    /** 单次回放的预计耗时（ms） */
    val estimatedMs: Long
        get() = actions.sumOf { it.delay + it.duration }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", RecordedScript.VERSION)
        put("name", name)
        put("createdAt", createdAt)
        put("pkg", pkg)
        put("actions", JSONArray().also { arr -> actions.forEach { arr.put(it.toJson()) } })
    }

    fun toPrettyString(): String = toJson().toString(2)

    companion object {
        const val VERSION = 1

        fun fromJson(o: JSONObject): RecordedScript {
            val arr = o.optJSONArray("actions") ?: JSONArray()
            val list = ArrayList<RecordedAction>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(RecordedAction.fromJson(it)) }
            }
            return RecordedScript(
                name = o.optString("name", "未命名脚本"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                pkg = o.optString("pkg", ""),
                actions = list
            )
        }
    }
}

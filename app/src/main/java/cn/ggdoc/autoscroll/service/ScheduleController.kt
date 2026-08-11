package cn.ggdoc.autoscroll.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import cn.ggdoc.autoscroll.human.ScheduleUtils

/**
 * 定时运行（多时段窗口）控制器。
 *
 * 从 [AutoScrollAccessibilityService] 抽离：
 *  - 精确/非精确闹钟安排与取消（每个窗口一对 开始/结束 闹钟）
 *  - 开始 / 结束时刻触发自动启停
 *  - 窗口内判定 isWithinWindow（支持跨午夜、多窗口）
 *
 *  多时段数据源为 [ServiceFace.scheduleWindows]（来自 AppConfig 的 schedule_windows）。
 */
class ScheduleController(
    private val context: Context,
    private val serviceProvider: ServiceFace
) {

    interface ServiceFace {
        val TAG: String get() = AutoScrollAccessibilityService.TAG
        val scheduleEnabled: Boolean
        val scheduleWindows: List<Pair<Int, Int>>
        val isScrolling: Boolean
        fun sendBroadcast(intent: Intent)
        fun startScrolling()
        fun stopScrolling()
        fun formatMinute(min: Int): String = ScheduleUtils.formatMinute(min)
    }

    /** 生效的多时段窗口列表（scheduleEnabled 关闭时为空） */
    val scheduleWindowPairs: List<Pair<Int, Int>>
        get() = if (serviceProvider.scheduleEnabled) {
            serviceProvider.scheduleWindows
        } else emptyList()

    fun onScheduleConfigChanged() {
        if (serviceProvider.scheduleEnabled) scheduleAlarms() else cancelAlarms()
    }

    fun autoStartBySchedule() {
        if (!serviceProvider.scheduleEnabled) return
        if (isWithinWindow(ScheduleUtils.nowMinute()) && !serviceProvider.isScrolling) {
            serviceProvider.startScrolling()
            Toast.makeText(context, "定时开始：自动滚动已启动", Toast.LENGTH_SHORT).show()
        }
    }

    fun autoStopBySchedule() {
        if (serviceProvider.isScrolling) {
            serviceProvider.stopScrolling()
            Toast.makeText(context, "定时结束：已自动停止", Toast.LENGTH_SHORT).show()
        }
    }

    /** 当前时间是否处于任意一个生效窗口内（支持跨午夜） */
    fun isWithinWindow(nowMin: Int = ScheduleUtils.nowMinute()): Boolean {
        if (!serviceProvider.scheduleEnabled) return true
        val windows = scheduleWindowPairs
        // 开启了定时但未设置任何时段时，视为「全天生效」
        if (windows.isEmpty()) return true
        return windows.any { (s, e) -> ScheduleUtils.isWithinWindow(nowMin, s, e) }
    }

    /** 安排下一次开始/结束闹钟（每日精确触发）；未授权精确闹钟时降级为普通闹钟 */
    fun scheduleAlarms() {
        cancelAlarms()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            for ((idx, pair) in scheduleWindowPairs.withIndex()) {
                val (startMin, endMin) = pair
                val startReq = 1 + idx * 2
                val endReq = startReq + 1
                if (canExact) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        ScheduleUtils.nextAlarmMillis(startMin),
                        makePendingIntent(AutoScrollAccessibilityService.ACTION_SCHEDULE_START, startReq)
                    )
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        ScheduleUtils.nextAlarmMillis(endMin),
                        makePendingIntent(AutoScrollAccessibilityService.ACTION_SCHEDULE_END, endReq)
                    )
                } else {
                    Log.w(serviceProvider.TAG, "无精确闹钟权限，降级为普通闹钟（窗口 #$idx）")
                    am.set(
                        AlarmManager.RTC_WAKEUP,
                        ScheduleUtils.nextAlarmMillis(startMin),
                        makePendingIntent(AutoScrollAccessibilityService.ACTION_SCHEDULE_START, startReq)
                    )
                    am.set(
                        AlarmManager.RTC_WAKEUP,
                        ScheduleUtils.nextAlarmMillis(endMin),
                        makePendingIntent(AutoScrollAccessibilityService.ACTION_SCHEDULE_END, endReq)
                    )
                }
                Log.i(
                    serviceProvider.TAG,
                    "定时闹钟已安排窗口#$idx：${serviceProvider.formatMinute(startMin)} ~ ${serviceProvider.formatMinute(endMin)}"
                )
            }
        } catch (e: Exception) {
            Log.e(serviceProvider.TAG, "安排定时闹钟失败", e)
        }
    }

    fun cancelAlarms() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 单窗口：取消 req 1/2；多时段上限设 8 对留余量，PIService 对不存在的 PendingIntent 取消是安全的
        for (i in 0 until 8) {
            val startReq = 1 + i * 2
            val endReq = startReq + 1
            am.cancel(makePendingIntent(AutoScrollAccessibilityService.ACTION_SCHEDULE_START, startReq))
            am.cancel(makePendingIntent(AutoScrollAccessibilityService.ACTION_SCHEDULE_END, endReq))
        }
    }

    private fun makePendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }
}

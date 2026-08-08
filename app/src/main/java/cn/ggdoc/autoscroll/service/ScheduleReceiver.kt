package cn.ggdoc.autoscroll.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 接收定时运行闹钟广播，触发自动开始 / 停止滚动。
 * 仅在无障碍服务实例存在时生效（服务未启用则忽略）。
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        // L2 修复：先记录收到广播，再执行业务。原实现在 when 之后记录，
        // 一旦 autoStartBySchedule / autoStopBySchedule 抛异常，日志不会打印且异常外泄。
        Log.i("ScheduleReceiver", "收到定时广播：$action")
        val svc = AutoScrollAccessibilityService.instance ?: return
        when (action) {
            AutoScrollAccessibilityService.ACTION_SCHEDULE_START -> svc.autoStartBySchedule()
            AutoScrollAccessibilityService.ACTION_SCHEDULE_END -> svc.autoStopBySchedule()
        }
    }
}

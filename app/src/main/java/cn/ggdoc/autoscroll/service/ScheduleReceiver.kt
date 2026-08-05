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
        val svc = AutoScrollAccessibilityService.instance ?: return
        when (action) {
            AutoScrollAccessibilityService.ACTION_SCHEDULE_START -> svc.autoStartBySchedule()
            AutoScrollAccessibilityService.ACTION_SCHEDULE_END -> svc.autoStopBySchedule()
        }
        Log.i("ScheduleReceiver", "收到定时广播：$action")
    }
}

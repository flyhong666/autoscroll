package cn.ggdoc.autoscroll.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService

/**
 * 桌面小部件（清单 #11）。
 *
 * 展示自动滚动的「运行状态 + 当前场景 + 今日滚动数」，点击按钮即可
 * 「开始 / 停止」切换，无需进入 App。
 *
 * 交互通过广播完成：小部件点击 -> 发 [ACTION_TOGGLE] 广播 -> 本 Provider 收到后
 * 调用 [AutoScrollAccessibilityService] 的 toggle；服务状态变化（[AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED]）
 * 时也会调用 [updateAll] 把最新状态推送回所有已放置的小部件。
 *
 * 注意：小部件运行在「桌面进程」，无法直接拿到服务的实时字段，因此状态展示依赖
 * 服务主动通过 [updateAll] 推送；若服务尚未连接，则按偏好推测（恢复标记 / 正在滚动标志）。
 */
class AutoScrollWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "cn.ggdoc.autoscroll.WIDGET_TOGGLE"

        /** 由服务在状态变化时调用，刷新所有已放置的小部件 */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AutoScrollWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val rv = buildRemoteViews(context)
            for (id in ids) mgr.updateAppWidget(id, rv)
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_autoscroll)
            val svc = AutoScrollAccessibilityService.instance
            val isRunning = svc?.let { AutoScrollAccessibilityService.isScrolling } ?: false
            val scene = if (svc != null) AutoScrollAccessibilityService.currentScene
                else AppConfig.getCurrentScene(context)
            val sceneLabel = sceneLabel(context, scene)
            val todayScrolls = svc?.getTodayStats()?.scrolls
                ?: cn.ggdoc.autoscroll.config.StatsStore.today(context).scrolls

            val status = if (isRunning) {
                context.getString(R.string.widget_status_running, sceneLabel)
            } else {
                context.getString(R.string.widget_status_stopped)
            }
            rv.setTextViewText(R.id.tvWidgetStatus, status)
            rv.setTextViewText(
                R.id.btnWidgetToggle,
                if (isRunning) context.getString(R.string.widget_stop)
                else context.getString(R.string.widget_start)
            )

            val toggleIntent = Intent(context, AutoScrollWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(context, 0, toggleIntent, flags)
            rv.setOnClickPendingIntent(R.id.btnWidgetToggle, pi)
            rv.setOnClickPendingIntent(R.id.tvWidgetStatus, pi)
            return rv
        }

        private fun sceneLabel(context: Context, scene: String): String {
            val res = context.resources
            val id = res.getIdentifier("scene_${scene}_label", "string", context.packageName)
            return if (id != 0) res.getString(id) else scene
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 初始放置 / 周期性刷新时，按当前状态重建视图
        val rv = buildRemoteViews(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, rv)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val svc = AutoScrollAccessibilityService.instance
                if (svc != null) {
                    if (AutoScrollAccessibilityService.isScrolling) svc.stopScrolling()
                    else svc.startScrolling()
                    // M9 修复：start/stop 内部会触发 STATE_CHANGED 广播（下方分支 updateAll）
                    // 并强制刷新小部件，这里不再重复刷新，避免一次点击两次 updateAll。
                } else {
                    // 服务未连接：无法切换，仅刷新提示当前不可用
                    updateAll(context)
                }
            }
            AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED -> {
                // 服务状态变化（开始/停止/配置变更），把最新状态推回所有小部件
                updateAll(context)
            }
        }
    }
}

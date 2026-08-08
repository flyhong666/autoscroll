package cn.ggdoc.autoscroll.task

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * 屏幕常亮管理：使用 WakeLock 防止 CPU/屏幕休眠
 *
 * 注意：调用方需要在停止时调用 release()，避免耗电
 */
object KeepAliveManager {

    private const val TAG = "KeepAliveManager"
    private const val WAKE_LOCK_TAG = "AutoScroll::KeepScreenOn"

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * WakeLock 安全持有上限（兜底）：正常情况由 [refresh] 周期性续期，
     * 但若进程被系统强杀（未走 onDestroy / release），无限持有的 WakeLock
     * 会永久泄漏、持续耗电。加上超时后，即使未被释放也会在超时后自动归还。
     */
    private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L

    /**
     * 获取屏幕常亮
     */
    fun acquire(context: Context) {
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "WakeLock 已持有，跳过")
            return
        }
        try {
            val pm = context.applicationContext
                .getSystemService(Context.POWER_SERVICE) as PowerManager
            // SCREEN_BRIGHT_WAKE_LOCK 自 API 17 起对屏幕已无实际效果，
            // 这里仅用 PARTIAL_WAKE_LOCK 保持 CPU 不休眠；
            // 真正的「屏幕常亮」由悬浮窗的 FLAG_KEEP_SCREEN_ON 实现
            // （见 FloatingWindowService.syncKeepScreenFlag）。
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            )
            wakeLock?.setReferenceCounted(false)
            // S4 修复：使用带超时的 acquire，避免进程被强杀后 WakeLock 永久泄漏耗电
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            Log.i(TAG, "WakeLock 已获取（CPU 保持运行，超时 ${WAKE_LOCK_TIMEOUT_MS}ms 兜底）")
        } catch (e: Exception) {
            Log.e(TAG, "获取 WakeLock 失败", e)
        }
    }

    /**
     * 周期续期：在持有期间重置超时上限，保证长效运行不中断；
     * 仅在 WakeLock 已持有时生效。由无障碍服务的 tick 定时调用即可。
     */
    fun refresh(context: Context) {
        if (wakeLock?.isHeld != true) return
        try {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "续期 WakeLock 失败", e)
        }
    }

    /**
     * 释放屏幕常亮
     */
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock 已释放")
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放 WakeLock 失败", e)
        }
        wakeLock = null
    }

    fun isHeld(): Boolean = wakeLock?.isHeld == true
}

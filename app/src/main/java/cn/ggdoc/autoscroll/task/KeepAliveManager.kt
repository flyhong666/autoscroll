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
            // SCREEN_BRIGHT_WAKE_LOCK：保持屏幕常亮 + CPU 运行（已 deprecated 但兼容性最好）
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                WAKE_LOCK_TAG
            )
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire()
            Log.i(TAG, "WakeLock 已获取，屏幕常亮")
        } catch (e: Exception) {
            Log.e(TAG, "获取 WakeLock 失败", e)
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

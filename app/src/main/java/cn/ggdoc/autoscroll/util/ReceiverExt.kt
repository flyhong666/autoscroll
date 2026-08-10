package cn.ggdoc.autoscroll.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

/**
 * 兼容注册广播接收器：
 * API 33+（TIRAMISU）使用 [Context.RECEIVER_NOT_EXPORTED]，低版本走普通注册。
 * 替换原本在多处重复的 `if (SDK_INT >= TIRAMISU) ... else ...` 写法。
 */
fun Context.registerReceiverSafe(receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        registerReceiver(receiver, filter)
    }
}

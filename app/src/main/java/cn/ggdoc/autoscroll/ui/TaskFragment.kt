package cn.ggdoc.autoscroll.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService
import cn.ggdoc.autoscroll.util.registerReceiverSafe

/**
 * 统计页：仅展示实时统计看板（滚动 / 点赞 / 关闭广告 / 激励次数 / 运行时长）。
 * 全部运行参数已迁移至「控制」页的「基础参数」设置面板（SettingsBottomSheet）。
 */
class TaskFragment : Fragment() {

    private lateinit var tvStatScroll: TextView
    private lateinit var tvStatLike: TextView
    private lateinit var tvStatAdBlock: TextView
    private lateinit var tvStatAdReward: TextView
    private lateinit var tvStatTime: TextView

    /** 监听无障碍服务状态广播，实时刷新统计看板 */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED) {
                refreshStats()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvStatScroll = view.findViewById(R.id.tvStatScroll)
        tvStatLike = view.findViewById(R.id.tvStatLike)
        tvStatAdBlock = view.findViewById(R.id.tvStatAdBlock)
        tvStatAdReward = view.findViewById(R.id.tvStatAdReward)
        tvStatTime = view.findViewById(R.id.tvStatTime)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AutoScrollAccessibilityService.BROADCAST_STATE_CHANGED)
        requireContext().registerReceiverSafe(stateReceiver, filter)
        refreshStats()
    }

    override fun onPause() {
        super.onPause()
        runCatching { requireContext().unregisterReceiver(stateReceiver) }
    }

    private fun refreshStats() {
        tvStatScroll.text = AutoScrollAccessibilityService.scrollCount.toString()
        tvStatLike.text = AutoScrollAccessibilityService.likeCount.toString()
        tvStatAdBlock.text = AutoScrollAccessibilityService.adBlockCount.toString()
        tvStatAdReward.text = AutoScrollAccessibilityService.adRewardCount.toString()
        tvStatTime.text = formatDuration(AutoScrollAccessibilityService.runningSeconds)
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}

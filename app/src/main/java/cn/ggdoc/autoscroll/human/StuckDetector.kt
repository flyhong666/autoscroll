package cn.ggdoc.autoscroll.human

/**
 * 卡死检测状态机（纯逻辑，可单元测试）。
 *
 * 解决的问题：原实现只要在运行就无脑按节奏滑，无论屏幕内容是否变化。
 * 于是以下情况会**一直空转到用户手动停止**：
 * - 信息流刷到底了
 * - 网络失败，页面停在「加载失败，点击重试」
 * - 弹窗/浮层遮挡，滑动手势被吃掉
 * - 目标 APP 被系统回收，前台变成桌面
 *
 * 做法：每轮滑动后对屏幕文本取指纹，与上一轮比较。
 * 连续 N 轮指纹不变即判定卡住，按严重程度分级恢复。
 */
class StuckDetector(
    /** 连续无变化多少次后尝试关闭弹窗 */
    private val adBlockAt: Int = 3,
    /** 连续无变化多少次后执行返回键 */
    private val backAt: Int = 5,
    /** 连续无变化多少次后重启目标 APP */
    private val restartAt: Int = 8
) {

    /** 建议采取的恢复动作 */
    enum class Action {
        /** 内容正常变化，无需干预 */
        NONE,

        /** 疑似弹窗遮挡：扫描并关闭广告/浮层 */
        CLOSE_POPUP,

        /** 疑似进入了非预期页面：按返回键 */
        PRESS_BACK,

        /** 疑似 APP 卡死或被回收：重新拉起目标 APP */
        RESTART_APP
    }

    private var lastHash: Long = NO_HASH
    private var sameCount: Int = 0

    /** 连续无变化次数（供日志/UI 展示） */
    val consecutiveSame: Int get() = sameCount

    /**
     * 提交本轮屏幕指纹，返回建议动作。
     *
     * 分级触发是**恰好命中**而非「大于等于」：
     * 避免在 5、6、7 次时反复按返回键，每个阈值只触发一次。
     * 超过 [restartAt] 后每 [restartAt] 次重试一轮重启。
     */
    fun submit(hash: Long): Action {
        if (hash == NO_HASH) {
            // 取不到内容（root 为 null 等）不计入，避免误判
            return Action.NONE
        }
        if (hash != lastHash) {
            lastHash = hash
            sameCount = 0
            return Action.NONE
        }

        sameCount++
        return when {
            sameCount == adBlockAt -> Action.CLOSE_POPUP
            sameCount == backAt -> Action.PRESS_BACK
            sameCount == restartAt -> Action.RESTART_APP
            // 长期卡死：每隔 restartAt 次再试一次重启，避免彻底放弃
            sameCount > restartAt && (sameCount - restartAt) % restartAt == 0 -> Action.RESTART_APP
            else -> Action.NONE
        }
    }

    /** 恢复动作执行后调用：给页面一次「重新观察」的机会，但不完全清零 */
    fun onRecoveryAttempted() {
        lastHash = NO_HASH
    }

    /** 完全重置（开始滚动 / 切换场景 / 切换 APP 时调用） */
    fun reset() {
        lastHash = NO_HASH
        sameCount = 0
    }

    companion object {
        /** 表示「本轮未能取得有效指纹」 */
        const val NO_HASH = Long.MIN_VALUE

        /**
         * 由文本列表计算指纹。
         *
         * 用 FNV-1a 而非 List.hashCode()：
         * 前者分布更均匀且实现稳定，后者在不同 JVM 版本上对 String 的
         * hashCode 虽然规范固定，但组合方式易受列表顺序微扰影响。
         * 这里顺序本身是有意义的信号（列表项顺序变化=内容变了），所以保留顺序敏感。
         */
        fun fingerprint(texts: List<String>): Long {
            if (texts.isEmpty()) return NO_HASH
            var h = -0x340d631b7bdddcdbL // FNV-1a 64 位 offset basis
            for (t in texts) {
                for (c in t) {
                    h = h xor c.code.toLong()
                    h *= 0x100000001b3L // FNV prime
                }
                h = h xor 0x2CL // 分隔符，避免 ["ab","c"] 与 ["a","bc"] 同值
                h *= 0x100000001b3L
            }
            // 极小概率撞上哨兵值，偏移一位规避
            return if (h == NO_HASH) h + 1 else h
        }
    }
}

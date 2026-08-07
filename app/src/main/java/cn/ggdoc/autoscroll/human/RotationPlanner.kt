package cn.ggdoc.autoscroll.human

/**
 * 多 APP 轮换规划器（纯逻辑，可单元测试）。
 *
 * 解决的问题：原实现是「取下一个包名 -> startActivity -> 完事」，
 * **完全不校验是否真的切过去了**。以下情况会导致后续整个轮换周期空转：
 * - 目标 APP 未安装（getLaunchIntentForPackage 返回 null）
 * - 目标 APP 启动即崩溃
 * - 卡在开屏广告，前台包名迟迟不变
 * - 系统限制后台启动 Activity（Android 10+ 常见）
 *
 * 本类维护每个包名的失败计数，连续失败达到阈值就把它**临时下线**，
 * 不再浪费轮换周期。全部候选都下线时自动全体复活（避免彻底停摆）。
 */
class RotationPlanner(packages: List<String>) {

    /** 单个包名连续失败多少次后临时下线 */
    private val maxFailures = 3

    private val all: List<String> = packages.distinct()
    private val failures = HashMap<String, Int>()
    private var cursor = -1

    /** 当前可用（未被下线）的候选 */
    val availablePackages: List<String>
        get() = all.filter { (failures[it] ?: 0) < maxFailures }

    val isEmpty: Boolean get() = all.isEmpty()

    /** 某个包名当前的连续失败次数 */
    fun failureCount(pkg: String): Int = failures[pkg] ?: 0

    /**
     * 取下一个要切换的目标包名。
     *
     * 跳过已下线的包名；若全部下线，则先全体复活再取——
     * 宁可重试也不要让轮换功能彻底哑掉（可能只是网络波动导致的连续失败）。
     */
    fun next(): String? {
        if (all.isEmpty()) return null
        if (availablePackages.isEmpty()) {
            // 全军覆没：复活所有候选，重新开始
            failures.clear()
        }
        // 最多绕一圈，找到第一个可用的
        for (i in 1..all.size) {
            val idx = (cursor + i).mod(all.size)
            val pkg = all[idx]
            if ((failures[pkg] ?: 0) < maxFailures) {
                cursor = idx
                return pkg
            }
        }
        return null
    }

    /** 切换成功：清零该包名的失败计数 */
    fun markSuccess(pkg: String) {
        failures.remove(pkg)
    }

    /**
     * 切换失败：累加失败计数。
     * @return 该包名是否已被下线
     */
    fun markFailure(pkg: String): Boolean {
        val n = (failures[pkg] ?: 0) + 1
        failures[pkg] = n
        return n >= maxFailures
    }

    /** 重置全部状态（重新开始滚动 / 配置变更时调用） */
    fun reset() {
        failures.clear()
        cursor = -1
    }

    /**
     * 判断一次切换是否成功。
     *
     * 注意不能简单比较 `foreground == target`：很多 APP 的开屏页
     * 属于同一包名但不同 Activity，包名一致即算成功；
     * 而部分 APP（如微信）跳转后前台包名可能短暂是系统 UI，
     * 所以调用方应在延时若干秒后再调用本方法。
     */
    fun isSwitchSuccessful(target: String, foregroundPackage: String?): Boolean {
        if (foregroundPackage.isNullOrBlank()) return false
        return foregroundPackage == target || foregroundPackage.startsWith("$target.")
    }
}

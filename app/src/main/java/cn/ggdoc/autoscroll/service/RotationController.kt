package cn.ggdoc.autoscroll.service

import android.content.Context
import android.content.Intent
import android.util.Log
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.human.RotationPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 多 APP 轮换控制器。
 *
 * 从 [AutoScrollAccessibilityService] 抽离：
 *  - 周期轮换调度（rotationJob）
 *  - 目标应用启动 + 延时回查前台包名（launchAndVerify → verifyJob）
 *  - 连续失败下线 / 全局复活
 *
 * 调度由内部 [CoroutineScope]（主线程）驱动：start() 启动一个
 * `while(isScrolling) { ...; delay(interval) }` 协程；verifyJob 用独立协程
 * 做延时回查。stop() 取消全部协程。
 */
class RotationController(
    private val context: Context,
    private val serviceProvider: ServiceFace
) {

    interface ServiceFace {
        val TAG: String get() = AutoScrollAccessibilityService.TAG
        val isScrolling: Boolean
        val appRotationEnabled: Boolean
        val rotationMinutes: Int
        val rotationList: List<String>
        val foregroundPackage: String?
        val packageName: String
        fun sendTaskEvent(type: String, msg: String)
        fun resetStuckDetector()
    }

    companion object {
        /** 轮换后回查前台包名的延时，给系统留出冷启动时间 */
        private const val ROTATION_VERIFY_DELAY_MS = 3500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rotationJob: Job? = null
    private var verifyJob: Job? = null
    private var rotationPlanner: RotationPlanner? = null

    fun start() {
        if (!serviceProvider.appRotationEnabled || serviceProvider.rotationList.isEmpty()) return
        rotationJob?.cancel()
        val intervalMs = serviceProvider.rotationMinutes.toLong() * 60 * 1000
        val planner = rotationPlanner ?: RotationPlanner(serviceProvider.rotationList).also {
            rotationPlanner = it
        }
        rotationJob = scope.launch {
            while (isActive && serviceProvider.isScrolling) {
                val targetPkg = planner.next()
                if (targetPkg == null) {
                    Log.w(serviceProvider.TAG, "轮换：无可用 APP，跳过本轮")
                } else {
                    launchAndVerify(planner, targetPkg)
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        rotationJob?.cancel()
        rotationJob = null
        verifyJob?.cancel()
        verifyJob = null
    }

    /** 服务端轮换列表 / 开关被用户修改后调用：重置调度链并按新参数重启（若运行中） */
    fun onRotationConfigChanged() {
        rotationPlanner = null
        stop()
        if (serviceProvider.isScrolling) start()
    }

    private fun launchAndVerify(planner: RotationPlanner, targetPkg: String) {
        val launchIntent = try {
            context.packageManager.getLaunchIntentForPackage(targetPkg)
        } catch (e: Exception) {
            Log.e(serviceProvider.TAG, "查询 $targetPkg 启动入口失败", e)
            null
        }
        if (launchIntent == null) {
            val offline = planner.markFailure(targetPkg)
            Log.w(serviceProvider.TAG, "轮换：$targetPkg 无启动入口${if (offline) "，已临时下线" else ""}")
            return
        }
        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            val offline = planner.markFailure(targetPkg)
            Log.e(serviceProvider.TAG, "轮换：启动 $targetPkg 失败${if (offline) "，已临时下线" else ""}", e)
            return
        }

        // 延时回查：给系统留出冷启动时间
        verifyJob?.cancel()
        verifyJob = scope.launch {
            delay(ROTATION_VERIFY_DELAY_MS)
            if (!serviceProvider.isScrolling) return@launch
            val ok = planner.isSwitchSuccessful(targetPkg, serviceProvider.foregroundPackage)
            if (ok) {
                planner.markSuccess(targetPkg)
                serviceProvider.resetStuckDetector()
                Log.d(serviceProvider.TAG, "轮换：已切换到 $targetPkg")
                serviceProvider.sendTaskEvent(
                    AutoScrollAccessibilityService.EVENT_APP_ROTATION,
                    context.getString(R.string.toast_app_rotation, targetPkg)
                )
            } else {
                val offline = planner.markFailure(targetPkg)
                Log.w(
                    serviceProvider.TAG,
                    "轮换：切换 $targetPkg 未生效（前台=${serviceProvider.foregroundPackage}）" +
                        if (offline) "，已临时下线" else "，将在下轮重试"
                )
            }
        }
    }
}

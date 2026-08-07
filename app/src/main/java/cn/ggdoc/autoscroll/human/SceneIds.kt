package cn.ggdoc.autoscroll.human

/**
 * 场景 ID 常量的**唯一真源**，不依赖任何 Android 类。
 *
 * 为什么单独抽一个文件：
 * [cn.ggdoc.autoscroll.human.SceneDetector] 是纯判定逻辑，本该能直接跑 JVM 单元测试，
 * 但它原先引用 `AppConfig.SCENE_*`——而 AppConfig 依赖 `android.content.Context`，
 * 一引入就把整个 Android 框架拖进了测试 classpath。
 *
 * 现在常量定义在这里，`AppConfig.SCENE_*` 改为指向本对象的别名，
 * 既保持了所有既有调用点不变，又让判定逻辑彻底脱离 Android。
 */
object SceneIds {
    const val SHORT_VIDEO = "short_video"
    const val NEWS = "news"
    const val NOVEL = "novel"
    const val SOCIAL = "social"
    const val LIVE = "live"
    const val CUSTOM = "custom"

    /** 全部场景 ID，顺序与 UI 展示一致 */
    val ALL = listOf(SHORT_VIDEO, NEWS, NOVEL, SOCIAL, LIVE, CUSTOM)

    /** 是否为合法场景 ID */
    fun isValid(id: String?): Boolean = id != null && id in ALL
}

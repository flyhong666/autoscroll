package cn.ggdoc.autoscroll.human

/**
 * 场景 ID 常量的**唯一真源**，不依赖任何 Android 类。
 *
 * 为什么单独抽一个文件：
 * 场景 ID 需要被「不依赖 Android 的纯逻辑」和「依赖 Context 的 AppConfig」共同使用，
 * 若直接定义在 AppConfig 里，会把 `android.content.Context` 拖进纯逻辑的测试 classpath。
 *
 * 现在常量定义在这里，`AppConfig.SCENE_*` 改为指向本对象的别名，
 * 既保持了所有既有调用点不变，又让纯逻辑彻底脱离 Android。
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

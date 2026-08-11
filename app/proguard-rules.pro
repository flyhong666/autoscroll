# =========================================================================
# AutoScroll ProGuard / R8 规则
# AGP 自动保留 AndroidManifest 引用的 Activity/Service/Receiver，这里补强：
#  - JSON 序列化模型（手动 fromJson，避免字段被重命名后破坏存盘脚本）
#  - Kotlin 元数据 / 反射入口
#  - 第三方库的已知反射点
# =========================================================================

# ---- 通用属性保留 ----
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod,InnerClasses
# Kotlin 元数据：data class 的 copy/componentN 依赖 @Metadata
-keep class kotlin.Metadata { *; }

# ---- 1. AndroidManifest 显式引用的组件（双保险，AGP 已自动保留） ----
-keep public class cn.ggdoc.autoscroll.MainActivity { *; }
-keep public class cn.ggdoc.autoscroll.ui.ScriptActivity { *; }
-keep public class cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService { *; }
-keep public class cn.ggdoc.autoscroll.service.FloatingWindowService { *; }
-keep public class cn.ggdoc.autoscroll.service.RecorderOverlayService { *; }
-keep public class cn.ggdoc.autoscroll.service.ScheduleReceiver { *; }

# ---- 2. JSON 序列化模型（手动 toJson/fromJson，字段名写入磁盘脚本） ----
# 脚本 .json 存盘后用户会导出 / 跨版本读取，字段名必须稳定。
-keep class cn.ggdoc.autoscroll.recorder.RecordedAction { *; }
-keep class cn.ggdoc.autoscroll.recorder.RecordedAction$Companion { *; }
-keep class cn.ggdoc.autoscroll.recorder.RecordedScript { *; }
-keep class cn.ggdoc.autoscroll.recorder.RecordedScript$Companion { *; }
-keep class cn.ggdoc.autoscroll.recorder.ScriptStore$Entry { *; }
-keep class cn.ggdoc.autoscroll.recorder.ScriptStore { *; }

# ---- 3. 配置 / 统计模型（SharedPreferences 显式 key，但仍保留以防反射访问） ----
-keep class cn.ggdoc.autoscroll.config.CustomGestureStep { *; }
-keep class cn.ggdoc.autoscroll.config.CustomGestureStep$Companion { *; }
-keep class cn.ggdoc.autoscroll.config.StatsStore$Stats { *; }
-keep class cn.ggdoc.autoscroll.config.StatsStore { *; }
-keep class cn.ggdoc.autoscroll.config.AppConfig { *; }
-keep class cn.ggdoc.autoscroll.config.SceneConfig { *; }
-keep class cn.ggdoc.autoscroll.config.SceneConfig$* { *; }

# ---- 4. ViewBinding 生成的绑定类（AGP 已保留，保险起见显式声明） ----
-keep class cn.ggdoc.autoscroll.databinding.** { *; }

# ---- 5. Kotlin 协程 / 标准库反射支持（万一未来引入） ----
-dontwarn kotlin.coroutines.**
-dontwarn kotlinx.coroutines.**

# ---- 6. Material Components（库自带 consumer rules，补强自定义主题属性） ----
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ---- 7. AndroidX（库自带 consumer rules，仅压制缺失引用告警） ----
-dontwarn androidx.**

# ---- 8. R8 优化选项（默认 proguard-android-optimize.txt 已含大部分） ----
# 合并同类接口，减少虚方法数
-allowaccessmodification
# 移除 Log.v / Log.d 调用（release 不需要详细日志，进一步减小体积）
# 注意：保留 Log.i / Log.w / Log.e 用于线上排障
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# 1. 保留 Kotlin 標準庫核心與集合
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# 2. 保留 AndroidX Startup 與 Lifecycle 初始化元件
-keep class androidx.startup.** { *; }
-keep class androidx.lifecycle.ProcessLifecycleInitializer { *; }
-keep public class * extends androidx.startup.Initializer { *; }

# 3. 保留 Compose 與協程底層反射
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn kotlinx.coroutines.**
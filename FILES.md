# 文件作用

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/kosong/misettings/ModuleHook.kt` | Xposed 入口；进程/版本守卫；注册 H01 首页、H02 详情两个 Hook |
| `app/src/main/java/com/kosong/misettings/DisplayRules.kt` | 设备时长、解锁和已有应用展示副本转换；交由 HostModels 访问宿主模型 |
| `app/src/main/java/com/kosong/misettings/HostModels.kt` | 按 Smali 精确 copy/getter 签名访问宿主模型，校验 DetailPageModel 接口与 ClassLoader；不猜测接口 |
| `app/src/test/java/com/kosong/misettings/HostModelsTest.kt` | 自编 JVM 模型夹具，验证空值、副本隔离、重载、错误接口及 ClassLoader；不包含目标 APK 源码 |
| `app/src/main/java/com/kosong/misettings/Config.kt` | 配置契约常量、配置数据类、Bundle 序列化、SharedPreferences 存取与 revision |
| `app/src/main/java/com/kosong/misettings/ConfigClient.kt` | 目标进程内异步读取配置快照、监听变更；异步投递日志 |
| `app/src/main/java/com/kosong/misettings/ModuleConfigProvider.kt` | 模块 App 对目标进程的配置/日志接口，校验调用方 |
| `app/src/main/java/com/kosong/misettings/ModuleLog.kt` | 日志写入：SAF 目录优先，私有目录回退，过期清理 |
| `app/src/main/java/com/kosong/misettings/LogCleanupWorker.kt` | WorkManager 每 7 天清理私有日志 |
| `app/src/main/java/com/kosong/misettings/MainActivity.kt` | Compose Miuix 配置界面、日志目录选择 |
| `app/src/main/AndroidManifest.xml` | Activity、Provider、Xposed 元数据与作用域 |
| `app/src/main/assets/xposed_init` | Xposed Java 入口类名 |
| `app/src/main/res/values/strings.xml` | App 名称、模块描述 |
| `app/build.gradle.kts` | compileSdk 37.0、依赖、Debug 版本号与可选签名 |
| `build.gradle.kts` | AGP / Kotlin / Compose 插件版本 |
| `settings.gradle.kts` | 仓库源（Xposed 仓库仅限 `de.robv.android.xposed` 组） |
| `gradle.properties` | Gradle / Kotlin / AndroidX 设置 |
| `.github/workflows/build-debug.yml` | 仅 Debug 云构建与 Artifact 上传 |
| `README.md` | 阶段说明、构建、使用、配置格式 |
| `FILES.md` | 本文件 |
| `LICENSE` | MIT |

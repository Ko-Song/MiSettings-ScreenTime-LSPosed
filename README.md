# MiSettings-ScreenTime-LSPosed

面向小米澎湃 OS 2 `com.xiaomi.misettings` 的 LSPosed 显示覆盖模块。逆向基线：`15.00.0722.01-phone` / versionCode `250722130`（Android 15，HyperOS 2.0.211.0）。

> 开发阶段，默认关闭。设计目标是只修改页面模型副本。已通过 Debug 云构建，尚未验证安装、Hook 命中、跨进程配置或实际页面行为。

## 已确认的云构建

[GitHub Actions #38](https://github.com/Ko-Song/MiSettings-ScreenTime-LSPosed/actions/runs/37641425221) 的 API 结论为 `success`，编译、打包和 Artifact 上传步骤全部成功。

| 项目 | 记录 |
|---|---|
| 源码提交 | `afd21d1bf1d0335786a1a0e0b2078dd003b0c7e9` |
| Artifact | `MiSettings-ScreenTime-LSPosed-debug-38` |
| Artifact ID | `11491523971` |
| Artifact 大小 | 10,707,702 字节（约 10.2 MiB） |
| 工作流指定的 APK 文件名 | `MiSettings-ScreenTime-LSPosed-v0.1.38-debug.apk` |
| 按构建配置生成的版本 | `versionName=0.1.38-debug`，`versionCode=10038` |
| Artifact SHA-256 | `93424081a25e4df1fe6614d405e545b282736808eb580a5e2bb4e7895a2813d5` |

登录 GitHub 后可在运行页面的 Artifacts 区域下载。上述摘要由 GitHub 返回，属于 Artifact，不是已独立校验的 APK 摘要。没有构建 Release 或发布 GitHub Release。

## 当前代码

- 传统 Xposed 入口（API 82），目标为 `com.xiaomi.misettings` 默认主进程，`Application.attach` 后检查 versionCode 和 versionName；
- H01 首页：`u9.h.invoke(Object)` 前置替换列表中的目标模型副本；
- H02 详情：`e9.a0.e(DetailPageModel)` 前置替换页面模型副本，接收者限定 `ma.h0`，保留宿主列表组装过程；
- 设备时长、解锁分桶、已有应用条目的时长和隐藏规则；
- ContentProvider 配置接口、目标进程异步配置快照及变更监听；
- 日志优先写所选 SAF 目录，失败回退私有目录；自动清理目前只覆盖私有目录中过期 7 天的日志；
- Compose Miuix 配置界面；GitHub Actions 执行模型单元测试和 `assembleDebug`，不构建 Release。

## 待补证和已知限制

- 已收到 `DetailPageModel`、`FunctionItem`、`e9.z`、`c9.f`、`c9.f$f` 的完整 Smali。`DetailPageModel.copy(VisualHealthDetails, ScreenTimeDetails, c9.b)` 及三个 getter 已精确确认；HostModels 按静态签名定位，不再按返回类型猜测 getter。H02 注册前检查接口，缺失时停用该点。
- 10 个模型 copy 描述符已逐项对照 Smali；9 项 JVM 测试验证空值、副本隔离、额外重载、错误接口和 ClassLoader 隔离。这些是模块接口测试，不代表目标 APK 的实机验证。
- 新证据表明“查看更多应用”点击进入 `e9.z` 后会通过 `c9.f$a` 重新创建 Intent 并传递 `range_index`、`day_tab`、`app_type`。仍缺 `c9.f$a` 及实际列表交付链，不能直接用 `c9.f$f` 的初始 Intent 推断完整覆盖。
- 两个核心 Hook 不覆盖“查看更多应用”完整列表、单应用或分类深入详情，也不能为原列表中不存在的应用新增条目。
- 日期/周期控制、快速切换请求的日期归属、跨日、缓存热命中及跨页面一致性尚未完成验收；不能仅凭桶数保证日/周/月语义正确。
- 未填写分桶时，总值规则会把数值放在最后一个桶，其余桶归零。`firstTime` 和 `lastCycle` 仍保留原值，解锁首次时间可能与新分布不一致，后续需要修正。
- 配置通知刷新快照，不会主动重绘停留中的页面。首次异步读取前默认关闭；后续读取失败会保留上一次快照。
- 日志查看、导出、容量限制、SAF 清理和有界日志队列尚未完成。
- LiquidGlass、Shapes 尚未集成；目前是使用 Miuix 库的 Android Compose 工程。
- 所有 Hook 行为尚未进行 LSPosed 真机测试，构建成功不代表功能已生效。

## 构建

云端使用 JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0 和 Gradle 8.13。SDK 初始化使用 `android-actions/setup-android@v4`，Gradle 由 `gradle/actions/setup-gradle` 安装。云构建不依赖 Gradle Wrapper。

版本号来自 `GITHUB_RUN_NUMBER`：`versionCode = 10000 + run`，`versionName = 0.1.<run>-debug`。

本地如需构建，请安装上述工具链后运行：

```bash
gradle assembleDebug
```

Gradle 支持 `DEBUG_KEYSTORE_PATH`、`DEBUG_KEYSTORE_PASSWORD`、`DEBUG_KEY_ALIAS`、`DEBUG_KEY_PASSWORD`，当前工作流尚未接入这些签名变量。临时 runner 的默认 Debug 签名不保证每次相同，连续覆盖安装需要先配置固定签名。密钥、密码和设备日志不提交到源码仓库。

## 后续验证顺序

1. `DetailPageModel` 复制接口已核对。接续读取 `c9.f$a`、`AppUsageLimitPage`、`AbsAppUsageLimitPage`，沿确切调用链定位列表交付点，并记录 LSPosed Manager/Framework 版本。
2. 修正规则一致性和日志问题，生成对应 Debug 构建。
3. 真机先保持总开关关闭，确认模块 App 启动、配置保存和宿主页面正常。
4. 启用 LSPosed 作用域，按框架要求重启目标进程，检查 Hook 注册和命中日志。
5. 一次启用一个固定规则，验证显示、刷新、关闭恢复，再扩大到周期和子页面。

## 配置格式（当前草稿）

- 设备分桶为分钟，解锁分桶为次数，逗号分隔；长度必须与当前宿主模型相同。
- 首页图表是七日分布，详情日模式通常为 24 小时；月模式可能为滚动 30 日或自然月 28/29/30/31 日。
- 分桶非空时使用分桶合计；总值输入仅在分桶为空时使用。
- 应用规则：每行 `包名=分钟`；隐藏包名：逗号或换行分隔。
- 生效时段为本地时间分钟数（0–1440），起止相等表示全天；它不是统计日期筛选。

## 逆向依据

原始逆向档案保留在本地 `MiSettings-Reverse-250722130`，不上传到公开源码仓库。依据为其中的 `REVERSE_REPORT.md`、`HANDOFF.md`、`indexes/hook-contract.json`。

## 许可

MIT，见 [LICENSE](LICENSE)。

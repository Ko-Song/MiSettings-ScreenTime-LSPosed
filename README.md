# MiSettings-ScreenTime-LSPosed

面向小米澎湃 OS 2 `com.xiaomi.misettings` 的 LSPosed 显示覆盖模块。逆向基线：`15.00.0722.01-phone` / versionCode `250722130`（Android 15，HyperOS 2.0.211.0）。

> 开发阶段，默认关闭。只修改页面展示副本，不改 Room / MMKV / UsageStats 原始数据。版本不匹配时不注册任何业务 Hook。

## 当前阶段：v0.1（首次云构建 + 待真机验证）

已实现：

- 传统 Xposed 入口（API 82），作用域仅 `com.xiaomi.misettings` 默认主进程，`Application.attach` 后做版本守卫；
- H01 首页：`u9.h.invoke(Object)` 前置替换 `List<GroupItem>`（`ChartItem(c9.c$d)`、`AppTop4Item`）；
- H02 详情：`e9.a0.e(DetailPageModel)` 前置替换页面模型副本，接收者限定 `ma.h0`，由宿主重新计算图表、Top4 与“查看全部”入口；
- 设备时长、解锁分桶、已有应用条目的时长覆盖与隐藏；
- 配置经自有 ContentProvider 提供给目标进程，目标进程异步刷新不可变快照；
- 日志优先写用户选择的 SAF 目录，失败回退 App 私有目录，7 天清理；
- Compose Miuix 配置界面；GitHub Actions 只构建 Debug。

未覆盖 / 待验证：

- `DetailPageModel.copy` 为运行时结构推断（类正文未逆向），推断失败会原样回退并写日志；
- “查看更多应用”完整列表、单应用 / 分类详情页；
- 列表中原本不存在的应用无法凭空新增；
- 快速切换日期的异步竞态、跨日、缓存热命中；
- 以上全部尚未真机验证。

## 构建

GitHub Actions：JDK 17 + Android SDK Platform 37.0 + Build Tools 36.0.0 + Gradle 8.13（`gradle/actions/setup-gradle`，仓库不提交 wrapper jar）。版本号来自 `GITHUB_RUN_NUMBER`：`versionCode = 10000 + run`，`versionName = 0.1.<run>-debug`。产物在 Actions 页面的 Artifact 中。

本地构建需自行安装 Gradle 8.13：

```bash
gradle assembleDebug
```

如需连续覆盖安装，配置固定 Debug keystore 环境变量（`DEBUG_KEYSTORE_PATH` 等）；不要把 `.jks`、密码或设备日志提交到仓库。

## 使用

1. 安装 Debug APK，在 LSPosed 中启用，作用域勾选“小米设置”。
2. 打开模块 App，开启总开关和需要的规则，点“保存并应用”。
3. 强制停止小米设置后进入“健康使用手机”。
4. 查看 LSPosed 日志（标签 `MiSettingsST`）和模块日志。

## 配置格式

- 设备分桶：分钟，逗号分隔，个数必须与宿主桶数一致（日 24、周 7、月按自然月天数），否则规则被拒绝；
- 设备总时长：分钟，未填写分桶时放入最后一个桶；
- 解锁分桶 / 总次数：同上，单位为次；
- 应用规则：每行 `包名=分钟`；
- 隐藏包名：逗号或换行分隔；
- 生效时段：本地时间分钟数（0–1440），开始等于结束表示全天。

## 逆向依据

逆向档案不放进仓库。实现依据为本地 `MiSettings-Reverse-250722130` 中的 `REVERSE_REPORT.md`、`HANDOFF.md`、`indexes/hook-contract.json`。

## 许可

MIT，见 [LICENSE](LICENSE)。

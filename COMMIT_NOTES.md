feat: Initial LSPosed module with H01 and H02 hooks

- H01: u9.h.invoke(Object) before hook for HomePage GroupItem list
- H02: e9.a0.e(DetailPageModel) before hook with runtime copy inference
- Version guard: 15.00.0722.01-phone (250722130)
- Config: ContentProvider + SharedPreferences with revision mechanism
- Logging: SAF tree priority, 7-day rotation, async bridge for target process
- UI: Miuix Compose settings with device/unlock/app rules
- Dependencies: Miuix 0.9.3, Xposed API 82, compileSdk 37.0
- GitHub Actions: ubuntu-latest, platforms;android-37.0, build-tools;36.0.0

## 待真机验证

1. GitHub Actions 云构建是否成功生成 Debug APK
2. DetailPageModel.copy 运行时推断是否工作
3. 首页/详情页切换、DAY/WEEK/MONTH 切换
4. 异常回退机制

## 使用前准备

**模块默认关闭**，需在模块 App 中手动开启总开关，然后强制停止小米设置才会生效。

## 需要逆向的文件（如果真机测试失败）

- `com.xiaomi.misettings.base.model.page.DetailPageModel`：需确认 copy 方法签名和参数顺序
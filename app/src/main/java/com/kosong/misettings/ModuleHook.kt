package com.kosong.misettings

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.WeakHashMap

/**
 * LSPosed 传统入口。
 *
 * H01：首页观察回调 u9.h.invoke(Object)，前置替换 List<GroupItem>。
 * H02：详情 e9.a0.e(DetailPageModel)，接收者限定 ma.h0，前置替换页面模型副本。
 * 两者都在版本守卫通过后才注册，任何结构不符都原样回退。
 */
class ModuleHook : IXposedHookLoadPackage {
    private val registeredClassLoaders = Collections.newSetFromMap(
        WeakHashMap<ClassLoader, Boolean>(),
    )

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != ConfigContract.EXPECTED_PACKAGE) return
        if (lpparam.processName != null && lpparam.processName != ConfigContract.EXPECTED_PACKAGE) return

        val classLoader = lpparam.classLoader ?: return
        if (!registeredClassLoaders.add(classLoader)) {
            XposedBridge.log("$TAG: hooks already registered for ${lpparam.packageName}")
            return
        }

        // Application.attach 是目标进程内的单点初始化时机，能够拿到宿主 Context，
        // 同时避免在模块自己的 App 进程中读取配置或注册业务 Hook。
        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        if (context.packageName != ConfigContract.EXPECTED_PACKAGE) return
                        if (!isSupportedVersion(context)) {
                            XposedBridge.log(
                                "$TAG: unsupported host version; hooks disabled " +
                                    "version=${versionSummary(context)}",
                            )
                            return
                        }
                        ConfigClient.initialize(context)
                        registerBusinessHooks(classLoader)
                    }
                },
            )
            XposedBridge.log("$TAG: attach guard registered for ${lpparam.packageName}")
        }.onFailure { throwable ->
            registeredClassLoaders.remove(classLoader)
            XposedBridge.log("$TAG: attach registration failed: $throwable")
        }
    }

    private fun registerBusinessHooks(classLoader: ClassLoader) {
        // 两个 Hook 互相独立注册，一个失败不影响另一个。
        runCatching { hookHomeCallback(classLoader) }
            .onSuccess { XposedBridge.log("$TAG: H01 u9.h.invoke registered") }
            .onFailure { XposedBridge.log("$TAG: H01 registration failed: $it") }
        runCatching { hookDetailModel(classLoader) }
            .onSuccess { XposedBridge.log("$TAG: H02 e9.a0.e registered (before, model copy)") }
            .onFailure { XposedBridge.log("$TAG: H02 registration failed: $it") }
    }

    /**
     * H02：在 e9.a0.e(DetailPageModel) 执行前替换入参为页面模型副本，
     * 让宿主原方法重新生成 ChartItem / Top4 /“查看全部”入口。
     * DetailPageModel.copy 结构不符合预期时 DisplayRules 返回原对象，即原样回退。
     */
    private fun hookDetailModel(classLoader: ClassLoader) {
        val baseClass = XposedHelpers.findClass("e9.a0", classLoader)
        val modelClass = HostModels.verifyDetailPage(classLoader)
        val receiverClass = XposedHelpers.findClass(DETAIL_VIEW_MODEL_CLASS, classLoader)
        XposedHelpers.findAndHookMethod(
            baseClass,
            "e",
            modelClass,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val receiver = param.thisObject ?: return
                    if (!receiverClass.isInstance(receiver)) return
                    val input = param.args.firstOrNull() ?: return
                    val context = runCatching {
                        XposedHelpers.getObjectField(receiver, "w") as? Context
                    }.getOrNull() ?: return

                    val config = ConfigClient.read(context)
                    if (!config.enabled || !config.applyDetail) return

                    runCatching {
                        val transformed = DisplayRules.transformDetailPageModel(
                            input,
                            config,
                        ) { message -> ModuleLogBridge.write(context, message) }
                        if (transformed != null && transformed !== input && modelClass.isInstance(transformed)) {
                            param.args[0] = transformed
                        }
                    }.onFailure { throwable ->
                        ModuleLogBridge.write(context, "detail transform fallback: $throwable")
                    }
                }
            },
        )
    }

    private fun hookHomeCallback(classLoader: ClassLoader) {
        val callbackClass = XposedHelpers.findClass("u9.h", classLoader)
        XposedHelpers.findAndHookMethod(
            callbackClass,
            "invoke",
            Any::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val homePage = homePageReceiver(param.thisObject) ?: return
                    val input = param.args.firstOrNull()
                    if (input !is List<*>) return

                    val config = ConfigClient.read(homePage)
                    if (!config.enabled || !config.applyHome) return

                    runCatching {
                        val transformed = DisplayRules.transformHomeList(
                            input,
                            config,
                        ) { message -> ModuleLogBridge.write(homePage, message) }
                        if (transformed is List<*> && transformed !== input) {
                            // 保持原方法和原返回值不变，只替换本次观察回调收到的列表。
                            param.args[0] = transformed
                        }
                    }.onFailure { throwable ->
                        ModuleLogBridge.write(homePage, "home transform fallback: $throwable")
                    }
                }
            },
        )
    }

    private fun homePageReceiver(callback: Any?): Context? {
        if (callback == null) return null
        val receiver = runCatching {
            XposedHelpers.getObjectField(callback, "b")
        }.getOrNull() ?: return null
        if (receiver.javaClass.name != HOME_PAGE_CLASS) return null
        return receiver as? Context
    }

    private fun isSupportedVersion(context: Context): Boolean {
        val packageInfo = packageInfo(context) ?: return false
        val versionCode = packageInfo.longVersionCode
        val versionName = packageInfo.versionName
        return versionCode == ConfigContract.EXPECTED_VERSION_CODE &&
            versionName == ConfigContract.EXPECTED_VERSION_NAME
    }

    private fun versionSummary(context: Context): String {
        val info = packageInfo(context) ?: return "unavailable"
        return "${info.versionName}/${info.longVersionCode}"
    }

    private fun packageInfo(context: Context): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    private companion object {
        const val TAG = "MiSettingsST"
        const val HOME_PAGE_CLASS = "com.xiaomi.misettings.features.HomePage"
        const val DETAIL_VIEW_MODEL_CLASS = "ma.h0"
    }
}

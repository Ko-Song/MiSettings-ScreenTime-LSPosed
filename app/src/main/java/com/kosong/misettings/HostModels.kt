package com.kosong.misettings

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Exact public model interfaces from the version 250722130 Smali archive. */
internal object HostModels {
    private const val PAGE = "com.xiaomi.misettings.base.model.page."
    private const val ITEM = "com.xiaomi.misettings.base.model.item."
    const val DETAIL_PAGE = PAGE + "DetailPageModel"
    private const val SCREEN_TIME = PAGE + "ScreenTimeDetails"
    private const val VISUAL_HEALTH = PAGE + "VisualHealthDetails"
    private const val BUSINESS = "c9.b"
    private const val LIST = "java.util.List"
    private const val STRING = "java.lang.String"
    private val deviceDetails = SCREEN_TIME + '$' + "DeviceUsageDetails"
    private val nameDetails = SCREEN_TIME + '$' + "NameAndCategoryDetails"
    private val unlockDetails = SCREEN_TIME + '$' + "UnlockUsageDetails"

    private val copySignatures = mapOf(
        DETAIL_PAGE to listOf(VISUAL_HEALTH, SCREEN_TIME, BUSINESS),
        SCREEN_TIME to listOf(deviceDetails, nameDetails, unlockDetails),
        deviceDetails to listOf("long", LIST, "long", "long", "long"),
        nameDetails to listOf(LIST, LIST),
        unlockDetails to listOf("int", LIST, "int", "long", "int", "int"),
        ITEM + "ChartItem" to listOf("c9.c", "c9.h", "java.lang.Object", "int", "long", "long", "boolean", "boolean", BUSINESS, "c9.g"),
        ITEM + "AppTop4Item" to listOf(LIST, "c9.g"),
        ITEM + "Top4ProgressItem" to listOf("c9.h", "java.lang.Object", "int", "c9.j", "c9.g"),
        ITEM + "NameAndCategoryItem" to listOf(ITEM + "AppItem", STRING, STRING, STRING, "c9.a", "c9.g", "boolean"),
        ITEM + "AppItem" to listOf(STRING, "long", STRING, "android.graphics.drawable.Drawable", "boolean", "boolean", "c9.e", "boolean", STRING),
    )
    private val copies = ConcurrentHashMap<Class<*>, Method>()

    private class DetailAccess(val visual: Method, val screen: Method, val business: Method, val copy: Method)
    private val detailAccess = ConcurrentHashMap<Class<*>, DetailAccess>()

    /** Bind before registering H02. A missing exact method disables that hook. */
    fun verifyDetailPage(classLoader: ClassLoader): Class<*> {
        val pageClass = Class.forName(DETAIL_PAGE, false, classLoader)
        access(pageClass)
        return pageClass
    }

    /** Missing/throwing getters must reach the hook's fallback, not turn into nullable data. */
    fun read(target: Any, name: String): Any? {
        val method = target.javaClass.getMethod(name)
        require(!Modifier.isStatic(method.modifiers)) { "Static getter: $name" }
        return method.invoke(target)
    }

    fun copy(target: Any, args: Array<Any?>): Any {
        val method = copyMethod(target.javaClass)
        require(args.size == method.parameterCount) { "Invalid copy argument count: ${target.javaClass.name}" }
        return method.invoke(target, *args)
            ?: error("Host copy returned null: ${target.javaClass.name}")
    }

    fun copyDetailPage(original: Any, newScreenTimeDetails: Any): Any {
        val pageClass = original.javaClass
        require(pageClass.name == DETAIL_PAGE) { "Unexpected page model: ${pageClass.name}" }
        val methods = access(pageClass)
        require(methods.screen.returnType.isInstance(newScreenTimeDetails)) {
            "ScreenTimeDetails type or ClassLoader mismatch"
        }
        val visual = methods.visual.invoke(original) // Nullable by the verified Smali contract.
        val business = requireNotNull(methods.business.invoke(original)) { "Missing page business" }
        val result = methods.copy.invoke(original, visual, newScreenTimeDetails, business)
            ?: error("DetailPageModel.copy returned null")
        check(result !== original && result.javaClass == pageClass) { "Invalid page copy" }
        check(methods.visual.invoke(result) === visual && methods.business.invoke(result) === business) {
            "Page copy changed an unrelated field"
        }
        check(methods.screen.invoke(result) === newScreenTimeDetails) { "Page copy did not preserve new statistics" }
        return result
    }

    private fun access(pageClass: Class<*>): DetailAccess = detailAccess.computeIfAbsent(pageClass) {
        require(it.name == DETAIL_PAGE) { "Unexpected page model: ${it.name}" }
        DetailAccess(
            checkedGetter(it, "getVisualHealthDetail", VISUAL_HEALTH),
            checkedGetter(it, "getScreenTimeDetails", SCREEN_TIME),
            checkedGetter(it, "getBusiness", BUSINESS),
            copyMethod(it),
        )
    }

    private fun checkedGetter(owner: Class<*>, name: String, returnType: String): Method {
        val method = owner.getDeclaredMethod(name)
        require(Modifier.isPublic(method.modifiers) && !Modifier.isStatic(method.modifiers)) {
            "Unexpected getter modifiers: ${owner.name}.$name"
        }
        require(method.returnType == resolve(returnType, owner.classLoader)) {
            "Unexpected getter return type: ${owner.name}.$name"
        }
        return method
    }

    private fun copyMethod(modelClass: Class<*>): Method = copies.computeIfAbsent(modelClass) {
        val signature = copySignatures[it.name] ?: error("No verified copy contract: ${it.name}")
        val parameters = signature.map { type -> resolve(type, it.classLoader) }.toTypedArray()
        val method = it.getDeclaredMethod("copy", *parameters)
        require(method.returnType == it && Modifier.isPublic(method.modifiers) && !Modifier.isStatic(method.modifiers)) {
            "Unexpected copy method: ${it.name}"
        }
        method
    }

    private fun resolve(name: String, classLoader: ClassLoader?): Class<*> = when (name) {
        "int" -> Int::class.javaPrimitiveType!!
        "long" -> Long::class.javaPrimitiveType!!
        "boolean" -> Boolean::class.javaPrimitiveType!!
        else -> Class.forName(name, false, classLoader)
    }
}

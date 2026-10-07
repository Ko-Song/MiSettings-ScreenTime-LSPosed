package com.kosong.misettings

import java.lang.reflect.Method
import java.util.Calendar

/**
 * 只处理宿主页面模型的展示副本，不原地修改宿主对象、缓存或数据库。
 *
 * 反射全部发生在目标 APK 的 ClassLoader 中，参数类型由宿主 data class 的 copy 方法
 * 在运行时校验；任一结构不符合预期时，调用方应保留原输入。
 */
object DisplayRules {
    private const val MS_PER_MINUTE = 60_000L
    private const val CHART_ITEM = "com.xiaomi.misettings.base.model.item.ChartItem"
    private const val APP_TOP4_ITEM = "com.xiaomi.misettings.base.model.item.AppTop4Item"
    private const val TOP4_PROGRESS_ITEM = "com.xiaomi.misettings.base.model.item.Top4ProgressItem"
    private val HOME_DEVICE_CHART = "c9.c" + '$' + "d"
    private val DETAIL_DEVICE_CHART = "c9.c" + '$' + "a"
    private val DETAIL_UNLOCK_CHART = "c9.c" + '$' + "e"

    fun transformHomeList(input: Any?, config: ModuleConfig, log: (String) -> Unit): Any? {
        if (!config.enabled || !config.applyHome || !isActive(config) || input !is List<*>) return input
        return transformGroupList(input, config, log, home = true)
    }

    /**
     * 后置详情转换仅保留作兼容备用；H02 当前不会注册它。
     * 改变正时长条目数量时必须改用 DetailPageModel 前置副本，让宿主重算入口。
     */
    fun transformDetailList(input: Any?, config: ModuleConfig, log: (String) -> Unit): Any? {
        if (!config.enabled || !config.applyDetail || !isActive(config) || input !is List<*>) return input
        return transformGroupList(input, config, log, home = false)
    }

    /**
     * H02 的前置页面模型转换。它只复制 ScreenTimeDetails 及其被修改的子模型，
     * 再通过宿主 DetailPageModel.copy() 交回原 e()，由宿主重新计算 FunctionItem。
     */
    fun transformDetailPageModel(input: Any?, config: ModuleConfig, log: (String) -> Unit): Any? {
        if (!config.enabled || !config.applyDetail || !isActive(config) || input == null) return input

        val details = invokeNoArg(input, "getScreenTimeDetails") ?: return input
        val device = invokeNoArg(details, "getDeviceUsage")
        val nameAndCategory = invokeNoArg(details, "getNameAndCategoryDetails")
        val unlock = invokeNoArg(details, "getUnlockUsage")

        val newDevice = if (config.modifyDevice && device != null) {
            transformDeviceDetails(device, config, log)
        } else {
            device
        }
        val newNameAndCategory = if (config.modifyApps && nameAndCategory != null) {
            transformNameCategoryDetails(nameAndCategory, config, log, allowHide = true)
        } else {
            nameAndCategory
        }
        val newUnlock = if (config.modifyUnlock && unlock != null) {
            transformUnlockDetails(unlock, config, log)
        } else {
            unlock
        }

        if (newDevice === device && newNameAndCategory === nameAndCategory && newUnlock === unlock) {
            return input
        }

        val newDetails = invokeCopy(
            details,
            arrayOf(newDevice, newNameAndCategory, newUnlock),
        ) ?: run {
            log("detail model fallback: ScreenTimeDetails.copy unavailable")
            return input
        }
        val newPage = copyDetailPageModel(input, newDetails)
        if (newPage == null) {
            log("detail model fallback: DetailPageModel.copy unavailable")
            return input
        }
        log("detail page model transformed")
        return newPage
    }

    private fun transformGroupList(
        input: List<*>,
        config: ModuleConfig,
        log: (String) -> Unit,
        home: Boolean,
    ): List<*> {
        val output = ArrayList<Any?>(input.size)
        var changed = false

        for (item in input) {
            if (item == null) {
                output += null
                continue
            }
            val replacement = when (item.javaClass.name) {
                CHART_ITEM -> transformChartItem(item, config, log, home)
                APP_TOP4_ITEM -> if (home) transformAppTop4(item, config, log) else item
                TOP4_PROGRESS_ITEM -> if (!home) transformTop4Progress(item, config, log) else item
                else -> item
            }
            output += replacement
            changed = changed || replacement !== item
        }

        if (!changed) return input
        log("${if (home) "home" else "detail"} list transformed, size=${output.size}")
        return output
    }

    private fun transformChartItem(
        item: Any,
        config: ModuleConfig,
        log: (String) -> Unit,
        home: Boolean,
    ): Any {
        val chartType = invokeNoArg(item, "getChartType") ?: return item
        val details = invokeNoArg(item, "getDetails") ?: return item
        val typeName = chartType.javaClass.name

        val newDetails = when {
            home && typeName == HOME_DEVICE_CHART && config.modifyDevice ->
                transformDeviceDetails(details, config, log)
            !home && typeName == DETAIL_DEVICE_CHART && config.modifyDevice ->
                transformDeviceDetails(details, config, log)
            !home && typeName == DETAIL_UNLOCK_CHART && config.modifyUnlock ->
                transformUnlockDetails(details, config, log)
            else -> details
        }
        if (newDetails === details) return item

        return invokeCopy(
            item,
            arrayOf(
                invokeNoArg(item, "getChartType"),
                invokeNoArg(item, "getTab"),
                newDetails,
                invokeNoArg(item, "getRangeIndex"),
                invokeNoArg(item, "getMinTime"),
                invokeNoArg(item, "getMaxTime"),
                invokeNoArg(item, "getShowRangeButton"),
                invokeNoArg(item, "getAnim"),
                invokeNoArg(item, "getBusiness"),
                invokeNoArg(item, "getGroup"),
            ),
        ) ?: item
    }

    private fun transformTop4Progress(item: Any, config: ModuleConfig, log: (String) -> Unit): Any {
        val details = invokeNoArg(item, "getDetails") ?: return item
        val newDetails = transformNameCategoryDetails(details, config, log, allowHide = true)
        if (newDetails === details) return item

        return invokeCopy(
            item,
            arrayOf(
                invokeNoArg(item, "getTab"),
                newDetails,
                invokeNoArg(item, "getRangeIndex"),
                invokeNoArg(item, "getTop4Type"),
                invokeNoArg(item, "getGroup"),
            ),
        ) ?: item
    }

    private fun transformAppTop4(item: Any, config: ModuleConfig, log: (String) -> Unit): Any {
        val list = invokeNoArg(item, "getTop4") as? List<*> ?: return item
        val newList = transformNameCategoryList(list, config, log, allowHide = true)
        if (newList === list) return item

        return invokeCopy(
            item,
            arrayOf(newList, invokeNoArg(item, "getGroup")),
        ) ?: item
    }

    private fun transformNameCategoryDetails(
        details: Any,
        config: ModuleConfig,
        log: (String) -> Unit,
        allowHide: Boolean,
    ): Any {
        val app = invokeNoArg(details, "getAppDetails") as? List<*>
        val category = invokeNoArg(details, "getCategoryDetails") as? List<*>
        val newApp = app?.let { transformNameCategoryList(it, config, log, allowHide) }
        val newCategory = category?.let { transformNameCategoryList(it, config, log, allowHide) }
        if (newApp === app && newCategory === category) return details

        return invokeCopy(details, arrayOf(newApp, newCategory)) ?: details
    }

    private fun transformNameCategoryList(
        input: List<*>,
        config: ModuleConfig,
        log: (String) -> Unit,
        allowHide: Boolean,
    ): List<*> {
        if (!config.modifyApps) return input

        val rules = parseAppRules(config.appRules)
        val hidden = parsePackageSet(config.hidePackages)
        val output = ArrayList<Any?>(input.size)
        var touched = false

        for (entry in input) {
            if (entry == null) {
                output += null
                continue
            }

            val packageName = invokeNoArg(entry, "getPackageName") as? String
            if (allowHide && packageName != null && packageName in hidden) {
                touched = true
                continue
            }

            val detail = invokeNoArg(entry, "getDetail")
            val oldUsage = detail?.let { (invokeNoArg(it, "getUsage") as? Number)?.toLong() }
            val ruleMinutes = packageName?.let { rules[it] }
            if (detail != null && oldUsage != null && ruleMinutes != null) {
                val newUsage = minutesToMillis(ruleMinutes)
                if (newUsage != null && newUsage != oldUsage) {
                    val newDetail = invokeCopy(
                        detail,
                        arrayOf(
                            invokeNoArg(detail, "getTitle"),
                            newUsage,
                            invokeNoArg(detail, "getIcon"),
                            invokeNoArg(detail, "getDrawable"),
                            invokeNoArg(detail, "isLimit"),
                            invokeNoArg(detail, "isSystem"),
                            invokeNoArg(detail, "getDataType"),
                            invokeNoArg(detail, "getAsTitle"),
                            invokeNoArg(detail, "getKeywords"),
                        ),
                    )
                    val newEntry = newDetail?.let {
                        invokeCopy(
                            entry,
                            arrayOf(
                                it,
                                invokeNoArg(entry, "getPackageName"),
                                invokeNoArg(entry, "getCategoryType"),
                                invokeNoArg(entry, "getCategoryId"),
                                invokeNoArg(entry, "getAppType"),
                                invokeNoArg(entry, "getGroup"),
                                invokeNoArg(entry, "getPressEffect"),
                            ),
                        )
                    }
                    if (newEntry != null) {
                        output += newEntry
                        touched = true
                        continue
                    }
                }
            }
            output += entry
        }

        if (!touched) return input
        output.sortByDescending { entry -> entryUsage(entry) }
        log("app list transformed, before=${input.size}, after=${output.size}")
        return output
    }

    private fun transformDeviceDetails(
        details: Any,
        config: ModuleConfig,
        log: (String) -> Unit,
    ): Any {
        val old = readLongList(details, "getDetail") ?: return details
        val requestedBucket = parseDeviceBuckets(config.deviceBucketsMinutes)
        if (config.deviceBucketsMinutes.isNotBlank() && requestedBucket == null) {
            log("device rule rejected: invalid bucket value")
            return details
        }
        if (requestedBucket != null && requestedBucket.size != old.size) {
            log("device rule rejected: bucket size=${requestedBucket.size}, expected=${old.size}")
            return details
        }

        val totalOnly = if (requestedBucket == null && config.deviceTotalMinutes >= 0L) {
            minutesToMillis(config.deviceTotalMinutes)
        } else {
            null
        }
        if (requestedBucket == null && config.deviceTotalMinutes >= 0L && totalOnly == null) {
            log("device rule rejected: total is out of range")
            return details
        }
        if (requestedBucket == null && config.deviceTotalMinutes < -1L) {
            log("device rule rejected: negative total")
            return details
        }
        if (requestedBucket == null && totalOnly == null && config.deviceTotalMinutes < 0L) return details

        val values = when {
            requestedBucket != null -> requestedBucket
            totalOnly != null -> old.mapIndexed { index, _ -> if (index == old.lastIndex) totalOnly else 0L }
            else -> old
        }
        val total = safeSum(values) ?: run {
            log("device rule rejected: total overflow")
            return details
        }
        val positive = values.count { it > 0L }
        val average = if (values.size == 24) {
            total
        } else if (positive == 0) {
            0L
        } else {
            total / positive
        }
        val max = values.maxOrNull() ?: 0L
        val oldTotal = (invokeNoArg(details, "getTotalDuration") as? Number)?.toLong()
        val oldMax = (invokeNoArg(details, "getMaxValue") as? Number)?.toLong()
        val oldAverage = (invokeNoArg(details, "getAvgValue") as? Number)?.toLong()
        if (values == old && oldTotal == total && oldMax == max && oldAverage == average) return details

        log("device details transformed, buckets=${values.size}, totalMs=$total")
        return invokeCopy(
            details,
            arrayOf(
                total,
                values,
                max,
                average,
                invokeNoArg(details, "getLastCycle"),
            ),
        ) ?: details
    }

    private fun transformUnlockDetails(
        details: Any,
        config: ModuleConfig,
        log: (String) -> Unit,
    ): Any {
        val old = readIntList(details, "getUnlocks") ?: return details
        val requestedBucket = parseUnlockBuckets(config.unlockBuckets)
        if (config.unlockBuckets.isNotBlank() && requestedBucket == null) {
            log("unlock rule rejected: invalid bucket value")
            return details
        }
        if (requestedBucket != null && requestedBucket.size != old.size) {
            log("unlock rule rejected: bucket size=${requestedBucket.size}, expected=${old.size}")
            return details
        }
        if (requestedBucket == null && config.unlockTotal < -1) {
            log("unlock rule rejected: negative total")
            return details
        }
        val totalOnly = if (requestedBucket == null && config.unlockTotal >= 0) config.unlockTotal else null
        if (requestedBucket == null && config.unlockTotal >= 0 && totalOnly == null) return details
        if (requestedBucket == null && totalOnly == null && config.unlockTotal < 0) return details

        val values = when {
            requestedBucket != null -> requestedBucket
            totalOnly != null -> old.mapIndexed { index, _ -> if (index == old.lastIndex) totalOnly else 0 }
            else -> old
        }
        val totalLong = values.fold(0L) { accumulator, value -> accumulator + value }
        if (totalLong > Int.MAX_VALUE) {
            log("unlock rule rejected: total overflow")
            return details
        }
        val total = totalLong.toInt()
        val positive = values.count { it > 0 }
        val average = if (values.size == 24) {
            total
        } else if (positive == 0) {
            0
        } else {
            total / positive
        }
        val max = values.maxOrNull() ?: 0
        val oldTotal = (invokeNoArg(details, "getUnlockTimes") as? Number)?.toInt()
        val oldMax = (invokeNoArg(details, "getMaxValue") as? Number)?.toInt()
        val oldAverage = (invokeNoArg(details, "getAvgValue") as? Number)?.toInt()
        if (values == old && oldTotal == total && oldMax == max && oldAverage == average) return details

        log("unlock details transformed, buckets=${values.size}, total=$total")
        return invokeCopy(
            details,
            arrayOf(
                total,
                values,
                max,
                invokeNoArg(details, "getFirstTime"),
                average,
                invokeNoArg(details, "getLastCycle"),
            ),
        ) ?: details
    }

    private fun isActive(config: ModuleConfig): Boolean {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val from = config.activeFromMinute.coerceIn(0, 1440)
        val to = config.activeToMinute.coerceIn(0, 1440)
        return if (from == to) true else if (from < to) minute in from until to else minute >= from || minute < to
    }

    private fun parseAppRules(raw: String): Map<String, Long> = raw
        .split('\n', ';')
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val packageName = line.substring(0, separator).trim()
            val minutes = line.substring(separator + 1).trim().toLongOrNull()
            if (!packageName.contains('.') || minutes == null || minutes < 0L) null
            else packageName to minutes
        }
        .toMap()

    private fun parsePackageSet(raw: String): Set<String> = raw
        .split(',', '\n', ';')
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .toSet()

    private fun parseDeviceBuckets(raw: String): List<Long>? {
        if (raw.isBlank()) return null
        return raw.split(',', ' ', '\n')
            .filter { it.isNotBlank() }
            .map { it.toLongOrNull()?.let(::minutesToMillis) ?: return null }
    }

    private fun parseUnlockBuckets(raw: String): List<Int>? {
        if (raw.isBlank()) return null
        return raw.split(',', ' ', '\n')
            .filter { it.isNotBlank() }
            .map {
                val value = it.toLongOrNull() ?: return null
                if (value !in 0..Int.MAX_VALUE) return null
                value.toInt()
            }
    }

    private fun readLongList(target: Any, getter: String): List<Long>? {
        val list = invokeNoArg(target, getter) as? List<*> ?: return null
        return list.map { value ->
            val number = value as? Number ?: return null
            number.toLong().coerceAtLeast(0L)
        }
    }

    private fun readIntList(target: Any, getter: String): List<Int>? {
        val list = invokeNoArg(target, getter) as? List<*> ?: return null
        return list.map { value ->
            val number = value as? Number ?: return null
            val longValue = number.toLong()
            if (longValue !in 0..Int.MAX_VALUE) return null
            longValue.toInt()
        }
    }

    private fun entryUsage(entry: Any?): Long {
        val detail = entry?.let { invokeNoArg(it, "getDetail") }
        return (detail?.let { invokeNoArg(it, "getUsage") } as? Number)?.toLong() ?: 0L
    }

    private fun minutesToMillis(minutes: Long): Long? {
        if (minutes < 0L) return null
        return runCatching { Math.multiplyExact(minutes, MS_PER_MINUTE) }.getOrNull()
    }

    private fun safeSum(values: List<Long>): Long? = runCatching {
        values.fold(0L) { accumulator, value -> Math.addExact(accumulator, value) }
    }.getOrNull()

    private fun invokeNoArg(target: Any, name: String): Any? = runCatching {
        target.javaClass.methods.firstOrNull { method ->
            method.name == name && method.parameterCount == 0
        }?.invoke(target)
    }.getOrNull()

    /**
     * DetailPageModel 正文不在逆向档案中。已知合成构造为
     * (VisualHealthDetails, ScreenTimeDetails, c9.b, int mask, marker)，
     * 因此普通构造/copy 推定为 3 个业务参数，但这里不硬编码参数个数和顺序：
     *
     * 1. 只接受唯一一个返回本类、且恰有一个参数可接收 ScreenTimeDetails 的 copy；
     * 2. 其余参数按“返回类型完全相同的唯一无参 getter”从原对象取值；
     * 3. 任一步不唯一就返回 null，调用方保留宿主原始模型。
     */
    private fun copyDetailPageModel(original: Any, newScreenTimeDetails: Any): Any? {
        val modelClass = original.javaClass
        val copies = modelClass.methods.filter { method ->
            method.name == "copy" &&
                method.returnType == modelClass &&
                method.parameterTypes.count { it.isAssignableFrom(newScreenTimeDetails.javaClass) } == 1
        }
        if (copies.size != 1) return null
        val copyMethod = copies.single()

        val getters: List<Method> = modelClass.methods.filter { method ->
            method.parameterCount == 0 &&
                method.name != "getClass" &&
                (method.name.startsWith("get") || method.name.startsWith("is"))
        }

        val arguments = arrayOfNulls<Any?>(copyMethod.parameterCount)
        for ((index, type) in copyMethod.parameterTypes.withIndex()) {
            if (type.isAssignableFrom(newScreenTimeDetails.javaClass)) {
                arguments[index] = newScreenTimeDetails
                continue
            }
            val matches = getters.filter { it.returnType == type }
            if (matches.size != 1) return null
            // VisualHealthDetails 在屏幕时长详情中通常为 null，保留原值即可。
            arguments[index] = runCatching { matches.single().invoke(original) }
                .getOrElse { return null }
        }
        return runCatching { copyMethod.invoke(original, *arguments) }.getOrNull()
    }

    private fun invokeCopy(target: Any, args: Array<Any?>): Any? {
        val candidates = target.javaClass.methods.filter { candidate ->
            candidate.name == "copy" && candidate.parameterCount == args.size &&
                candidate.parameterTypes.indices.all { index ->
                    val value = args[index]
                    value == null || boxed(candidate.parameterTypes[index]).isInstance(value)
                }
        }
        if (candidates.size != 1) return null
        return runCatching { candidates.single().invoke(target, *args) }.getOrNull()
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> type
    }
}

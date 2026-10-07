package com.kosong.misettings

import android.content.Context
import android.net.Uri
import android.os.Bundle

object ConfigContract {
    const val AUTHORITY = "com.kosong.misettings.screentime.config"
    const val METHOD_GET = "get_config"
    const val METHOD_LOG = "append_log"
    const val EXPECTED_PACKAGE = "com.xiaomi.misettings"
    const val EXPECTED_VERSION_CODE = 250722130L
    const val EXPECTED_VERSION_NAME = "15.00.0722.01-phone"
    const val SCHEMA = 1
}

data class ModuleConfig(
    val revision: Long = 0L,
    val enabled: Boolean = false,
    val applyHome: Boolean = true,
    val applyDetail: Boolean = true,
    val modifyDevice: Boolean = false,
    val modifyUnlock: Boolean = false,
    val modifyApps: Boolean = false,
    val deviceTotalMinutes: Long = -1L,
    val deviceBucketsMinutes: String = "",
    val unlockTotal: Int = -1,
    val unlockBuckets: String = "",
    val appRules: String = "",
    val hidePackages: String = "",
    val logEnabled: Boolean = true,
    val logTreeUri: String = "",
    val onlyTodayBucket: Boolean = false,
    val activeFromMinute: Int = 0,
    val activeToMinute: Int = 1440,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putInt("schema", ConfigContract.SCHEMA)
        putLong("revision", revision)
        putBoolean("enabled", enabled)
        putBoolean("applyHome", applyHome)
        putBoolean("applyDetail", applyDetail)
        putBoolean("modifyDevice", modifyDevice)
        putBoolean("modifyUnlock", modifyUnlock)
        putBoolean("modifyApps", modifyApps)
        putLong("deviceTotalMinutes", deviceTotalMinutes)
        putString("deviceBucketsMinutes", deviceBucketsMinutes)
        putInt("unlockTotal", unlockTotal)
        putString("unlockBuckets", unlockBuckets)
        putString("appRules", appRules)
        putString("hidePackages", hidePackages)
        putBoolean("logEnabled", logEnabled)
        putString("logTreeUri", logTreeUri)
        putBoolean("onlyTodayBucket", onlyTodayBucket)
        putInt("activeFromMinute", activeFromMinute)
        putInt("activeToMinute", activeToMinute)
    }

    companion object {
        fun fromBundle(bundle: Bundle?): ModuleConfig {
            if (bundle == null || bundle.getInt("schema", -1) != ConfigContract.SCHEMA) {
                return ModuleConfig()
            }
            return ModuleConfig(
                revision = bundle.getLong("revision", 0L),
                enabled = bundle.getBoolean("enabled", false),
                applyHome = bundle.getBoolean("applyHome", true),
                applyDetail = bundle.getBoolean("applyDetail", true),
                modifyDevice = bundle.getBoolean("modifyDevice", false),
                modifyUnlock = bundle.getBoolean("modifyUnlock", false),
                modifyApps = bundle.getBoolean("modifyApps", false),
                deviceTotalMinutes = bundle.getLong("deviceTotalMinutes", -1L),
                deviceBucketsMinutes = bundle.getString("deviceBucketsMinutes", "") ?: "",
                unlockTotal = bundle.getInt("unlockTotal", -1),
                unlockBuckets = bundle.getString("unlockBuckets", "") ?: "",
                appRules = bundle.getString("appRules", "") ?: "",
                hidePackages = bundle.getString("hidePackages", "") ?: "",
                logEnabled = bundle.getBoolean("logEnabled", true),
                logTreeUri = bundle.getString("logTreeUri", "") ?: "",
                onlyTodayBucket = bundle.getBoolean("onlyTodayBucket", false),
                activeFromMinute = bundle.getInt("activeFromMinute", 0),
                activeToMinute = bundle.getInt("activeToMinute", 1440),
            )
        }
    }
}

class ConfigStore(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(
        "screen_time_config",
        Context.MODE_PRIVATE,
    )

    fun load(): ModuleConfig = ModuleConfig(
        revision = prefs.getLong("revision", 0L),
        enabled = prefs.getBoolean("enabled", false),
        applyHome = prefs.getBoolean("applyHome", true),
        applyDetail = prefs.getBoolean("applyDetail", true),
        modifyDevice = prefs.getBoolean("modifyDevice", false),
        modifyUnlock = prefs.getBoolean("modifyUnlock", false),
        modifyApps = prefs.getBoolean("modifyApps", false),
        deviceTotalMinutes = prefs.getLong("deviceTotalMinutes", -1L),
        deviceBucketsMinutes = prefs.getString("deviceBucketsMinutes", "") ?: "",
        unlockTotal = prefs.getInt("unlockTotal", -1),
        unlockBuckets = prefs.getString("unlockBuckets", "") ?: "",
        appRules = prefs.getString("appRules", "") ?: "",
        hidePackages = prefs.getString("hidePackages", "") ?: "",
        logEnabled = prefs.getBoolean("logEnabled", true),
        logTreeUri = prefs.getString("logTreeUri", "") ?: "",
        onlyTodayBucket = prefs.getBoolean("onlyTodayBucket", false),
        activeFromMinute = prefs.getInt("activeFromMinute", 0),
        activeToMinute = prefs.getInt("activeToMinute", 1440),
    )

    /** 保存不可变快照，并返回实际写入的 revision。 */
    fun save(config: ModuleConfig): ModuleConfig {
        val nextRevision = maxOf(
            System.currentTimeMillis(),
            prefs.getLong("revision", 0L) + 1L,
        )
        val saved = config.copy(revision = nextRevision)
        prefs.edit()
            .putLong("revision", saved.revision)
            .putBoolean("enabled", saved.enabled)
            .putBoolean("applyHome", saved.applyHome)
            .putBoolean("applyDetail", saved.applyDetail)
            .putBoolean("modifyDevice", saved.modifyDevice)
            .putBoolean("modifyUnlock", saved.modifyUnlock)
            .putBoolean("modifyApps", saved.modifyApps)
            .putLong("deviceTotalMinutes", saved.deviceTotalMinutes)
            .putString("deviceBucketsMinutes", saved.deviceBucketsMinutes)
            .putInt("unlockTotal", saved.unlockTotal)
            .putString("unlockBuckets", saved.unlockBuckets)
            .putString("appRules", saved.appRules)
            .putString("hidePackages", saved.hidePackages)
            .putBoolean("logEnabled", saved.logEnabled)
            .putString("logTreeUri", saved.logTreeUri)
            .putBoolean("onlyTodayBucket", saved.onlyTodayBucket)
            .putInt("activeFromMinute", saved.activeFromMinute)
            .putInt("activeToMinute", saved.activeToMinute)
            .apply()
        appContext.contentResolver.notifyChange(
            Uri.parse("content://${ConfigContract.AUTHORITY}/config"),
            null,
        )
        return saved
    }
}

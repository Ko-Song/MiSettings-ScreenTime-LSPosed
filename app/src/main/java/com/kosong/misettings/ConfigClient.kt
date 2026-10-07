package com.kosong.misettings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 目标进程中的只读配置客户端。
 *
 * 配置读取通过 Provider 异步刷新，Hook 回调只读取不可变快照，避免把 IPC 或磁盘 IO
 * 放到小米设置的 UI 线程。Provider 不可用时保留默认关闭配置。
 */
object ConfigClient {
    private const val TAG = "MiSettingsST"
    private const val REFRESH_INTERVAL_MS = 1_000L

    private val uri = Uri.parse("content://${ConfigContract.AUTHORITY}/config")
    private val snapshot = AtomicReference(ModuleConfig())
    private val refreshInFlight = AtomicBoolean(false)
    private val refreshExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MiSettingsST-config").apply { isDaemon = true }
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var lastRefreshAt = 0L

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, changedUri: Uri?) {
            // Provider 保存配置后会发出通知；回调本身只调度后台读取。
            refreshAsync()
        }
    }

    /** 初始化目标进程配置观察器；同一个进程只注册一次。 */
    fun initialize(context: Context) {
        val applicationContext = context.applicationContext ?: context
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            appContext = applicationContext
            runCatching {
                applicationContext.contentResolver.registerContentObserver(uri, false, observer)
            }.onFailure {
                Log.w(TAG, "配置观察器注册失败，将使用定时刷新", it)
            }
            refreshAsync()
        }
    }

    /** 返回当前不可变快照，并在过期时调度一次后台刷新。 */
    fun read(context: Context): ModuleConfig {
        initialize(context)
        if (System.currentTimeMillis() - lastRefreshAt >= REFRESH_INTERVAL_MS) {
            refreshAsync()
        }
        return snapshot.get()
    }

    /** 在后台读取 Provider；读取失败保持上一个快照，首次失败则保持默认关闭。 */
    private fun refreshAsync() {
        val context = appContext ?: return
        if (!refreshInFlight.compareAndSet(false, true)) return
        refreshExecutor.execute {
            try {
                val bundle = context.contentResolver.call(
                    uri,
                    ConfigContract.METHOD_GET,
                    null,
                    null,
                )
                if (bundle?.getBoolean("ok", false) == true) {
                    snapshot.set(ModuleConfig.fromBundle(bundle))
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "配置 Provider 不可用，保留当前快照", throwable)
            } finally {
                lastRefreshAt = System.currentTimeMillis()
                refreshInFlight.set(false)
            }
        }
    }
}

/**
 * 目标进程日志桥接器。
 * 日志只通过有界职责明确的单线程队列发送，不阻断宿主页面渲染。
 */
object ModuleLogBridge {
    private const val TAG = "MiSettingsST"
    private val uri = Uri.parse("content://${ConfigContract.AUTHORITY}/config")
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MiSettingsST-log").apply { isDaemon = true }
    }

    /** 异步投递一条模块功能日志；Provider 失败时只记录 Logcat。 */
    fun write(context: Context, message: String) {
        if (message.isBlank()) return
        val applicationContext = context.applicationContext ?: context
        executor.execute {
            runCatching {
                applicationContext.contentResolver.call(
                    uri,
                    ConfigContract.METHOD_LOG,
                    null,
                    Bundle().apply { putString("message", message) },
                )
            }.onFailure {
                Log.w(TAG, "模块日志投递失败", it)
            }
        }
    }
}

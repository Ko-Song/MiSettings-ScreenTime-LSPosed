package com.kosong.misettings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var store: ConfigStore
    private lateinit var logPicker: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        scheduleCleanup()
        logPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            store.save(store.load().copy(logTreeUri = uri.toString()))
        }
        setContent { SettingsScreen() }
    }

    private fun scheduleCleanup() {
        val request = PeriodicWorkRequestBuilder<LogCleanupWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "screen-time-log-cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun pickLogDirectory() {
        logPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }

    @androidx.compose.runtime.Composable
    private fun SettingsScreen() {
        val initial = remember { store.load() }
        var config by remember { mutableStateOf(initial) }
        var deviceMinutes by remember { mutableStateOf(if (config.deviceTotalMinutes >= 0) config.deviceTotalMinutes.toString() else "") }
        var unlockCount by remember { mutableStateOf(if (config.unlockTotal >= 0) config.unlockTotal.toString() else "") }
        var deviceBuckets by remember { mutableStateOf(config.deviceBucketsMinutes) }
        var unlockBuckets by remember { mutableStateOf(config.unlockBuckets) }
        var appRules by remember { mutableStateOf(config.appRules) }
        var hidePackages by remember { mutableStateOf(config.hidePackages) }
        var fromMinute by remember { mutableStateOf(config.activeFromMinute.toString()) }
        var toMinute by remember { mutableStateOf(config.activeToMinute.toString()) }
        var message by remember { mutableStateOf("默认关闭，保存后需强制停止小米设置再测试") }

        fun save(next: ModuleConfig) {
            config = store.save(next)
            message = "已保存配置 revision=${config.revision}"
        }

        MiuixTheme {
            Scaffold { padding ->
                LazyColumn(
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SmallTitle("小米设置 / 健康使用手机")
                        Text(
                            "目标版本：com.xiaomi.misettings 15.00.0722.01-phone (250722130)",
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Text(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            SwitchPreference(
                                checked = config.enabled,
                                onCheckedChange = { config = config.copy(enabled = it) },
                                title = "启用显示覆盖",
                                summary = "仅在目标版本和目标进程中生效",
                            )
                            SwitchPreference(
                                checked = config.applyHome,
                                onCheckedChange = { config = config.copy(applyHome = it) },
                                title = "首页",
                            )
                            SwitchPreference(
                                checked = config.applyDetail,
                                onCheckedChange = { config = config.copy(applyDetail = it) },
                                title = "屏幕使用时长详情页",
                            )
                            SwitchPreference(
                                checked = config.modifyDevice,
                                onCheckedChange = { config = config.copy(modifyDevice = it) },
                                title = "修改设备时长",
                            )
                            SwitchPreference(
                                checked = config.modifyUnlock,
                                onCheckedChange = { config = config.copy(modifyUnlock = it) },
                                title = "修改解锁统计",
                            )
                            SwitchPreference(
                                checked = config.modifyApps,
                                onCheckedChange = { config = config.copy(modifyApps = it) },
                                title = "修改应用使用情况",
                            )
                            SwitchPreference(
                                checked = config.logEnabled,
                                onCheckedChange = { config = config.copy(logEnabled = it) },
                                title = "启用模块日志",
                            )
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            SmallTitle("设备时长规则")
                            TextField(
                                value = TextFieldValue(deviceMinutes),
                                onValueChange = { deviceMinutes = it.text },
                                label = "总时长（分钟，留空不覆盖）",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                singleLine = true,
                            )
                            TextField(
                                value = TextFieldValue(deviceBuckets),
                                onValueChange = { deviceBuckets = it.text },
                                label = "分桶分钟：24/7/自然月项，逗号分隔",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                maxLines = 3,
                            )
                            SmallTitle("解锁规则")
                            TextField(
                                value = TextFieldValue(unlockCount),
                                onValueChange = { unlockCount = it.text },
                                label = "总次数（留空不覆盖）",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                singleLine = true,
                            )
                            TextField(
                                value = TextFieldValue(unlockBuckets),
                                onValueChange = { unlockBuckets = it.text },
                                label = "解锁分桶次数，逗号分隔",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                maxLines = 3,
                            )
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            SmallTitle("应用规则")
                            TextField(
                                value = TextFieldValue(appRules),
                                onValueChange = { appRules = it.text },
                                label = "每行：包名=分钟，例如 com.example.app=30",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                maxLines = 5,
                            )
                            TextField(
                                value = TextFieldValue(hidePackages),
                                onValueChange = { hidePackages = it.text },
                                label = "隐藏包名，逗号或换行分隔",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                maxLines = 4,
                            )
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            SmallTitle("生效时段")
                            TextField(
                                value = TextFieldValue(fromMinute),
                                onValueChange = { fromMinute = it.text },
                                label = "开始分钟（0-1440）",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                singleLine = true,
                            )
                            TextField(
                                value = TextFieldValue(toMinute),
                                onValueChange = { toMinute = it.text },
                                label = "结束分钟（0-1440；相等表示全天）",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                singleLine = true,
                            )
                            Button(
                                onClick = { pickLogDirectory() },
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            ) { Text("选择日志目录") }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                save(config.copy(
                                    deviceTotalMinutes = deviceMinutes.toLongOrNull() ?: -1L,
                                    deviceBucketsMinutes = deviceBuckets,
                                    unlockTotal = unlockCount.toIntOrNull() ?: -1,
                                    unlockBuckets = unlockBuckets,
                                    appRules = appRules,
                                    hidePackages = hidePackages,
                                    activeFromMinute = fromMinute.toIntOrNull()?.coerceIn(0, 1440) ?: 0,
                                    activeToMinute = toMinute.toIntOrNull()?.coerceIn(0, 1440) ?: 1440,
                                ))
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        ) { Text("保存并应用") }
                    }
                }
            }
        }
    }
}

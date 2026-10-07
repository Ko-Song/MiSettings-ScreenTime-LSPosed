package com.kosong.misettings

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class LogCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        ModuleLog(applicationContext).cleanup()
        return Result.success()
    }
}

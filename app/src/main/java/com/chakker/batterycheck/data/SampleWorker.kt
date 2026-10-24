package com.chakker.batterycheck.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Records a battery sample roughly every 15 minutes so drain can be measured over time. */
class SampleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        SampleStore(applicationContext).append(BatteryReader.read(applicationContext))
        return Result.success()
    }

    companion object {
        private const val NAME = "battery-sampler"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SampleWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

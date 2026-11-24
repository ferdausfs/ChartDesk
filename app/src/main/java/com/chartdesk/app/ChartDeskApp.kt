package com.chartdesk.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.chartdesk.app.data.alerts.AlertCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class ChartDeskApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleAlertChecks()
    }

    private fun scheduleAlertChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<AlertCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork("chartdesk-alert-check", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        workManager.enqueue(OneTimeWorkRequestBuilder<AlertCheckWorker>().setConstraints(constraints).build())
    }
}

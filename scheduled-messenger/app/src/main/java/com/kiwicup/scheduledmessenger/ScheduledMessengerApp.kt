package com.kiwicup.scheduledmessenger

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
import com.kiwicup.scheduledmessenger.diagnostics.CrashHandler
import com.kiwicup.scheduledmessenger.work.RearmWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ScheduledMessengerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Before anything else: a crash during Hilt's own component setup is still worth a
        // report, and there is no earlier point in the process to install one from.
        AppLog.init(this)
        CrashHandler.install(this)
        super.onCreate()
        // Belt and braces: the boot receiver covers reboots, this covers app updates and force-stops.
        RearmWorker.enqueue(this)
    }
}

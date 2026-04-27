package com.digitalgram.android

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.digitalgram.android.data.AppSettings
import com.digitalgram.android.data.JournalDatabase

class DigitalGramApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        // Warm up singletons off the first-activity hot path
        AppSettings.getInstance(this)
        JournalDatabase.getInstance(this)

        // Cancel JournalDatabase coroutine scope when the process is destroyed
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                try { JournalDatabase.getInstance(this@DigitalGramApplication).cancelScope() } catch (_: Throwable) {}
            }
        })
    }
}

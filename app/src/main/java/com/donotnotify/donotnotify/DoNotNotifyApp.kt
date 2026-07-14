package com.donotnotify.donotnotify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.donotnotify.donotnotify.health.HealthCheckWorker

class DoNotNotifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createHealthChannel()
        // Each STACK rule owns a notification channel, so the user can set its sound/vibration in
        // system settings. Reconcile them (and retire the old shared channel) at process start.
        StackChannelsAndroid.sync(this)
        HealthCheckWorker.enqueue(this)
        // Disable any stale fail-closed prebuilt allowlists if the device language changed
        // since they were installed. Runs at process start for both the UI and the listener.
        PrebuiltRuleReconciler.reconcileIfLocaleChanged(this)
    }

    private fun createHealthChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            HealthCheckWorker.CHANNEL_ID,
            getString(R.string.health_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.health_channel_description)
        }
        nm.createNotificationChannel(channel)
    }
}

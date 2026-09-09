package com.gbhall.childlock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class ChildLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
        // MIN importance: present in the shade for the foreground service, but no
        // status-bar icon, so the on-screen badge is the one and only indicator.
        val channel = NotificationChannel(
            CHANNEL_LOCK,
            getString(R.string.channel_lock),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.channel_lock_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_LOCK = "lock_quiet"
        private const val LEGACY_CHANNEL = "lock"
    }
}

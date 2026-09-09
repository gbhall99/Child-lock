package com.gbhall.childlock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class ChildLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_LOCK,
            getString(R.string.channel_lock),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_lock_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_LOCK = "lock"
    }
}

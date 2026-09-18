package com.gbhall.childlock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.gbhall.childlock.billing.Billing
import com.gbhall.childlock.billing.FeatureGate
import com.gbhall.childlock.review.ReviewSignals

// Open so tests can put a fake store in front of the real application.
open class ChildLockApp : Application() {
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
        // A process that starts while a lock was recorded died locked; that is remembered.
        ReviewSignals.onAppStart(this)
        // The trial runs from first launch, whichever screen or service asks first.
        FeatureGate.trialStart(this)
        // Restores a purchase from Play before any screen or service asks the gate.
        Billing.backend.connect(this)
    }

    companion object {
        const val CHANNEL_LOCK = "lock_quiet"
        private const val LEGACY_CHANNEL = "lock"
    }
}

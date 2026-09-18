package com.gbhall.childlock.billing

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Single choke point for the free trial and the one-time purchase.
 *
 * Every build starts a [TRIAL_DAYS]-day trial on first launch. After that
 * the lock will not engage until the app is bought once. Unlocking is never
 * gated: a trial that ends while the phone is locked still lets the parent
 * out. Builds that cannot sell ([PurchaseBackend.canSell] false) are simply
 * unlocked.
 *
 * The purchase itself lives with Google Play and is restored from there on
 * every launch, so a reinstall never loses it. The trial start is only on
 * the device: clearing the app's data starts the trial again, which is an
 * accepted cost of keeping the app free of accounts and servers.
 */
object FeatureGate {
    const val TRIAL_DAYS = 30

    /** The one-time product configured in Play Console. Changing it orphans every existing purchase. */
    const val PRODUCT_ID = "childlock_full"

    sealed class Access {
        object Purchased : Access()
        data class Trial(val daysLeft: Int) : Access()
        object Expired : Access()
    }

    /** Wall-clock source, replaceable in tests. */
    var clock: () -> Long = { System.currentTimeMillis() }

    private const val PREFS = "childlock"
    private const val KEY_PRO = "pro_unlocked"
    private const val KEY_TRIAL_START = "trial_start_ms"
    private const val KEY_PREVIEW_EXPIRED = "pro_preview_free"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun access(context: Context): Access {
        if (!Billing.backend.canSell) return Access.Purchased
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_PREVIEW_EXPIRED, false)) return Access.Expired
        if (prefs.getBoolean(KEY_PRO, false)) return Access.Purchased
        val elapsed = (clock() - trialStart(context)).coerceAtLeast(0L)
        val left = TRIAL_DAYS * DAY_MS - elapsed
        return if (left <= 0L) Access.Expired else Access.Trial(((left + DAY_MS - 1) / DAY_MS).toInt())
    }

    /** Whether a new lock may start. Never consulted for unlocking. */
    fun isUnlocked(context: Context): Boolean = access(context) !is Access.Expired

    fun isPurchased(context: Context): Boolean = prefs(context).getBoolean(KEY_PRO, false)

    /** When the trial began, recorded the first time anything asks. */
    fun trialStart(context: Context): Long {
        val prefs = prefs(context)
        val stored = prefs.getLong(KEY_TRIAL_START, 0L)
        if (stored > 0L) return stored
        val now = clock()
        prefs.edit().putLong(KEY_TRIAL_START, now).apply()
        return now
    }

    /** Called by the billing backend with what the store says this account owns. */
    fun recordPurchase(context: Context, purchased: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRO, purchased).apply()
    }

    /** Debug-only: shows the app as it looks after the trial has ended. */
    fun setPreviewExpired(context: Context, preview: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREVIEW_EXPIRED, preview).apply()
    }

    fun isPreviewingExpired(context: Context): Boolean = prefs(context).getBoolean(KEY_PREVIEW_EXPIRED, false)

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

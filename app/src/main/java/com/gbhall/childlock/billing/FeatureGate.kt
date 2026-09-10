package com.gbhall.childlock.billing

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Single choke point for the free/Pro split (see MONETISATION.md).
 *
 * Pro is true when a purchase has been recorded, or in debuggable builds so
 * the owner can test everything. Play Billing plugs in by calling
 * [recordPurchase] from the purchase callback; nothing else changes.
 */
object FeatureGate {
    enum class Feature { AUTO_LOCK, SKIP_ADS, RELOCK, CUSTOM_BADGE }

    const val PRICE_LABEL = "£2.99"
    private const val PREFS = "childlock"
    private const val KEY_PRO = "pro_unlocked"
    private const val KEY_PREVIEW_FREE = "pro_preview_free"

    fun isPro(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PREVIEW_FREE, false)) return false
        if (prefs.getBoolean(KEY_PRO, false)) return true
        return isDebuggable(context)
    }

    fun has(context: Context, feature: Feature): Boolean = when (feature) {
        Feature.AUTO_LOCK, Feature.SKIP_ADS, Feature.RELOCK, Feature.CUSTOM_BADGE -> isPro(context)
    }

    /** Called by the billing integration once a purchase is verified. */
    fun recordPurchase(context: Context, purchased: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRO, purchased).apply()
    }

    /** Debug-only: lets the owner see the free experience. */
    fun setPreviewFree(context: Context, preview: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREVIEW_FREE, preview).apply()
    }

    fun isPreviewingFree(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PREVIEW_FREE, false)

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

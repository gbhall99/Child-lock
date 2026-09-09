package com.gbhall.childlock.billing

import android.content.Context

/**
 * Single choke point for the freemium split described in MONETISATION.md.
 * This build unlocks everything. When Play Billing is wired in, [isPro]
 * becomes the cached purchase state and nothing else in the app changes.
 */
object FeatureGate {
    enum class Feature { VOLUME_GESTURES, SWIPE_BLOCKING, BADGE_PIN, CUSTOM_BADGE }

    @Suppress("UNUSED_PARAMETER")
    fun isPro(context: Context): Boolean = true

    fun has(context: Context, feature: Feature): Boolean = when (feature) {
        Feature.VOLUME_GESTURES, Feature.SWIPE_BLOCKING, Feature.BADGE_PIN, Feature.CUSTOM_BADGE -> isPro(context)
    }
}

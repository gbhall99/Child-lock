package com.gbhall.childlock.guard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Play flavour: skip-ad tapping is not part of this build.
 *
 * Tapping another app's "Skip ad" button conflicts with Google Play's Device
 * and Network Abuse policy and with YouTube's terms, and it is the only thing
 * that would make Child Lock read screen content. The capability is compiled
 * out rather than merely hidden, so the released app cannot do it at all.
 */
object SkipAdFeature {
    const val AVAILABLE = false

    /** No strings either: the Play build never mentions the feature. */
    const val TITLE_RES = 0
    const val DESC_RES = 0
    const val HELP_RES = 0

    @Suppress("UNUSED_PARAMETER")
    fun isSupported(packageName: String): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun clickSkip(root: AccessibilityNodeInfo, packageName: String, windowWidth: Int, windowHeight: Int): Boolean = false
}

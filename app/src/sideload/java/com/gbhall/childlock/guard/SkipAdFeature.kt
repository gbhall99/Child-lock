package com.gbhall.childlock.guard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Sideload flavour: skip-ad tapping is present.
 *
 * The Play flavour replaces this file with a stub that compiles the whole
 * capability out, because tapping another app's "Skip ad" button conflicts
 * with the Device and Network Abuse policy and with YouTube's terms. See
 * REVIEW.md, group D.
 */
object SkipAdFeature {
    const val AVAILABLE = true

    fun isSupported(packageName: String): Boolean = SkipAdMatcher.isSupported(packageName)

    /**
     * Finds and clicks a genuine skip-ad button in [root], which must already
     * be the window of [packageName]. Returns true if one was clicked.
     */
    fun clickSkip(root: AccessibilityNodeInfo, packageName: String, windowWidth: Int, windowHeight: Int): Boolean {
        val bounds = android.graphics.Rect()
        val hits = root.findAccessibilityNodeInfosByText("skip") ?: return false
        for (node in hits) {
            node.getBoundsInScreen(bounds)
            val parentText = node.parent?.let { p -> p.text ?: p.contentDescription }
            val ok = SkipAdMatcher.isCandidate(
                packageName, node.text, node.contentDescription, parentText,
                node.isClickable, node.isEnabled, node.isVisibleToUser, node.isEditable,
                bounds.width(), bounds.height(), windowWidth, windowHeight,
            )
            if (ok && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }
}

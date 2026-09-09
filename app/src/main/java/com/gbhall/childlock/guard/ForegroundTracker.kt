package com.gbhall.childlock.guard

/**
 * Last launchable app seen in the foreground, other than Child Lock itself.
 * Populated by the accessibility service; used as the app to protect when the
 * lock is armed from the tile or the settings screen.
 */
object ForegroundTracker {
    @Volatile
    var lastApp: String? = null
}

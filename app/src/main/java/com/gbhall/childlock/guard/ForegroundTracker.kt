package com.gbhall.childlock.guard

/**
 * What is in front, as the accessibility service last saw it.
 *
 * [lastApp] is the last launchable app other than Child Lock itself: the app
 * the in-app lock button goes back to. [current] is what a lock started from
 * where the user is now should keep in front: the volume pattern and the tile
 * use it, so locking on the home screen keeps the home screen rather than
 * jumping back to an older app.
 */
object ForegroundTracker {
    @Volatile
    var lastApp: String? = null

    /** The home screen came to the front after [lastApp]. */
    @Volatile
    var homeInFront: Boolean = false

    val current: String? get() = if (homeInFront) null else lastApp

    fun reset() {
        lastApp = null
        homeInFront = false
    }
}

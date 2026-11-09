package com.gbhall.childlock.guard

/**
 * What is in front.
 *
 * [lastApp] and [homeInFront] are history: what the accessibility service last
 * heard about. History goes stale (a late event from the app just left, a
 * launcher page that announces nothing, a screen that is not an app at all),
 * so it only answers "which app was the parent using before opening Child
 * Lock", which nothing on screen can.
 *
 * What a lock should keep in front is read off the screen instead, at the
 * moment the lock engages: see [inFront] and [protectNow].
 */
object ForegroundTracker {
    /** What is on screen right now, as far as a lock is concerned. */
    sealed interface Front {
        /** An ordinary app: a lock keeps it in front and brings it back. */
        data class App(val packageName: String) : Front

        /** The home screen: a lock there stays there. */
        data object Home : Front

        /** Child Lock itself, Settings or another screen that is never brought back. */
        data object Other : Front

        /** The helper is not running, or could not tell. */
        data object Unknown : Front
    }

    /** The last launchable app other than Child Lock: where the in-app lock button goes back to. */
    @Volatile
    var lastApp: String? = null

    /** The home screen came to the front after [lastApp]. */
    @Volatile
    var homeInFront: Boolean = false

    /** The best guess from history alone; only used when the screen itself cannot be read. */
    val current: String? get() = if (homeInFront) null else lastApp

    /** Set by the accessibility service while it runs: reads the screen as it is now. */
    @Volatile
    var probe: (() -> Front)? = null

    fun inFront(): Front = try {
        probe?.invoke() ?: Front.Unknown
    } catch (e: Exception) {
        Front.Unknown
    }

    /**
     * The app a lock engaging right now should keep in front: whatever is on
     * screen, never an app remembered from earlier. [fallback] is used only
     * when the screen cannot be read.
     */
    fun protectNow(fallback: String?): String? = when (val front = inFront()) {
        is Front.App -> front.packageName
        Front.Home, Front.Other -> null
        Front.Unknown -> fallback
    }

    fun reset() {
        lastApp = null
        homeInFront = false
    }
}

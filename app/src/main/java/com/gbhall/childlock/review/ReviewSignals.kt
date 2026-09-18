package com.gbhall.childlock.review

import android.content.Context
import com.gbhall.childlock.lock.UnlockReason

/**
 * Decides when a parent is likely happy enough to be asked for a review.
 *
 * Google forbids asking "do you like the app?" first, so the judgement rests
 * on behaviour alone: a lock that ran for a while and was ended on purpose
 * is a session that worked. A lock the parent had to escape with the
 * three-finger fallback, or that ended with the process dying (a forced
 * restart, the one way out when everything else fails), is distress, and
 * nobody is asked for a fortnight after it. The question is asked once.
 */
object ReviewSignals {
    /** A lock shorter than this proves nothing either way. */
    const val GOOD_SESSION_MS = 2 * 60_000L
    const val GOOD_SESSIONS_NEEDED = 3
    const val DISTRESS_QUIET_MS = 14L * 24 * 60 * 60 * 1000

    var clock: () -> Long = { System.currentTimeMillis() }

    private const val PREFS = "childlock"
    private const val KEY_GOOD = "review_good_sessions"
    private const val KEY_DISTRESS = "review_distress_ms"
    private const val KEY_ASKED = "review_asked_ms"
    private const val KEY_LOCKED_SINCE = "review_locked_since_ms"

    /** A real (not practice) lock attached. Remembered on disk so a death while locked is noticed. */
    fun onLocked(context: Context) {
        prefs(context).edit().putLong(KEY_LOCKED_SINCE, clock()).apply()
    }

    /** That lock ended, after [durationMs], for [reason]. */
    fun onUnlocked(context: Context, durationMs: Long, reason: UnlockReason) {
        val p = prefs(context)
        val e = p.edit().remove(KEY_LOCKED_SINCE)
        when (reason) {
            UnlockReason.FALLBACK, UnlockReason.SYSTEM -> e.putLong(KEY_DISTRESS, clock())
            UnlockReason.PARENT, UnlockReason.TIMER ->
                if (durationMs >= GOOD_SESSION_MS) e.putInt(KEY_GOOD, p.getInt(KEY_GOOD, 0) + 1)
            UnlockReason.CALL -> Unit
        }
        e.apply()
    }

    /** The process started while a lock was recorded as running: it died locked. */
    fun onAppStart(context: Context) {
        val p = prefs(context)
        if (p.getLong(KEY_LOCKED_SINCE, 0L) != 0L) {
            p.edit().remove(KEY_LOCKED_SINCE).putLong(KEY_DISTRESS, clock()).apply()
        }
    }

    fun goodSessions(context: Context): Int = prefs(context).getInt(KEY_GOOD, 0)

    fun shouldAsk(context: Context): Boolean {
        val p = prefs(context)
        if (p.getLong(KEY_ASKED, 0L) != 0L) return false
        if (p.getLong(KEY_LOCKED_SINCE, 0L) != 0L) return false // locked right now
        val distress = p.getLong(KEY_DISTRESS, 0L)
        if (distress != 0L && clock() - distress < DISTRESS_QUIET_MS) return false
        return p.getInt(KEY_GOOD, 0) >= GOOD_SESSIONS_NEEDED
    }

    fun markAsked(context: Context) {
        prefs(context).edit().putLong(KEY_ASKED, clock()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

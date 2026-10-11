package com.gbhall.childlock.lock

/**
 * No app can stop the power button: Android hands it to the system before any
 * app sees it. What Child Lock can do is undo a child's press, by turning the
 * screen straight back on and, if a video was playing, pressing play again.
 *
 * This decides whether to, a moment after the screen went dark during a lock.
 * It only ever chooses to turn the screen on, so whatever it decides, the
 * power menu, emergency calls and a forced restart are untouched.
 *
 *  - Never when the phone has actually locked itself: the lock screen belongs
 *    to the parent, and getting past it is a way out of Child Lock.
 *  - Never when the screen could have gone dark on its own (keep-screen-on
 *    switched off), so it can never fight the phone's own sleep.
 *  - Never twice in quick succession: a press soon after a wake is someone
 *    who means it, and the screen stays off.
 *
 * Holds no Android types so it can be tested on its own.
 */
class ScreenWakeGuard {
    enum class Decision { LEAVE, WAKE, WAKE_AND_RESUME }

    private var lastWakeMs = NEVER
    private var mediaHeardMs = NEVER

    /** Called whenever media is heard playing while locked. */
    fun onMediaPlaying(nowMs: Long) {
        mediaHeardMs = nowMs
    }

    fun decide(nowMs: Long, enabled: Boolean, keepScreenOn: Boolean, phoneLocked: Boolean): Decision {
        if (!enabled || !keepScreenOn || phoneLocked) return Decision.LEAVE
        if (lastWakeMs != NEVER && nowMs - lastWakeMs < PRESSED_AGAIN_MS) {
            // Off again straight after a wake: leave it off, and treat the next
            // press as a fresh one.
            lastWakeMs = NEVER
            return Decision.LEAVE
        }
        lastWakeMs = nowMs
        val wasPlaying = mediaHeardMs != NEVER && nowMs - mediaHeardMs <= MEDIA_RECENT_MS
        return if (wasPlaying) Decision.WAKE_AND_RESUME else Decision.WAKE
    }

    fun reset() {
        lastWakeMs = NEVER
        mediaHeardMs = NEVER
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE

        /** A second press within this long of a wake keeps the screen off. */
        const val PRESSED_AGAIN_MS = 10_000L

        /** Media heard this recently before the screen went dark counts as playing. */
        const val MEDIA_RECENT_MS = 3_000L
    }
}

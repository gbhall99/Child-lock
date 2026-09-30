package com.gbhall.childlock.settings

import android.content.Context
import com.gbhall.childlock.R
import com.gbhall.childlock.gesture.VolumePattern

/** Human wording for the configured gestures, shared by the screen and the notification. */
object GestureText {
    fun patternWords(context: Context, s: LockSettings): String {
        val base = context.getString(
            if (s.volumePattern == VolumePattern.UP_THEN_DOWN) R.string.pattern_up_down else R.string.pattern_down_up,
        )
        return if (s.volumeRepeats >= 2) context.getString(R.string.pattern_twice, base) else base
    }

    /** The allowed unlocks in a fixed, sensible order. */
    fun ordered(s: LockSettings): List<GestureType> = GestureType.entries.filter { it in s.gestures }

    private fun hint(context: Context, s: LockSettings, g: GestureType): String = when (g) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.hint_volume_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.hint_corner_hold)
        GestureType.BADGE_PIN -> context.getString(R.string.hint_badge_pin)
    }

    /** Every allowed unlock, the main one first, the rest each introduced with "Or". */
    fun unlockHint(context: Context, s: LockSettings): String =
        ordered(s).mapIndexed { i, g ->
            val h = hint(context, s, g)
            if (i == 0) h else context.getString(R.string.hint_or, h.replaceFirstChar { it.lowercase() })
        }.joinToString(" ")

    /**
     * The one unlock that exists without a switch of its own: the three-finger
     * triple tap, done twice, while swipe blocking has the screen in the touch
     * mode screen readers use. It is there because touch unlocks cannot fire
     * in that mode. Everywhere else, what is allowed is exactly what is named.
     */
    fun fallbackHint(context: Context, s: LockSettings): String {
        // Mirrors GuardPolicy.gestureBlockFlags: explore-by-touch is only ever requested on API 30+.
        val exploring = s.hasVolumeGesture && s.blockGestures &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        return if (exploring) context.getString(R.string.fallback_three_finger) else ""
    }

    /** Whether getting past the phone's own lock screen takes a face, fingerprint or PIN. */
    fun phoneLockIsSecure(context: Context): Boolean = try {
        context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
    } catch (e: Exception) {
        false
    }

    /** Power and volume down is Samsung's restart; on every other phone it takes a screenshot. */
    private fun isSamsung(): Boolean = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** How to force a restart when nothing else works; the lock never survives one. */
    fun forceRestart(context: Context): String =
        context.getString(if (isSamsung()) R.string.force_restart_samsung else R.string.force_restart)

    /** The short "if you're stuck" note under the big button and on the settings screen. */
    fun stuckNote(context: Context): String = context.getString(
        if (phoneLockIsSecure(context)) R.string.safety_note_phone else R.string.safety_note,
        forceRestart(context),
    )

    /** The About page's version: unlocking the phone first where that works, then the restart. */
    fun stuckAbout(context: Context): String {
        val restart = context.getString(R.string.about_stuck_body, forceRestart(context).replaceFirstChar { it.uppercase() })
        return if (phoneLockIsSecure(context)) context.getString(R.string.about_stuck_phone) + "\n\n" + restart else restart
    }

    /** The label a touch on the locked screen shows beside the padlock. */
    fun touchHint(context: Context, s: LockSettings): String = when (s.gesture) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.touch_hint_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.touch_hint_corners)
        GestureType.BADGE_PIN -> context.getString(R.string.touch_hint_pin)
    }

    private fun name(context: Context, s: LockSettings, g: GestureType): String = when (g) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.gesture_name_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.gesture_name_corners)
        GestureType.BADGE_PIN -> context.getString(R.string.gesture_name_pin)
    }

    /** The allowed unlocks as a short label for the home screen tile. */
    fun gestureName(context: Context, s: LockSettings): String = ordered(s).joinToString(" \u00b7 ") { name(context, s, it) }

    /** One short line for the ON banner: how to get out again. */
    fun unlockShort(context: Context, s: LockSettings): String = when (s.gesture) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.banner_on_detail_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.banner_on_detail_corners)
        GestureType.BADGE_PIN -> context.getString(R.string.banner_on_detail_pin)
    }

    fun lockHint(context: Context, s: LockSettings): String =
        if (GestureType.VOLUME_SEQUENCE in s.gestures) {
            context.getString(R.string.lock_hint_volume_sequence, patternWords(context, s))
        } else {
            context.getString(R.string.lock_hint_other)
        }
}

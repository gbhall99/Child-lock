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
        GestureType.VOLUME_CHORD -> context.getString(R.string.hint_volume_chord)
    }

    /** Every allowed unlock, the main one first, the rest each introduced with "Or". */
    fun unlockHint(context: Context, s: LockSettings): String =
        ordered(s).mapIndexed { i, g ->
            val h = hint(context, s, g)
            if (i == 0) h else context.getString(R.string.hint_or, h.replaceFirstChar { it.lowercase() })
        }.joinToString(" ")

    /**
     * The unlock that still works when the main one does not, or empty when
     * the allowed unlocks already cover it.
     *
     * Blocking swipes uses the same mode a screen reader uses, and in that
     * mode the screen sends hover, not touches, so the corner hold cannot
     * fire. The three-finger triple tap, done twice, is the fallback there.
     * Saying "two corners always works" would be untrue in the default setup.
     */
    fun fallbackHint(context: Context, s: LockSettings): String {
        // Mirrors GuardPolicy.gestureBlockFlags: explore-by-touch is only ever
        // requested on API 30+, so below that the corner hold still works.
        val exploring = s.hasVolumeGesture && s.blockGestures &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        return when {
            exploring -> context.getString(R.string.fallback_three_finger)
            GestureType.CORNER_HOLD in s.gestures -> ""
            else -> context.getString(R.string.fallback_corners)
        }
    }

    private fun name(context: Context, s: LockSettings, g: GestureType): String = when (g) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.gesture_name_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.gesture_name_corners)
        GestureType.BADGE_PIN -> context.getString(R.string.gesture_name_pin)
        GestureType.VOLUME_CHORD -> context.getString(R.string.gesture_name_chord)
    }

    /** The allowed unlocks as a short label for the home screen tile. */
    fun gestureName(context: Context, s: LockSettings): String = ordered(s).joinToString(" \u00b7 ") { name(context, s, it) }

    /** One short line for the ON banner: how to get out again. */
    fun unlockShort(context: Context, s: LockSettings): String = when (s.gesture) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.banner_on_detail_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.banner_on_detail_corners)
        GestureType.BADGE_PIN -> context.getString(R.string.banner_on_detail_pin)
        GestureType.VOLUME_CHORD -> context.getString(R.string.banner_on_detail_chord)
    }

    fun lockHint(context: Context, s: LockSettings): String =
        if (GestureType.VOLUME_SEQUENCE in s.gestures) {
            context.getString(R.string.lock_hint_volume_sequence, patternWords(context, s))
        } else {
            context.getString(R.string.lock_hint_other)
        }
}

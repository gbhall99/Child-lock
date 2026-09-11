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

    fun unlockHint(context: Context, s: LockSettings): String = when (s.gesture) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.hint_volume_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.hint_corner_hold)
        GestureType.BADGE_PIN -> context.getString(R.string.hint_badge_pin)
        GestureType.VOLUME_CHORD -> context.getString(R.string.hint_volume_chord)
    }

    /**
     * The unlock that still works when the main one does not.
     *
     * Blocking swipes uses the same mode a screen reader uses, and in that
     * mode the screen sends hover, not touches, so the corner hold cannot
     * fire. The three-finger triple tap is the fallback there. Saying "two
     * corners always works" would be untrue in the default setup.
     */
    fun fallbackHint(context: Context, s: LockSettings): String {
        // Mirrors GuardPolicy.gestureBlockFlags: explore-by-touch is only ever
        // requested on API 30+, so below that the corner hold still works.
        val exploring = s.gesture.needsGuard && s.blockGestures &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        return context.getString(if (exploring) R.string.fallback_three_finger else R.string.fallback_corners)
    }

    /** One short line for the ON banner: how to get out again. */
    fun unlockShort(context: Context, s: LockSettings): String = when (s.gesture) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.banner_on_detail_sequence, patternWords(context, s))
        GestureType.CORNER_HOLD -> context.getString(R.string.banner_on_detail_corners)
        GestureType.BADGE_PIN -> context.getString(R.string.banner_on_detail_pin)
        GestureType.VOLUME_CHORD -> context.getString(R.string.banner_on_detail_chord)
    }

    fun lockHint(context: Context, s: LockSettings): String = when (s.gesture) {
        GestureType.VOLUME_SEQUENCE -> context.getString(R.string.lock_hint_volume_sequence, patternWords(context, s))
        else -> context.getString(R.string.lock_hint_other)
    }
}

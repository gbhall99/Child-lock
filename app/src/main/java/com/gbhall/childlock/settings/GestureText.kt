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

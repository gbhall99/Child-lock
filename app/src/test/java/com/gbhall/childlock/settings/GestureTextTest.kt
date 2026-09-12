package com.gbhall.childlock.settings

import com.gbhall.childlock.TestSupport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The advertised backup unlock must be one that can actually fire in the
 * configuration the parent is in. Promising the corner hold while swipe
 * blocking is on would be a promise the platform cannot keep.
 */
@RunWith(RobolectricTestRunner::class)
class GestureTextTest {
    private val ctx get() = TestSupport.app

    @Test
    fun `blocking swipes means the three-finger tap is the backup, not corners`() {
        val s = LockSettings(gesture = GestureType.VOLUME_SEQUENCE, blockGestures = true)
        assertEquals(
            ctx.getString(com.gbhall.childlock.R.string.fallback_three_finger),
            GestureText.fallbackHint(ctx, s),
        )
    }

    @Test
    fun `without swipe blocking there is no backup that the settings do not show`() {
        assertEquals("", GestureText.fallbackHint(ctx, LockSettings(gesture = GestureType.VOLUME_SEQUENCE, blockGestures = false)))
        assertEquals("", GestureText.fallbackHint(ctx, LockSettings(gesture = GestureType.BADGE_PIN, blockGestures = true)))
        assertEquals("", GestureText.fallbackHint(ctx, LockSettings(gesture = GestureType.CORNER_HOLD)))
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [26])
    fun `below api 30 swipe blocking never engages, so no three-finger backup is promised`() {
        assertEquals("", GestureText.fallbackHint(ctx, LockSettings(gesture = GestureType.VOLUME_SEQUENCE, blockGestures = true)))
    }

    @Test
    fun `several allowed unlocks are all named, the main one first`() {
        val s = LockSettings().withGestures(setOf(GestureType.BADGE_PIN, GestureType.VOLUME_SEQUENCE))
        val hint = GestureText.unlockHint(ctx, s)
        assertEquals(true, hint.startsWith(ctx.getString(com.gbhall.childlock.R.string.hint_volume_sequence, GestureText.patternWords(ctx, s))))
        assertEquals(true, hint.contains("Or hold the lock icon"))
        assertEquals("Volume up, then down \u00b7 PIN", GestureText.gestureName(ctx, s))
        assertEquals("the lock hint follows the volume pattern whenever it is allowed", true, GestureText.lockHint(ctx, s).startsWith("Press volume"))
    }
}

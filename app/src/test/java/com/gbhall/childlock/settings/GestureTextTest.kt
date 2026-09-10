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
    fun `without swipe blocking the corner hold is the backup`() {
        val s = LockSettings(gesture = GestureType.VOLUME_SEQUENCE, blockGestures = false)
        assertEquals(
            ctx.getString(com.gbhall.childlock.R.string.fallback_corners),
            GestureText.fallbackHint(ctx, s),
        )
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [26])
    fun `below api 30 the backup named is the notification button`() {
        // Touch exploration still blocks the swipes there, but multi-finger
        // gestures do not exist, so the three-finger tap cannot fire.
        val s = LockSettings(gesture = GestureType.VOLUME_SEQUENCE, blockGestures = true)
        assertEquals(
            ctx.getString(com.gbhall.childlock.R.string.fallback_notification),
            GestureText.fallbackHint(ctx, s),
        )
    }

    @Test
    fun `a touch gesture never claims the three-finger tap`() {
        val s = LockSettings(gesture = GestureType.CORNER_HOLD, blockGestures = true)
        assertEquals(
            ctx.getString(com.gbhall.childlock.R.string.fallback_corners),
            GestureText.fallbackHint(ctx, s),
        )
    }
}

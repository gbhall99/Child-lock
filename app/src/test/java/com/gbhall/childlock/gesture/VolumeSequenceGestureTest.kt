package com.gbhall.childlock.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeSequenceGestureTest {
    private val listener = RecordingListener()

    private fun gesture(pattern: VolumePattern = VolumePattern.UP_THEN_DOWN, repeats: Int = 1) =
        VolumeSequenceGesture(pattern, repeats, listener, stepWindowMs = 900)

    private fun UnlockGesture.tap(key: HardwareKey, t: Long) {
        onKey(key, true, t)
        onKey(key, false, t + 80)
    }

    @Test
    fun `up then down completes`() {
        val g = gesture()
        g.tap(HardwareKey.VOLUME_UP, 0)
        assertFalse(listener.unlocked)
        g.tap(HardwareKey.VOLUME_DOWN, 400)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `down then up pattern needs that order`() {
        val g = gesture(VolumePattern.DOWN_THEN_UP)
        g.tap(HardwareKey.VOLUME_UP, 0)
        g.tap(HardwareKey.VOLUME_DOWN, 300)
        assertFalse(listener.unlocked)
        g.tap(HardwareKey.VOLUME_UP, 600)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `too slow does not complete`() {
        val g = gesture()
        g.tap(HardwareKey.VOLUME_UP, 0)
        g.tap(HardwareKey.VOLUME_DOWN, 1500)
        assertFalse(listener.unlocked)
        assertTrue(listener.resets >= 1)
    }

    @Test
    fun `same key twice restarts rather than completes`() {
        val g = gesture()
        g.tap(HardwareKey.VOLUME_UP, 0)
        g.tap(HardwareKey.VOLUME_UP, 300)
        assertFalse(listener.unlocked)
        g.tap(HardwareKey.VOLUME_DOWN, 600)
        assertTrue("second up started a fresh attempt", listener.unlocked)
    }

    @Test
    fun `repeat twice needs four presses`() {
        val g = gesture(repeats = 2)
        g.tap(HardwareKey.VOLUME_UP, 0)
        g.tap(HardwareKey.VOLUME_DOWN, 300)
        assertFalse(listener.unlocked)
        g.tap(HardwareKey.VOLUME_UP, 600)
        g.tap(HardwareKey.VOLUME_DOWN, 900)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `back press breaks the pattern`() {
        val g = gesture()
        g.tap(HardwareKey.VOLUME_UP, 0)
        g.tap(HardwareKey.BACK, 200)
        g.tap(HardwareKey.VOLUME_DOWN, 400)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `key releases alone never count`() {
        val g = gesture()
        g.onKey(HardwareKey.VOLUME_UP, false, 0)
        g.onKey(HardwareKey.VOLUME_DOWN, false, 100)
        assertFalse(listener.unlocked)
        assertFalse(g.wantsTicks)
    }

    @Test
    fun `progress is reported for the first press`() {
        val g = gesture()
        g.tap(HardwareKey.VOLUME_UP, 0)
        assertEquals(GestureEvent.Progress(0.5f), listener.events.last())
    }

    @Test
    fun `completes again after completing once`() {
        val g = gesture()
        g.tap(HardwareKey.VOLUME_UP, 0)
        g.tap(HardwareKey.VOLUME_DOWN, 300)
        g.tap(HardwareKey.VOLUME_UP, 2000)
        g.tap(HardwareKey.VOLUME_DOWN, 2300)
        assertEquals(2, listener.events.count { it == GestureEvent.Unlocked })
    }
}

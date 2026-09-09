package com.gbhall.childlock.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeChordGestureTest {
    private val listener = RecordingListener()
    private val gesture = VolumeChordGesture(1500, listener)

    @Test
    fun `both volume keys held unlocks`() {
        gesture.onKey(HardwareKey.VOLUME_UP, true, 0)
        gesture.onKey(HardwareKey.VOLUME_DOWN, true, 200)
        gesture.onTick(1600)
        assertFalse(listener.unlocked)
        gesture.onTick(1700)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `releasing one key resets`() {
        gesture.onKey(HardwareKey.VOLUME_UP, true, 0)
        gesture.onKey(HardwareKey.VOLUME_DOWN, true, 0)
        gesture.onKey(HardwareKey.VOLUME_UP, false, 800)
        gesture.onTick(3000)
        assertFalse(listener.unlocked)
        assertTrue(listener.resets == 1)
    }

    @Test
    fun `single key held forever never unlocks`() {
        gesture.onKey(HardwareKey.VOLUME_DOWN, true, 0)
        gesture.onTick(60_000)
        assertFalse(listener.unlocked)
        assertFalse(gesture.wantsTicks)
    }

    @Test
    fun `back press during chord breaks it`() {
        gesture.onKey(HardwareKey.VOLUME_UP, true, 0)
        gesture.onKey(HardwareKey.VOLUME_DOWN, true, 0)
        gesture.onKey(HardwareKey.BACK, true, 500)
        gesture.onTick(2000)
        assertFalse(listener.unlocked)
    }
}

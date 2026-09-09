package com.gbhall.childlock.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgePinGestureTest {
    private val listener = RecordingListener()
    private val gesture = BadgePinGesture(1000, listener).also { it.setBadgeBounds(0f, 0f, 120f, 120f) }
    private val onBadge = Pointer(0, 50f, 50f)
    private val offBadge = Pointer(0, 500f, 500f)

    private val shown: Boolean get() = GestureEvent.ShowPinPad in listener.events

    @Test
    fun `long press on badge shows pin pad`() {
        gesture.onTouch(TouchSample(TouchAction.DOWN, listOf(onBadge), 0))
        gesture.onTick(999)
        assertFalse(shown)
        gesture.onTick(1000)
        assertTrue(shown)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `press elsewhere does nothing`() {
        gesture.onTouch(TouchSample(TouchAction.DOWN, listOf(offBadge), 0))
        assertFalse(gesture.wantsTicks)
        gesture.onTick(5000)
        assertFalse(shown)
    }

    @Test
    fun `second finger resets the press`() {
        gesture.onTouch(TouchSample(TouchAction.DOWN, listOf(onBadge), 0))
        gesture.onTouch(TouchSample(TouchAction.DOWN, listOf(onBadge, Pointer(1, 60f, 60f)), 500))
        gesture.onTick(1500)
        assertFalse(shown)
    }

    @Test
    fun `sliding off the badge resets the press`() {
        gesture.onTouch(TouchSample(TouchAction.DOWN, listOf(onBadge), 0))
        gesture.onTouch(TouchSample(TouchAction.MOVE, listOf(offBadge), 600))
        gesture.onTick(1200)
        assertFalse(shown)
    }
}

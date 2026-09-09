package com.gbhall.childlock.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerHoldGestureTest {
    private val w = 1080
    private val h = 2400
    private val listener = RecordingListener()
    private val gesture = CornerHoldGesture(1500, CornerPair.TOP_LEFT_BOTTOM_RIGHT, listener).also {
        it.setBounds(w, h)
    }

    private val topLeft = Pointer(0, 40f, 60f)
    private val bottomRight = Pointer(1, 1040f, 2350f)
    private val middle = Pointer(2, 540f, 1200f)

    private fun down(vararg p: Pointer, t: Long) = gesture.onTouch(TouchSample(TouchAction.DOWN, p.toList(), t))
    private fun move(vararg p: Pointer, t: Long) = gesture.onTouch(TouchSample(TouchAction.MOVE, p.toList(), t))
    private fun up(vararg p: Pointer, t: Long) = gesture.onTouch(TouchSample(TouchAction.UP, p.toList(), t))

    @Test
    fun `both corners held for the hold time unlocks`() {
        down(topLeft, t = 0)
        down(topLeft, bottomRight, t = 100)
        assertTrue(gesture.wantsTicks)
        gesture.onTick(800)
        assertFalse(listener.unlocked)
        gesture.onTick(1600)
        assertTrue(listener.unlocked)
        assertFalse(gesture.wantsTicks)
    }

    @Test
    fun `progress is reported while holding`() {
        down(topLeft, bottomRight, t = 0)
        gesture.onTick(750)
        val progress = listener.events.filterIsInstance<GestureEvent.Progress>().last()
        assertEquals(0.5f, progress.fraction, 0.01f)
    }

    @Test
    fun `finger order does not matter`() {
        down(bottomRight, topLeft, t = 0)
        gesture.onTick(1500)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `a third finger resets the hold`() {
        down(topLeft, bottomRight, t = 0)
        gesture.onTick(1000)
        down(topLeft, bottomRight, middle, t = 1000)
        assertEquals(1, listener.resets)
        gesture.onTick(1600)
        assertFalse(listener.unlocked)
        // Lifting the extra finger starts a fresh hold rather than resuming.
        up(topLeft, bottomRight, t = 1700)
        gesture.onTick(2500)
        assertFalse(listener.unlocked)
        gesture.onTick(3200)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `finger drifting out of its corner resets`() {
        down(topLeft, bottomRight, t = 0)
        gesture.onTick(1000)
        move(Pointer(0, 600f, 60f), bottomRight, t = 1100)
        assertEquals(1, listener.resets)
        gesture.onTick(2000)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `wrong diagonal never unlocks`() {
        val topRight = Pointer(0, 1040f, 60f)
        val bottomLeft = Pointer(1, 40f, 2350f)
        down(topRight, bottomLeft, t = 0)
        gesture.onTick(5000)
        assertFalse(listener.unlocked)
        assertFalse(gesture.wantsTicks)
    }

    @Test
    fun `child mashing all over the screen never unlocks`() {
        var t = 0L
        val rnd = java.util.Random(42)
        repeat(2000) {
            val n = 1 + rnd.nextInt(5)
            val pointers = List(n) { i -> Pointer(i, rnd.nextFloat() * w, rnd.nextFloat() * h) }
            val action = TouchAction.entries[rnd.nextInt(3)]
            gesture.onTouch(TouchSample(action, pointers, t))
            t += 30
            gesture.onTick(t)
        }
        assertFalse(listener.unlocked)
    }

    @Test
    fun `single finger in a corner held forever never unlocks`() {
        down(topLeft, t = 0)
        gesture.onTick(60_000)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `cancel event aborts the hold`() {
        down(topLeft, bottomRight, t = 0)
        gesture.onTouch(TouchSample(TouchAction.CANCEL, emptyList(), 500))
        assertEquals(1, listener.resets)
        gesture.onTick(3000)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `rotation rebounds cancels an in-flight hold`() {
        down(topLeft, bottomRight, t = 0)
        gesture.setBounds(h, w)
        assertEquals(1, listener.resets)
        assertFalse(gesture.wantsTicks)
    }
}

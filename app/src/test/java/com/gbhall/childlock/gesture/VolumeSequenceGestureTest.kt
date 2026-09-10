package com.gbhall.childlock.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class VolumeSequenceGestureTest {
    private val listener = RecordingListener()

    private fun gesture(pattern: VolumePattern = VolumePattern.UP_THEN_DOWN, repeats: Int = 1) =
        VolumeSequenceGesture(pattern, repeats, listener)

    /** A deliberate press: down, then up after [holdMs]. */
    private fun UnlockGesture.press(key: HardwareKey, t: Long, holdMs: Long = 120) {
        onKey(key, true, t)
        onKey(key, false, t + holdMs)
    }

    private val HOLD = VolumeSequenceGesture.DEFAULT_FINAL_HOLD_MS + 100

    @Test
    fun `up then down, holding the last press, unlocks`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        assertFalse(listener.unlocked)
        g.press(HardwareKey.VOLUME_DOWN, 400, holdMs = HOLD)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `a quick jab on the last press does not unlock`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 400, holdMs = 60)
        assertFalse("the final press must be held", listener.unlocked)
    }

    @Test
    fun `down then up pattern needs that order`() {
        val g = gesture(VolumePattern.DOWN_THEN_UP)
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 300)
        assertFalse(listener.unlocked)
        // The wrong key opened a quiet period; wait it out, then do it properly.
        g.press(HardwareKey.VOLUME_DOWN, 3000)
        g.press(HardwareKey.VOLUME_UP, 3400, holdMs = HOLD)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `too slow between presses does not complete`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 5000, holdMs = HOLD)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `a wrong press starts a quiet period that mashing keeps open`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_DOWN, 0) // wrong first key
        // Keep pressing inside the quiet period: it never lapses.
        var t = 200L
        repeat(20) {
            g.press(HardwareKey.VOLUME_UP, t)
            g.press(HardwareKey.VOLUME_DOWN, t + 200, holdMs = HOLD)
            t += 400
        }
        assertFalse("mashing must not find a clean run", listener.unlocked)
    }

    @Test
    fun `repeat twice needs the pattern twice`() {
        val g = gesture(repeats = 2)
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 300)
        assertFalse(listener.unlocked)
        g.press(HardwareKey.VOLUME_UP, 600)
        g.press(HardwareKey.VOLUME_DOWN, 900, holdMs = HOLD)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `back press breaks the pattern`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.BACK, 200)
        g.press(HardwareKey.VOLUME_DOWN, 400, holdMs = HOLD)
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
        g.press(HardwareKey.VOLUME_UP, 0)
        assertEquals(GestureEvent.Progress(0.5f), listener.events.last())
    }

    @Test
    fun `completes again after completing once`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 300, holdMs = HOLD)
        g.press(HardwareKey.VOLUME_UP, 3000)
        g.press(HardwareKey.VOLUME_DOWN, 3300, holdMs = HOLD)
        assertEquals(2, listener.events.count { it == GestureEvent.Unlocked })
    }

    /**
     * The headline property: a child mashing the rocker must not stumble into
     * the pattern. The previous forgiving recogniser unlocked within seconds.
     */
    @Test
    fun `a child mashing the rocker for ten minutes never unlocks`() {
        val rnd = Random(7)
        repeat(200) { trial ->
            val heard = RecordingListener()
            val g = VolumeSequenceGesture(VolumePattern.UP_THEN_DOWN, 1, heard)
            var t = 0L
            while (t < 600_000) {
                val key = if (rnd.nextBoolean()) HardwareKey.VOLUME_UP else HardwareKey.VOLUME_DOWN
                val hold = 40L + rnd.nextInt(260)
                g.onKey(key, true, t)
                g.onKey(key, false, t + hold)
                if (heard.unlocked) break
                t += 150 + rnd.nextInt(550)
            }
            assertFalse("trial $trial unlocked by mashing", heard.unlocked)
        }
    }
}

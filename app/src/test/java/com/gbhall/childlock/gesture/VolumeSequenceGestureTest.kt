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

    @Test
    fun `up then down unlocks on the second press, with nothing held`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        assertFalse(listener.unlocked)
        g.press(HardwareKey.VOLUME_DOWN, 400)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `the pattern completes on the press itself, not on letting go`() {
        val g = gesture()
        g.onKey(HardwareKey.VOLUME_UP, true, 0)
        g.onKey(HardwareKey.VOLUME_DOWN, true, 300)
        assertTrue("a parent must not have to hold or release anything", listener.unlocked)
    }

    @Test
    fun `a quick jab is a press like any other`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 400, holdMs = 20)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `down then up pattern needs that order`() {
        val g = gesture(VolumePattern.DOWN_THEN_UP)
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 300)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `a fumbled press costs nothing, the very next attempt works`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_DOWN, 0) // wrong key first
        g.press(HardwareKey.VOLUME_UP, 200)
        g.press(HardwareKey.VOLUME_DOWN, 500)
        assertTrue("no penalty period may stand between a parent and the unlock", listener.unlocked)
    }

    @Test
    fun `a wrong key that is also the pattern's first press counts as a fresh start`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_UP, 200) // wrong here, but a valid opening
        g.press(HardwareKey.VOLUME_DOWN, 400)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `too slow between presses does not complete`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 5000)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `repeat twice needs the pattern twice`() {
        val g = gesture(repeats = 2)
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.VOLUME_DOWN, 300)
        assertFalse(listener.unlocked)
        g.press(HardwareKey.VOLUME_UP, 600)
        g.press(HardwareKey.VOLUME_DOWN, 900)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `three repeats are reachable`() {
        val g = gesture(repeats = 3)
        var t = 0L
        repeat(2) {
            g.press(HardwareKey.VOLUME_UP, t)
            g.press(HardwareKey.VOLUME_DOWN, t + 300)
            t += 600
        }
        assertFalse(listener.unlocked)
        g.press(HardwareKey.VOLUME_UP, t)
        g.press(HardwareKey.VOLUME_DOWN, t + 300)
        assertTrue(listener.unlocked)
    }

    @Test
    fun `back press breaks the pattern`() {
        val g = gesture()
        g.press(HardwareKey.VOLUME_UP, 0)
        g.press(HardwareKey.BACK, 200)
        g.press(HardwareKey.VOLUME_DOWN, 400)
        assertFalse(listener.unlocked)
    }

    @Test
    fun `key releases alone never count`() {
        val g = gesture()
        g.onKey(HardwareKey.VOLUME_UP, false, 0)
        g.onKey(HardwareKey.VOLUME_DOWN, false, 100)
        assertFalse(listener.unlocked)
        assertFalse("nothing is time-based any more", g.wantsTicks)
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
        g.press(HardwareKey.VOLUME_DOWN, 300)
        g.press(HardwareKey.VOLUME_UP, 3000)
        g.press(HardwareKey.VOLUME_DOWN, 3300)
        assertEquals(2, listener.events.count { it == GestureEvent.Unlocked })
    }

    /**
     * The cost of instant completion, stated rather than hidden.
     *
     * A held final press and a quiet period after a wrong key used to make this
     * unreachable by mashing, but they made the parent's own unlock slow and
     * unreliable, so they are gone. A child mashing the rocker CAN now stumble
     * into a one-length pattern. This test records how quickly, and shows that
     * asking for repeats is what buys the resistance back.
     *
     * It asserts only the ordering - longer patterns must be markedly harder to
     * hit - so it stays honest if the timings are tuned.
     */
    @Test
    fun `mashing finds a short pattern but not a long one`() {
        fun mediansSeconds(repeats: Int): Long {
            val times = mutableListOf<Long>()
            repeat(60) { trial ->
                val heard = RecordingListener()
                val g = VolumeSequenceGesture(VolumePattern.UP_THEN_DOWN, repeats, heard)
                val rnd = Random(trial.toLong())
                var t = 0L
                while (t < 600_000 && !heard.unlocked) {
                    val key = if (rnd.nextBoolean()) HardwareKey.VOLUME_UP else HardwareKey.VOLUME_DOWN
                    g.onKey(key, true, t)
                    g.onKey(key, false, t + 40L + rnd.nextInt(260))
                    t += 150 + rnd.nextInt(550)
                }
                times += if (heard.unlocked) t else 600_000L
            }
            return times.sorted()[times.size / 2] / 1000
        }
        val one = mediansSeconds(1)
        val three = mediansSeconds(3)
        assertTrue(
            "a single pattern is now within a mashing child's reach (median ${one}s) - " +
                "this is the deliberate cost of an instant unlock",
            one < 60,
        )
        assertTrue(
            "repeating the pattern must buy real resistance back (1x ${one}s vs 3x ${three}s)",
            three > one * 3,
        )
    }
}

package com.gbhall.childlock.lock

import com.gbhall.childlock.lock.ScreenWakeGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenWakeGuardTest {
    private fun decide(g: ScreenWakeGuard, now: Long, enabled: Boolean = true, keepScreenOn: Boolean = true, phoneLocked: Boolean = false) =
        g.decide(now, enabled, keepScreenOn, phoneLocked)

    @Test
    fun `a child's press is undone`() {
        assertEquals(Decision.WAKE, decide(ScreenWakeGuard(), 1_000))
    }

    @Test
    fun `a video that was playing is resumed`() {
        val g = ScreenWakeGuard()
        g.onMediaPlaying(9_000)
        assertEquals(Decision.WAKE_AND_RESUME, decide(g, 10_000))
    }

    @Test
    fun `media heard long before the press is not resumed`() {
        val g = ScreenWakeGuard()
        g.onMediaPlaying(1_000)
        assertEquals(Decision.WAKE, decide(g, 1_000 + ScreenWakeGuard.MEDIA_RECENT_MS + 1))
    }

    @Test
    fun `a phone that locked itself is left on its lock screen`() {
        assertEquals(Decision.LEAVE, decide(ScreenWakeGuard(), 1_000, phoneLocked = true))
    }

    @Test
    fun `switched off, or when the screen may sleep on its own, it never wakes`() {
        assertEquals(Decision.LEAVE, decide(ScreenWakeGuard(), 1_000, enabled = false))
        assertEquals(Decision.LEAVE, decide(ScreenWakeGuard(), 1_000, keepScreenOn = false))
    }

    @Test
    fun `pressing again straight after a wake keeps the screen off`() {
        val g = ScreenWakeGuard()
        assertEquals(Decision.WAKE, decide(g, 1_000))
        assertEquals("second press", Decision.LEAVE, decide(g, 4_000))
        assertEquals("a later press is a fresh one", Decision.WAKE, decide(g, 30_000))
    }

    @Test
    fun `presses far apart are each undone`() {
        val g = ScreenWakeGuard()
        assertEquals(Decision.WAKE, decide(g, 1_000))
        assertEquals(Decision.WAKE, decide(g, 1_000 + ScreenWakeGuard.PRESSED_AGAIN_MS))
    }

    @Test
    fun `a new lock starts fresh`() {
        val g = ScreenWakeGuard()
        g.onMediaPlaying(900)
        decide(g, 1_000)
        g.reset()
        assertEquals(Decision.WAKE, decide(g, 2_000))
    }
}

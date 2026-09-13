package com.gbhall.childlock.review

import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.lock.UnlockReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewSignalsTest {
    private val app get() = TestSupport.app
    private val day = 24L * 60 * 60 * 1000
    private var now = System.currentTimeMillis()

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        ReviewSignals.clock = { now }
    }

    @After
    fun tearDown() {
        ReviewSignals.clock = { System.currentTimeMillis() }
    }

    private fun session(minutes: Long, reason: UnlockReason = UnlockReason.PARENT) {
        ReviewSignals.onLocked(app)
        ReviewSignals.onUnlocked(app, minutes * 60_000L, reason)
    }

    @Test
    fun `only locks that ran a while and ended on purpose count, and three of them are needed`() {
        session(1)
        session(10, UnlockReason.CALL)
        assertEquals(0, ReviewSignals.goodSessions(app))
        session(2)
        session(5, UnlockReason.TIMER)
        assertEquals(2, ReviewSignals.goodSessions(app))
        assertFalse(ReviewSignals.shouldAsk(app))
        session(3)
        assertTrue(ReviewSignals.shouldAsk(app))
    }

    @Test
    fun `the fallback or a lock the app gave up on is distress, and silences the question for a fortnight`() {
        repeat(3) { session(5) }
        session(5, UnlockReason.FALLBACK)
        assertFalse(ReviewSignals.shouldAsk(app))
        now += 13 * day
        assertFalse(ReviewSignals.shouldAsk(app))
        now += 2 * day
        assertTrue(ReviewSignals.shouldAsk(app))
        session(5, UnlockReason.SYSTEM)
        assertFalse(ReviewSignals.shouldAsk(app))
    }

    @Test
    fun `a process that starts while a lock was running died locked, which is distress too`() {
        repeat(3) { session(5) }
        ReviewSignals.onLocked(app)
        assertFalse("locked right now", ReviewSignals.shouldAsk(app))
        ReviewSignals.onAppStart(app) // the forced restart
        assertFalse(ReviewSignals.shouldAsk(app))
        now += 15 * day
        assertTrue(ReviewSignals.shouldAsk(app))
    }

    @Test
    fun `asked once, never again`() {
        repeat(3) { session(5) }
        assertTrue(ReviewSignals.shouldAsk(app))
        ReviewSignals.markAsked(app)
        repeat(10) { session(5) }
        now += 100 * day
        assertFalse(ReviewSignals.shouldAsk(app))
    }
}

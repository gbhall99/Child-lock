package com.gbhall.childlock.ui

import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.lock.UnlockReason
import com.gbhall.childlock.review.FakeReview
import com.gbhall.childlock.review.ReviewSignals
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** The home screen asks for a review only after locks that plainly worked, and only once. */
@RunWith(RobolectricTestRunner::class)
class MainActivityReviewTest {
    @Before
    fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        SettingsRepository.get(TestSupport.app).setupDismissed = true
    }

    private fun open() {
        Robolectric.buildActivity(MainActivity::class.java).setup().pause().stop().destroy()
    }

    private fun session(minutes: Long, reason: UnlockReason = UnlockReason.PARENT) {
        ReviewSignals.onLocked(TestSupport.app)
        ReviewSignals.onUnlocked(TestSupport.app, minutes * 60_000L, reason)
    }

    @Test
    fun `no question until three good locks, then exactly one`() {
        open()
        assertEquals(0, FakeReview.current.requests)
        repeat(2) { session(5) }
        open()
        assertEquals(0, FakeReview.current.requests)
        session(5)
        open()
        assertEquals(1, FakeReview.current.requests)
        open()
        open()
        assertEquals("once per install", 1, FakeReview.current.requests)
    }

    @Test
    fun `a parent who needed the fallback is not asked`() {
        repeat(3) { session(5) }
        session(5, UnlockReason.FALLBACK)
        open()
        assertEquals(0, FakeReview.current.requests)
    }
}

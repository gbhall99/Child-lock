package com.gbhall.childlock.billing

import com.gbhall.childlock.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeatureGateTest {
    private val app get() = TestSupport.app
    private val day = 24L * 60 * 60 * 1000
    private var now = System.currentTimeMillis()

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        FeatureGate.clock = { now }
    }

    @After
    fun tearDown() {
        FeatureGate.clock = { System.currentTimeMillis() }
    }

    @Test
    fun `the trial starts the first time anything asks and runs for thirty days`() {
        assertEquals(FeatureGate.Access.Trial(30), FeatureGate.access(app))
        assertEquals(now, FeatureGate.trialStart(app))
        now += 29 * day + day / 2
        assertEquals("last half day still counts as a day", FeatureGate.Access.Trial(1), FeatureGate.access(app))
        assertTrue(FeatureGate.isUnlocked(app))
        now += day / 2
        assertEquals(FeatureGate.Access.Expired, FeatureGate.access(app))
        assertFalse(FeatureGate.isUnlocked(app))
    }

    @Test
    fun `days left round up so the first day says thirty and the last says one`() {
        FeatureGate.trialStart(app)
        now += 1
        assertEquals(FeatureGate.Access.Trial(30), FeatureGate.access(app))
        now += day - 1
        assertEquals(FeatureGate.Access.Trial(29), FeatureGate.access(app))
    }

    @Test
    fun `a purchase unlocks for good and a refund takes it away again`() {
        FeatureGate.trialStart(app)
        now += 45 * day
        assertEquals(FeatureGate.Access.Expired, FeatureGate.access(app))
        FeatureGate.recordPurchase(app, true)
        assertEquals(FeatureGate.Access.Purchased, FeatureGate.access(app))
        assertTrue(FeatureGate.isPurchased(app))
        FeatureGate.recordPurchase(app, false)
        assertEquals(FeatureGate.Access.Expired, FeatureGate.access(app))
    }

    @Test
    fun `a build that cannot sell is simply unlocked`() {
        FakeBilling.current.canSell = false
        FeatureGate.trialStart(app)
        now += 400 * day
        assertEquals(FeatureGate.Access.Purchased, FeatureGate.access(app))
        assertTrue(FeatureGate.isUnlocked(app))
    }

    @Test
    fun `turning the clock back does not end the trial early or extend it past its start`() {
        FeatureGate.trialStart(app)
        now -= 10 * day
        assertEquals(FeatureGate.Access.Trial(30), FeatureGate.access(app))
    }

    @Test
    fun `the debug preview shows the app as it looks after the trial, without touching the purchase`() {
        FeatureGate.setPreviewExpired(app, true)
        assertTrue(FeatureGate.isPreviewingExpired(app))
        assertEquals(FeatureGate.Access.Expired, FeatureGate.access(app))
        FeatureGate.setPreviewExpired(app, false)
        assertEquals(FeatureGate.Access.Trial(30), FeatureGate.access(app))
    }
}

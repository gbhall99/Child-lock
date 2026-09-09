package com.gbhall.childlock.lock

import com.gbhall.childlock.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LockControllerTest {
    @Before fun setUp() = TestSupport.resetLock()
    @After fun tearDown() = TestSupport.resetLock()

    @Test
    fun `starts unlocked and reports transitions to listeners`() {
        val seen = mutableListOf<LockState>()
        val l: (LockState) -> Unit = { seen += it }
        LockController.addListener(l)
        LockController.set(LockState.Locked("com.example.call", 1))
        LockController.set(LockState.Locked("com.example.call", 1)) // duplicate: no event
        LockController.unlock()
        TestSupport.idle()
        LockController.removeListener(l)
        assertEquals(listOf<LockState>(LockState.Locked("com.example.call", 1), LockState.Unlocked), seen)
        assertFalse(LockController.isLocked)
    }

    @Test
    fun `requestLock starts the overlay service with package and delay`() {
        assertTrue(LockController.requestLock(TestSupport.app, "com.example.call", 4000))
        val intent = shadowOf(TestSupport.app).nextStartedService
        assertNotNull(intent)
        assertEquals(LockOverlayService.ACTION_LOCK, intent.action)
        assertEquals(LockOverlayService::class.java.name, intent.component?.className)
        assertEquals("com.example.call", intent.getStringExtra(LockOverlayService.EXTRA_PACKAGE))
        assertEquals(4000L, intent.getLongExtra(LockOverlayService.EXTRA_DELAY_MS, -1))
    }
}

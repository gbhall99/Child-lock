package com.gbhall.childlock.tile

import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.guard.ForegroundTracker
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.lock.LockState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class LockTileServiceTest {
    @Before fun setUp() { TestSupport.resetLock(); ShadowSettings.setCanDrawOverlays(true) }
    @After fun tearDown() = TestSupport.resetLock()

    private fun tile() = Robolectric.buildService(LockTileService::class.java).create().get()

    @Test
    fun `tap while unlocked arms the lock for the last foreground app`() {
        ForegroundTracker.lastApp = "com.example.call"
        val t = tile()
        t.onStartListening()
        t.onClick()
        val intent = shadowOf(TestSupport.app).nextStartedService
        assertNotNull(intent)
        assertEquals(LockOverlayService.ACTION_LOCK, intent.action)
        assertEquals("com.example.call", intent.getStringExtra(LockOverlayService.EXTRA_PACKAGE))
        assertEquals(2500L, intent.getLongExtra(LockOverlayService.EXTRA_DELAY_MS, -1))
        t.onStopListening()
    }

    @Test
    fun `tile remembers whether it is in quick settings`() {
        val repo = com.gbhall.childlock.settings.SettingsRepository.get(TestSupport.app)
        val t = tile()
        t.onTileAdded()
        assertEquals(true, repo.tileAdded)
        t.onTileRemoved()
        assertEquals(false, repo.tileAdded)
    }

    @Test
    fun `tap while locked does nothing`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        tile().onClick()
        assertNull(shadowOf(TestSupport.app).nextStartedService)
        assertEquals(true, LockController.isLocked)
    }

    @Test
    fun `tap while arming cancels the countdown`() {
        LockController.set(LockState.Arming(9999, "com.example.call"))
        tile().onClick()
        TestSupport.idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }
}

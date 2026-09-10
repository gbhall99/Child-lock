package com.gbhall.childlock

import android.view.KeyEvent
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowSettings

/**
 * End to end: the "Practise" button locks with no delay and no protected app,
 * then the parent must be able to get out again with the volume pattern.
 */
@RunWith(RobolectricTestRunner::class)
class PractiseUnlockTest {
    private lateinit var guard: GuardAccessibilityService
    private var overlay: ServiceController<LockOverlayService>? = null

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.VOLUME_SEQUENCE) }
        guard = Robolectric.buildService(GuardAccessibilityService::class.java).create().get()
        guard.onServiceConnected()
    }

    @After
    fun tearDown() {
        overlay?.destroy()
        guard.onUnbind(null)
        TestSupport.resetLock()
    }

    /** Exactly what the Practise button does, via the real request path. */
    private fun practise() {
        LockController.requestLock(TestSupport.app, null, 0, rehearsal = true)
        val intent = org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService
        overlay = Robolectric.buildService(LockOverlayService::class.java, intent).create().startCommand(0, 1)
        TestSupport.idle()
    }

    private fun press(code: Int, t: Long, holdMs: Long) {
        guard.onKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        guard.onKeyEvent(KeyEvent(t, t + holdMs, KeyEvent.ACTION_UP, code, 0))
    }

    @Test
    fun `practise locks and the volume pattern unlocks it`() {
        practise()
        assertTrue("practise should lock", LockController.isLocked)
        press(KeyEvent.KEYCODE_VOLUME_UP, 1000, 120)
        press(KeyEvent.KEYCODE_VOLUME_DOWN, 1400, 120)
        TestSupport.idle()
        assertEquals("the parent must be able to get out", LockState.Unlocked, LockController.state)
    }

    /**
     * Some devices never deliver the release of a key the service consumed.
     * The unlock completes on the press itself, so that cannot strand anyone,
     * and the rehearsal ends on its own regardless.
     */
    @Test
    fun `a practice lock always ends by itself`() {
        practise()
        assertTrue(LockController.isLocked)
        TestSupport.idle(LockOverlayService.REHEARSAL_MS + 2000)
        assertEquals(
            "trying it out must never be able to strand anyone",
            LockState.Unlocked,
            LockController.state,
        )
    }

    @Test
    fun `holding the last press unlocks even if the release never arrives`() {
        practise()
        assertTrue(LockController.isLocked)
        guard.onKeyEvent(KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0))
        guard.onKeyEvent(KeyEvent(1000, 1120, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0))
        guard.onKeyEvent(KeyEvent(1400, 1400, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0))
        // No ACTION_UP for the final press at all; just time passing.
        TestSupport.idle(1500)
        assertEquals("a missing release must not trap the parent", LockState.Unlocked, LockController.state)
    }
}

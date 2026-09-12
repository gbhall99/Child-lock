package com.gbhall.childlock.guard

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.view.KeyEvent
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The overlay swallows every touch it is given, but Android handles two swipes
 * before any window sees them: down from the top opens the notification shade
 * and up from the bottom goes home. Those are the accessibility guard's job:
 * close the shade at once, and bring the protected app back.
 */
@RunWith(RobolectricTestRunner::class)
class GuardSystemSwipeTest {
    private lateinit var service: GuardAccessibilityService

    @Before
    fun setUp() {
        service = GuardRig.start()
    }

    @After
    fun tearDown() {
        service.onUnbind(null)
        TestSupport.resetLock()
    }

    private fun lock() {
        LockController.set(LockState.Locked(GuardRig.APP, 0))
        idle()
    }

    /** The child drags the shade down: SystemUI's tall window appears and the windows change. */
    private fun pullShadeDown() {
        GuardRig.setWindows(service, GuardRig.shade(service))
        service.onAccessibilityEvent(GuardRig.windowsChanged())
    }

    @Test
    fun `pulling the shade down while locked closes it at once`() {
        lock()
        pullShadeDown()
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE), GuardRig.globalActions(service))
        assertTrue("still locked", LockController.isLocked)
    }

    @Test
    fun `the shade is closed every time, for as long as the lock lasts`() {
        lock()
        repeat(5) {
            pullShadeDown()
            idle(GuardPolicy.SHADE_DISMISS_DEBOUNCE_MS + 100)
        }
        assertEquals(5, GuardRig.globalActions(service).size)
    }

    @Test
    fun `a burst of window events for one pull closes the shade once`() {
        lock()
        repeat(4) { pullShadeDown() }
        assertEquals("debounced", 1, GuardRig.globalActions(service).size)
    }

    @Test
    fun `the shade is left alone while unlocked`() {
        pullShadeDown()
        assertEquals(emptyList<Int>(), GuardRig.globalActions(service))
    }

    @Test
    fun `the shade is left alone when the parent switched that off`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(blockShade = false) }
        idle()
        lock()
        pullShadeDown()
        assertEquals(emptyList<Int>(), GuardRig.globalActions(service))
    }

    @Test
    fun `the device lock screen keeps its own shade`() {
        lock()
        val km = shadowOf(TestSupport.app.getSystemService(KeyguardManager::class.java))
        km.setKeyguardLocked(true)
        try {
            pullShadeDown()
            assertEquals(emptyList<Int>(), GuardRig.globalActions(service))
        } finally {
            km.setKeyguardLocked(false)
        }
    }

    @Test
    fun `the status bar and app windows are never mistaken for the shade`() {
        lock()
        val s = GuardRig.screen(service)
        GuardRig.setWindows(
            service,
            GuardRig.statusBar(service),
            GuardRig.window(android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION, s, GuardRig.APP),
        )
        service.onAccessibilityEvent(GuardRig.windowsChanged())
        assertEquals(emptyList<Int>(), GuardRig.globalActions(service))
    }

    @Test
    fun `a tall window from another package is not the shade either`() {
        lock()
        val s = GuardRig.screen(service)
        GuardRig.setWindows(
            service,
            GuardRig.window(android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM, s, "com.other.overlay"),
        )
        service.onAccessibilityEvent(GuardRig.windowsChanged())
        assertEquals(emptyList<Int>(), GuardRig.globalActions(service))
    }

    @Test
    @Config(sdk = [26])
    fun `on older Android the shade is closed with back`() {
        lock()
        pullShadeDown()
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_BACK), GuardRig.globalActions(service))
    }

    @Test
    fun `swiping home while locked leaves the lock and the protected app in place`() {
        lock()
        service.onAccessibilityEvent(GuardRig.windowEvent(GuardRig.HOME))
        idle()
        assertTrue(LockController.isLocked)
        assertEquals(GuardRig.APP, (LockController.state as LockState.Locked).protectedPackage)
        // Back keeps being swallowed and the parent's unlock pattern still works.
        assertTrue(service.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        service.onKeyEvent(KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0))
        service.onKeyEvent(KeyEvent(1000, 1100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0))
        service.onKeyEvent(KeyEvent(1300, 1300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0))
        service.onKeyEvent(KeyEvent(1300, 1400, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0))
        idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }
}

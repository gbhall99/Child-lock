package com.gbhall.childlock.guard

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GuardAccessibilityServiceTest {
    private lateinit var service: GuardAccessibilityService

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        ForegroundTracker.lastApp = null
        val pm = shadowOf(TestSupport.app.packageManager)
        val call = ComponentName("com.example.call", "com.example.call.Main")
        pm.addActivityIfNotPresent(call)
        pm.addIntentFilterForActivity(call, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        service = Robolectric.buildService(GuardAccessibilityService::class.java).create().get()
        service.onServiceConnected()
    }

    @After
    fun tearDown() {
        service.onUnbind(null)
        TestSupport.resetLock()
    }

    private fun windowEvent(pkg: String) = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
        packageName = pkg
    }

    private fun key(code: Int, down: Boolean = true) =
        KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)

    @Test
    fun `remembers the last launchable foreground app but never itself or non-apps`() {
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        assertEquals("com.example.call", ForegroundTracker.lastApp)
        service.onAccessibilityEvent(windowEvent(TestSupport.app.packageName))
        assertEquals("com.example.call", ForegroundTracker.lastApp)
        service.onAccessibilityEvent(windowEvent("com.android.systemui"))
        assertEquals("com.example.call", ForegroundTracker.lastApp)
    }

    @Test
    fun `keys pass through while unlocked`() {
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP)))
    }

    @Test
    fun `back and volume are swallowed while locked, other keys are not`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertTrue(service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
        assertTrue(service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_CAMERA)))
    }

    @Test
    fun `block keys off lets keys through unless the volume chord needs them`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(blockKeys = false) }
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP)))

        LockController.unlock()
        TestSupport.idle()
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.VOLUME_CHORD) }
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertTrue(service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP)))
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
    }

    @Test
    fun `volume chord held through the service unlocks`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.VOLUME_CHORD, holdMs = 1000) }
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP))
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_DOWN))
        TestSupport.idle(600)
        assertTrue(LockController.isLocked)
        TestSupport.idle(500)
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `releasing a chord key resets the hold`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.VOLUME_CHORD, holdMs = 1000) }
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP))
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_DOWN))
        TestSupport.idle(600)
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP, down = false))
        TestSupport.idle(2000)
        assertTrue(LockController.isLocked)
    }

    @Test
    fun `window events while locked do not crash without window access`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.android.launcher"))
        service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
    }

    @Test
    fun `isEnabled reads the secure setting`() {
        assertFalse(GuardAccessibilityService.isEnabled(TestSupport.app))
        val flat = ComponentName(TestSupport.app, GuardAccessibilityService::class.java).flattenToString()
        android.provider.Settings.Secure.putString(
            TestSupport.app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other/.Svc:$flat",
        )
        assertTrue(GuardAccessibilityService.isEnabled(TestSupport.app))
    }
}

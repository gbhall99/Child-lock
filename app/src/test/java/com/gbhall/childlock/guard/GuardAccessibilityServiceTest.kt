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
        val home = ComponentName("com.android.launcher", "com.android.launcher.Home")
        pm.addActivityIfNotPresent(home)
        pm.addIntentFilterForActivity(home, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) })
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
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.CORNER_HOLD) }
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertTrue(service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
        assertTrue(service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_CAMERA)))
    }

    @Test
    fun `block keys off lets keys through unless the volume chord needs them`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(blockKeys = false, gesture = GestureType.CORNER_HOLD) }
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

    private fun tap(code: Int, t: Long, holdMs: Long = 120) {
        service.onKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        service.onKeyEvent(KeyEvent(t, t + holdMs, KeyEvent.ACTION_UP, code, 0))
    }

    /** Nothing has to be held any more; an ordinary press length. */
    private val HOLD = 120L

    @Test
    fun `volume pattern while unlocked arms the lock immediately and is not consumed`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        ForegroundTracker.lastApp = "com.example.call"
        assertFalse(service.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0)))
        assertFalse(service.onKeyEvent(KeyEvent(0, 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0)))
        assertFalse(service.onKeyEvent(KeyEvent(300, 300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
        assertFalse(service.onKeyEvent(KeyEvent(300, 300 + HOLD, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
        TestSupport.idle()
        val intent = org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService
        org.junit.Assert.assertNotNull("lock service should start", intent)
        assertEquals(com.gbhall.childlock.lock.LockOverlayService.ACTION_LOCK, intent.action)
        assertEquals(0L, intent.getLongExtra(com.gbhall.childlock.lock.LockOverlayService.EXTRA_DELAY_MS, -1))
        assertEquals("com.example.call", intent.getStringExtra(com.gbhall.childlock.lock.LockOverlayService.EXTRA_PACKAGE))
    }

    @Test
    fun `volume pattern while locked unlocks`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        tap(KeyEvent.KEYCODE_VOLUME_UP, 1000)
        assertTrue(LockController.isLocked)
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 1300, HOLD)
        TestSupport.idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `wrong order or slow presses do not unlock`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 1000)
        tap(KeyEvent.KEYCODE_VOLUME_UP, 1300, HOLD)
        TestSupport.idle()
        assertTrue(LockController.isLocked)
        tap(KeyEvent.KEYCODE_VOLUME_UP, 5000)
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 7000)
        TestSupport.idle()
        assertTrue(LockController.isLocked)
    }

    @Test
    fun `held key auto-repeat counts as one press`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        service.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0))
        service.onKeyEvent(KeyEvent(0, 200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 1))
        service.onKeyEvent(KeyEvent(0, 400, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 2))
        service.onKeyEvent(KeyEvent(0, 500, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0))
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 700, HOLD)
        TestSupport.idle()
        assertEquals("up (held), down should still complete", LockState.Unlocked, LockController.state)
    }

    @Test
    fun `changing the pattern in settings takes effect without reconnecting`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(volumePattern = com.gbhall.childlock.gesture.VolumePattern.DOWN_THEN_UP) }
        TestSupport.idle()
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 1000)
        tap(KeyEvent.KEYCODE_VOLUME_UP, 1300, HOLD)
        TestSupport.idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `presses may be swallowed but releases always pass through`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        // Unlocked: everything passes.
        assertFalse(service.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0)))
        assertFalse(service.onKeyEvent(KeyEvent(0, 60, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0)))
        assertFalse(service.onKeyEvent(KeyEvent(300, 300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
        TestSupport.idle() // the posted lock request runs (and starts the service)
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertFalse("release after locking still passes", service.onKeyEvent(KeyEvent(300, 360, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0)))
        // Locked: the press is swallowed, its repeats too, but never the release.
        assertTrue(service.onKeyEvent(KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0)))
        assertTrue(service.onKeyEvent(KeyEvent(1000, 1200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 1)))
        assertFalse(service.onKeyEvent(KeyEvent(1000, 1300, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0)))
    }

    @Test
    fun `key callback returns before the lock state listeners run`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        var heard = false
        val l: (LockState) -> Unit = { if (it == LockState.Unlocked) heard = true }
        LockController.addListener(l)
        tap(KeyEvent.KEYCODE_VOLUME_UP, 2000)
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 2300, HOLD)
        assertFalse("listeners must not run inside onKeyEvent", heard)
        TestSupport.idle()
        assertTrue(heard)
        LockController.removeListener(l)
    }

    @Test
    fun `gesture blocking flags are requested only while locked with a volume gesture`() {
        val block = com.gbhall.childlock.guard.GuardPolicy.FLAG_TOUCH_EXPLORATION or com.gbhall.childlock.guard.GuardPolicy.FLAG_MULTI_FINGER
        // Off by default now, so a parent has to have opted in for any of this to apply.
        SettingsRepository.get(TestSupport.app).update { it.copy(blockGestures = true) }
        TestSupport.idle()
        assertEquals(0, service.requestedFlags and block)
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertEquals(block, service.requestedFlags and block)
        LockController.unlock()
        TestSupport.idle()
        assertEquals(0, service.requestedFlags and block)

        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.CORNER_HOLD) }
        TestSupport.idle()
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertEquals("touch gestures need real touches", 0, service.requestedFlags and block)
    }

    @Test
    fun `three-finger triple tap done twice unlocks as the touch fallback`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        val e = android.accessibilityservice.AccessibilityGestureEvent(
            android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, 0, emptyList(),
        )
        assertTrue(service.onGesture(e))
        TestSupport.idle()
        assertTrue("one is within a small hand's reach", LockController.isLocked)
        TestSupport.idle(1000)
        assertTrue(service.onGesture(e))
        TestSupport.idle()
        assertEquals("two in a row is the parent", LockState.Unlocked, LockController.state)
        assertFalse("nothing to unlock", service.onGesture(e))
    }

    @Test
    fun `a three-finger triple tap left too long does not pair with the next`() {
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        val e = android.accessibilityservice.AccessibilityGestureEvent(
            android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, 0, emptyList(),
        )
        service.onGesture(e)
        TestSupport.idle(GuardAccessibilityService.TRIPLE_TAP_REPEAT_MS + 500)
        service.onGesture(e)
        TestSupport.idle()
        assertTrue("the first had gone stale", LockController.isLocked)
        TestSupport.idle(500)
        service.onGesture(e)
        TestSupport.idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `chosen app coming to the front arms the lock with the auto-lock delay`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.OPEN), autoLockDelaySec = 7) }
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle()
        val intent = org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService
        org.junit.Assert.assertNotNull(intent)
        assertEquals(7000L, intent.getLongExtra(com.gbhall.childlock.lock.LockOverlayService.EXTRA_DELAY_MS, -1))
        assertEquals("com.example.call", intent.getStringExtra(com.gbhall.childlock.lock.LockOverlayService.EXTRA_PACKAGE))
    }

    @Test
    fun `other apps and unlocking then returning do not arm`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.OPEN)) }
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.android.launcher"))
        TestSupport.idle()
        assertEquals(null, org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)

        // The chosen app comes to the front: it arms, and the lock engages.
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle()
        org.junit.Assert.assertNotNull(org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        // Parent unlocks while still in the app: staying there must not re-arm.
        LockController.unlock()
        TestSupport.idle(3000)
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle(2000)
        assertEquals(null, org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        // After another app has been in front, the chosen app arms again.
        service.onAccessibilityEvent(windowEvent("com.android.launcher"))
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle()
        org.junit.Assert.assertNotNull(org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
    }

    @Test
    fun `call trigger waits for call mode, then arms`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.CALL)) }
        TestSupport.idle()
        val audio = TestSupport.app.getSystemService(android.media.AudioManager::class.java)
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle(3000)
        assertEquals("not in a call yet", null, org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        audio.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        TestSupport.idle(1500)
        org.junit.Assert.assertNotNull("call connected: arm", org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        audio.mode = android.media.AudioManager.MODE_NORMAL
    }

    @Test
    fun `video call trigger needs the camera as well as call audio`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.VIDEO_CALL)) }
        TestSupport.idle()
        val audio = TestSupport.app.getSystemService(android.media.AudioManager::class.java)
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        audio.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        TestSupport.idle(2500)
        assertEquals("voice call: no arm", null, org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        service.camerasInUse.add("1")
        TestSupport.idle(1500)
        org.junit.Assert.assertNotNull("camera on: video call, arm", org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        audio.mode = android.media.AudioManager.MODE_NORMAL
        service.camerasInUse.clear()
    }

    @Test
    fun `leaving the app stops waiting for its trigger`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.CALL)) }
        TestSupport.idle()
        val audio = TestSupport.app.getSystemService(android.media.AudioManager::class.java)
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        service.onAccessibilityEvent(windowEvent("com.android.launcher"))
        audio.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        TestSupport.idle(3000)
        assertEquals(null, org.robolectric.Shadows.shadowOf(TestSupport.app).nextStartedService)
        audio.mode = android.media.AudioManager.MODE_NORMAL
    }

    @Test
    fun `switching away during the auto-lock countdown cancels it`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.OPEN)) }
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle()
        LockController.set(LockState.Arming(9999, "com.example.call"))
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.android.launcher"))
        TestSupport.idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `volume pattern during a countdown cancels it`() {
        LockController.set(LockState.Arming(9999, "com.example.call"))
        TestSupport.idle()
        tap(KeyEvent.KEYCODE_VOLUME_UP, 1000)
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 1300, HOLD)
        TestSupport.idle()
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `a permission prompt during the countdown does not cancel it`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.example.call" to com.gbhall.childlock.settings.AutoLockTrigger.OPEN)) }
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.example.call"))
        TestSupport.idle()
        LockController.set(LockState.Arming(9999, "com.example.call"))
        TestSupport.idle()
        service.onAccessibilityEvent(windowEvent("com.google.android.permissioncontroller"))
        TestSupport.idle()
        assertTrue(LockController.state is LockState.Arming)
    }

    @Test
    fun `touch-exploration flags are cleared when the helper is unbound`() {
        val block = GuardPolicy.FLAG_TOUCH_EXPLORATION or GuardPolicy.FLAG_MULTI_FINGER
        SettingsRepository.get(TestSupport.app).update { it.copy(blockGestures = true) }
        TestSupport.idle()
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertEquals(block, service.requestedFlags and block)
        service.onUnbind(null)
        assertEquals("the phone must not be left exploring by touch", 0, service.requestedFlags and block)
    }

    @Test
    fun `the device lock screen keeps its own keys`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.CORNER_HOLD) }
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertTrue(service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
        org.robolectric.Shadows.shadowOf(
            TestSupport.app.getSystemService(android.app.KeyguardManager::class.java),
        ).setKeyguardLocked(true)
        assertFalse("back belongs to the lock screen", service.onKeyEvent(key(KeyEvent.KEYCODE_BACK)))
        assertFalse(service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP)))
        org.robolectric.Shadows.shadowOf(
            TestSupport.app.getSystemService(android.app.KeyguardManager::class.java),
        ).setKeyguardLocked(false)
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

    @Test
    fun `key filtering stays requested through a whole lock and unlock`() {
        val filter = GuardPolicy.FLAG_REQUEST_FILTER_KEY_EVENTS
        SettingsRepository.get(TestSupport.app).update { it.copy(blockGestures = true) }
        TestSupport.idle()
        assertEquals(filter, service.requestedFlags and filter)
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        assertEquals(filter, service.requestedFlags and filter)
        // Asking for touch exploration turns it on device-wide, exactly as the real
        // system does. We must not then read that back as somebody else's screen
        // reader and stand our own request down, flag on, flag off, for the whole lock.
        val block = GuardPolicy.FLAG_TOUCH_EXPLORATION or GuardPolicy.FLAG_MULTI_FINGER
        val held = service.requestedFlags
        assertEquals(block, held and block)
        val am = TestSupport.app.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        shadowOf(am).setTouchExplorationEnabled(true)
        assertFalse("our own touch exploration is not another tool's", service.otherScreenReaderActive())
        repeat(3) {
            service.onAccessibilityEvent(windowEvent("com.example.call"))
            TestSupport.idle()
            assertEquals("the flags must not flip-flop while locked", held, service.requestedFlags)
        }
        LockController.unlock()
        TestSupport.idle()
        assertEquals(filter, service.requestedFlags and filter)
        assertEquals("and it is handed back on unlock", 0, service.requestedFlags and block)
    }

    @Test
    fun `a real screen reader keeps explore-by-touch to itself`() {
        val am = TestSupport.app.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        shadowOf(am).setTouchExplorationEnabled(true) // TalkBack, before we ask for anything
        SettingsRepository.get(TestSupport.app).update { it.copy(blockGestures = true) }
        TestSupport.idle()
        assertTrue(service.otherScreenReaderActive())
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        val block = GuardPolicy.FLAG_TOUCH_EXPLORATION or GuardPolicy.FLAG_MULTI_FINGER
        assertEquals("competing with TalkBack breaks both", 0, service.requestedFlags and block)
    }

    @Test
    fun `volume pattern and volume chord can both be allowed`() {
        SettingsRepository.get(TestSupport.app).update {
            it.copy(holdMs = 1000).withGestures(setOf(GestureType.VOLUME_SEQUENCE, GestureType.VOLUME_CHORD))
        }
        TestSupport.idle()
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP))
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_DOWN))
        TestSupport.idle(1100)
        assertEquals("the chord unlocked", LockState.Unlocked, LockController.state)
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_UP, down = false))
        service.onKeyEvent(key(KeyEvent.KEYCODE_VOLUME_DOWN, down = false))
        LockController.set(LockState.Locked("com.example.call", 0))
        TestSupport.idle()
        tap(KeyEvent.KEYCODE_VOLUME_UP, 5000)
        tap(KeyEvent.KEYCODE_VOLUME_DOWN, 5300, HOLD)
        TestSupport.idle()
        assertEquals("and so does the pattern", LockState.Unlocked, LockController.state)
    }
}

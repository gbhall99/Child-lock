package com.gbhall.childlock.guard

import android.content.Intent
import android.media.AudioManager
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

/**
 * Auto-lock driven end to end through the guard: the rules a parent saves in
 * settings, the signals Android exposes (audio mode, playing media, the
 * status bar, cameras) and the once-a-second watch that turns them into a
 * lock request.
 */
@RunWith(RobolectricTestRunner::class)
class GuardAutoLockTest {
    private lateinit var service: GuardAccessibilityService
    private lateinit var audio: AudioManager

    @Before
    fun setUp() {
        service = GuardRig.start()
        ShadowSettings.setCanDrawOverlays(true)
        audio = TestSupport.app.getSystemService(AudioManager::class.java)
    }

    @After
    fun tearDown() {
        audio.mode = AudioManager.MODE_NORMAL
        shadowOf(audio).setIsMusicActive(false)
        service.camerasInUse.clear()
        service.onUnbind(null)
        TestSupport.resetLock()
    }

    private fun rule(trigger: AutoLockTrigger, delaySec: Int = 15) {
        SettingsRepository.get(TestSupport.app).update {
            it.copy(autoLockRules = mapOf(GuardRig.APP to trigger), autoLockDelaySec = delaySec)
        }
        idle()
    }

    private fun open(pkg: String) {
        service.onAccessibilityEvent(GuardRig.windowEvent(pkg))
        idle()
    }

    private fun assertArmed(message: String) {
        val intent = GuardRig.nextLockRequest()
        assertNotNull(message, intent)
        assertEquals(LockOverlayService.ACTION_LOCK, intent!!.action)
        assertEquals(GuardRig.APP, intent.getStringExtra(LockOverlayService.EXTRA_PACKAGE))
    }

    private fun assertNotArmed(message: String) = assertNull(message, GuardRig.nextLockRequest())

    @Test
    fun `voice call trigger arms on call audio with no camera in use`() {
        rule(AutoLockTrigger.VOICE_CALL)
        open(GuardRig.APP)
        idle(2000)
        assertNotArmed("no call yet")
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        idle(1500)
        assertArmed("voice call connected")
    }

    @Test
    fun `voice call trigger ignores a video call`() {
        rule(AutoLockTrigger.VOICE_CALL)
        open(GuardRig.APP)
        service.camerasInUse.add("0")
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        idle(3000)
        assertNotArmed("camera on: that is a video call")
    }

    @Test
    fun `playback trigger arms as soon as the app plays something`() {
        rule(AutoLockTrigger.PLAYBACK)
        open(GuardRig.APP)
        idle(2000)
        assertNotArmed("silent")
        shadowOf(audio).setIsMusicActive(true)
        idle(1500)
        assertArmed("playing")
    }

    @Test
    fun `full screen playback trigger waits for the status bar to hide`() {
        rule(AutoLockTrigger.FULLSCREEN_PLAYBACK)
        GuardRig.setWindows(service, GuardRig.statusBar(service))
        open(GuardRig.APP)
        shadowOf(audio).setIsMusicActive(true)
        idle(3000)
        assertNotArmed("playing, but the status bar is showing")
        GuardRig.setWindows(service)
        idle(1500)
        assertArmed("full screen now")
    }

    @Test
    fun `the lock request carries the delay chosen in settings`() {
        rule(AutoLockTrigger.OPEN, delaySec = 9)
        open(GuardRig.APP)
        val intent = GuardRig.nextLockRequest()
        assertNotNull(intent)
        assertEquals(9000L, intent!!.getLongExtra(LockOverlayService.EXTRA_DELAY_MS, -1))
    }

    @Test
    fun `a rule saved while the helper runs applies at the next app switch`() {
        open(GuardRig.APP)
        idle(2000)
        assertNotArmed("no rule yet")
        rule(AutoLockTrigger.OPEN)
        open(GuardRig.HOME)
        open(GuardRig.APP)
        assertArmed("rule picked up without reconnecting")
    }

    @Test
    fun `after the parent unlocks, the same moment happening again re-locks`() {
        rule(AutoLockTrigger.FULLSCREEN_PLAYBACK)
        open(GuardRig.APP)
        shadowOf(audio).setIsMusicActive(true)
        idle(1500)
        assertArmed("full screen video")
        LockController.set(LockState.Locked(GuardRig.APP, 0))
        idle()
        LockController.unlock()
        idle()
        // The video is still playing full screen: that is the moment the
        // parent unlocked from, and it must not lock again while it lasts.
        idle(40_000)
        assertNotArmed("the same moment continuing is not a new one")
        // It ends for good and, later, starts again.
        shadowOf(audio).setIsMusicActive(false)
        idle((AutoLockEngine.FALL_POLLS + 2) * 1000L)
        assertNotArmed("nothing playing")
        shadowOf(audio).setIsMusicActive(true)
        idle((AutoLockEngine.RISE_POLLS + 1) * 1000L)
        assertArmed("full screen again: lock again by itself")
    }

    @Test
    fun `re-lock stays off when the parent switched it off`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(relockSameApp = false) }
        rule(AutoLockTrigger.FULLSCREEN_PLAYBACK)
        open(GuardRig.APP)
        shadowOf(audio).setIsMusicActive(true)
        idle(1500)
        assertArmed("first time")
        LockController.set(LockState.Locked(GuardRig.APP, 0))
        idle()
        LockController.unlock()
        idle()
        shadowOf(audio).setIsMusicActive(false)
        idle(40_000)
        shadowOf(audio).setIsMusicActive(true)
        idle(5000)
        assertNotArmed("re-lock is off")
    }

    @Test
    fun `a chosen app coming back to the front while locked sends no second lock request`() {
        rule(AutoLockTrigger.CALL)
        open(GuardRig.APP)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        idle(1500)
        assertArmed("call connected")
        LockController.set(LockState.Locked(GuardRig.APP, 0))
        idle()
        // The child reaches the home screen and the guard brings the app back.
        open(GuardRig.HOME)
        open(GuardRig.APP)
        idle(3000)
        assertNotArmed("already locked")
        assertEquals(AutoLockEngine.State.LOCKED, service.engine.state)
        // And the unlock that follows still counts as unlocking from this app's moment.
        LockController.unlock()
        idle()
        assertEquals(AutoLockEngine.State.HOT, service.engine.state)
    }

    @Test
    fun `screen off pauses the watch and screen on resumes it`() {
        rule(AutoLockTrigger.CALL)
        open(GuardRig.APP)
        TestSupport.app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle()
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        idle(3000)
        assertNotArmed("screen is off")
        TestSupport.app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        idle(1500)
        assertArmed("screen back on, call still connected")
    }

    @Test
    fun `without overlay permission a rule cannot arm and the engine does not get stuck`() {
        ShadowSettings.setCanDrawOverlays(false)
        rule(AutoLockTrigger.OPEN)
        open(GuardRig.APP)
        assertNotArmed("no permission")
        assertEquals(LockState.Unlocked, LockController.state)
        ShadowSettings.setCanDrawOverlays(true)
        open(GuardRig.HOME)
        open(GuardRig.APP)
        assertArmed("permission granted")
    }
}

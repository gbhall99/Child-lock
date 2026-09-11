package com.gbhall.childlock.guard

import com.gbhall.childlock.gesture.HardwareKey
import com.gbhall.childlock.settings.AutoLockTrigger as T
import com.gbhall.childlock.guard.GuardPolicy.RelaunchDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardPolicyTest {
    private val screen = 2400

    @Test
    fun `open shade is a tall system-ui system window`() {
        assertTrue(GuardPolicy.isShadeWindow(true, 2400, screen, GuardPolicy.SYSTEM_UI))
        assertTrue(GuardPolicy.isShadeWindow(true, 1000, screen, GuardPolicy.SYSTEM_UI))
    }

    @Test
    fun `status bar, other packages and app windows are not the shade`() {
        assertFalse("status bar is thin", GuardPolicy.isShadeWindow(true, 90, screen, GuardPolicy.SYSTEM_UI))
        assertFalse("not system ui", GuardPolicy.isShadeWindow(true, 2400, screen, "com.example.call"))
        assertFalse("app-type window", GuardPolicy.isShadeWindow(false, 2400, screen, GuardPolicy.SYSTEM_UI))
        assertFalse("unknown package", GuardPolicy.isShadeWindow(true, 2400, screen, null))
        assertFalse("bad screen size", GuardPolicy.isShadeWindow(true, 2400, 0, GuardPolicy.SYSTEM_UI))
    }

    private fun decide(
        protectedPkg: String? = "com.example.call",
        foreground: String? = "com.android.launcher",
        dialer: String? = "com.google.android.dialer",
        keyguard: Boolean = false,
        now: Long = 10_000,
        last: Long = 0,
    ) = GuardPolicy.relaunchDecision(protectedPkg, foreground, "com.gbhall.childlock", dialer, keyguard, now, last)

    @Test
    fun `child on the home screen triggers a relaunch`() {
        assertEquals(RelaunchDecision.Relaunch, decide())
    }

    @Test
    fun `no relaunch when the call is already in front`() {
        assertTrue(decide(foreground = "com.example.call") is RelaunchDecision.Skip)
    }

    @Test
    fun `no relaunch when nothing is protected or foreground unknown`() {
        assertTrue(decide(protectedPkg = null) is RelaunchDecision.Skip)
        assertTrue(decide(foreground = null) is RelaunchDecision.Skip)
    }

    @Test
    fun `never fights a phone call, the keyguard, system ui or child lock itself`() {
        assertTrue(decide(foreground = "com.google.android.dialer") is RelaunchDecision.Skip)
        assertTrue(decide(foreground = "com.samsung.android.incallui", dialer = null) is RelaunchDecision.Skip)
        assertTrue(decide(keyguard = true) is RelaunchDecision.Skip)
        assertTrue(decide(foreground = GuardPolicy.SYSTEM_UI) is RelaunchDecision.Skip)
        assertTrue(decide(foreground = "com.gbhall.childlock") is RelaunchDecision.Skip)
        assertTrue("permission prompt", decide(foreground = "com.google.android.permissioncontroller") is RelaunchDecision.Skip)
        assertTrue("system dialog", decide(foreground = "android") is RelaunchDecision.Skip)
    }

    @Test
    fun `relaunches are debounced`() {
        assertTrue(decide(now = 1000, last = 0) is RelaunchDecision.Skip)
        assertEquals(RelaunchDecision.Relaunch, decide(now = 1600, last = 0))
    }

    @Test
    fun `gesture block flags need lock, setting and a volume gesture`() {
        val both = GuardPolicy.FLAG_TOUCH_EXPLORATION or GuardPolicy.FLAG_MULTI_FINGER
        assertEquals(both, GuardPolicy.gestureBlockFlags(true, true, true, 35))
        assertEquals(0, GuardPolicy.gestureBlockFlags(false, true, true, 35))
        assertEquals(0, GuardPolicy.gestureBlockFlags(true, false, true, 35))
        assertEquals("touch gestures need real touches", 0, GuardPolicy.gestureBlockFlags(true, true, false, 35))
    }

    @Test
    fun `explore-by-touch is never requested below api 30`() {
        // Without multi-finger gestures there is no touch way out at all.
        assertEquals(0, GuardPolicy.gestureBlockFlags(true, true, true, 29))
    }

    private fun auto(fg: String, locked: Boolean = false, arming: Boolean = false, armed: String? = null, suppressed: String? = null) =
        GuardPolicy.autoLockDecision(fg, setOf("com.video", "com.game"), locked, arming, armed, suppressed)

    @Test
    fun `auto-lock arms for chosen apps only, while unlocked`() {
        assertEquals(GuardPolicy.AutoLockDecision.Arm, auto("com.video"))
        assertEquals(GuardPolicy.AutoLockDecision.None, auto("com.other"))
        assertEquals(GuardPolicy.AutoLockDecision.None, auto("com.video", locked = true))
    }

    @Test
    fun `auto-lock does not re-arm for the app just unlocked from`() {
        assertEquals(GuardPolicy.AutoLockDecision.None, auto("com.video", suppressed = "com.video"))
        assertEquals(GuardPolicy.AutoLockDecision.Arm, auto("com.game", suppressed = "com.video"))
    }

    @Test
    fun `leaving the app during the countdown cancels it, other arming is left alone`() {
        assertEquals(GuardPolicy.AutoLockDecision.CancelArm, auto("com.launcher", arming = true, armed = "com.video"))
        assertEquals(GuardPolicy.AutoLockDecision.None, auto("com.video", arming = true, armed = "com.video"))
        assertEquals("tile-armed countdown is not ours to cancel", GuardPolicy.AutoLockDecision.None, auto("com.launcher", arming = true, armed = null))
    }

    @Test
    fun `smart defaults pick call for messengers, full screen for video apps, open otherwise`() {
        assertEquals(T.VIDEO_CALL, GuardPolicy.smartTrigger("com.whatsapp"))
        assertEquals(T.VIDEO_CALL, GuardPolicy.smartTrigger("com.google.android.apps.tachyon"))  // Google Meet / Duo
        assertEquals(T.FULLSCREEN_PLAYBACK, GuardPolicy.smartTrigger("com.google.android.youtube"))
        assertEquals(T.FULLSCREEN_PLAYBACK, GuardPolicy.smartTrigger("bbc.iplayer.android"))
        assertEquals(T.FULLSCREEN_PLAYBACK, GuardPolicy.smartTrigger("com.netflix.mediaclient"))
        assertEquals(T.OPEN, GuardPolicy.smartTrigger("com.rovio.angrybirds"))
        assertEquals("duolingo is not a video-call app", T.OPEN, GuardPolicy.smartTrigger("com.duolingo"))
    }

    @Test
    fun `triggers fire on the right signals`() {
        assertTrue(GuardPolicy.triggerSatisfied(T.OPEN, 0, false, true))
        assertFalse(GuardPolicy.triggerSatisfied(T.CALL, 0, true, false))
        assertTrue(GuardPolicy.triggerSatisfied(T.CALL, 3, false, true))
        assertTrue(GuardPolicy.triggerSatisfied(T.CALL, 2, false, true))
        assertTrue("video call: call audio plus camera", GuardPolicy.triggerSatisfied(T.VIDEO_CALL, 3, false, true, cameraInUse = true))
        assertFalse("voice call is not a video call", GuardPolicy.triggerSatisfied(T.VIDEO_CALL, 3, false, true, cameraInUse = false))
        assertFalse("camera alone is not a call", GuardPolicy.triggerSatisfied(T.VIDEO_CALL, 0, false, true, cameraInUse = true))
        assertTrue(GuardPolicy.triggerSatisfied(T.VOICE_CALL, 3, false, true, cameraInUse = false))
        assertFalse(GuardPolicy.triggerSatisfied(T.VOICE_CALL, 3, false, true, cameraInUse = true))
        assertFalse(GuardPolicy.triggerSatisfied(T.FULLSCREEN_PLAYBACK, 0, true, true))
        assertTrue(GuardPolicy.triggerSatisfied(T.FULLSCREEN_PLAYBACK, 0, true, false))
        assertFalse(GuardPolicy.triggerSatisfied(T.PLAYBACK, 0, false, false))
        assertTrue(GuardPolicy.triggerSatisfied(T.PLAYBACK, 0, true, true))
    }

    @Test
    fun `status bar is a thin system window at the top`() {
        assertTrue(GuardPolicy.isStatusBarWindow(true, 0, 120, 2400))
        assertFalse("heads-up notification is taller", GuardPolicy.isStatusBarWindow(true, 0, 300, 2400))
        assertFalse("shade is tall", GuardPolicy.isStatusBarWindow(true, 0, 2400, 2400))
        assertFalse("nav bar is at the bottom", GuardPolicy.isStatusBarWindow(true, 2280, 120, 2400))
        assertFalse("app window", GuardPolicy.isStatusBarWindow(false, 0, 120, 2400))
    }

    @Test
    fun `key consumption follows settings and chord state`() {
        assertTrue(GuardPolicy.consumeKey(HardwareKey.BACK, blockKeys = true, chordActive = false))
        assertFalse(GuardPolicy.consumeKey(HardwareKey.BACK, blockKeys = false, chordActive = true))
        assertTrue(GuardPolicy.consumeKey(HardwareKey.VOLUME_UP, blockKeys = false, chordActive = true))
        assertFalse(GuardPolicy.consumeKey(HardwareKey.VOLUME_DOWN, blockKeys = false, chordActive = false))
    }
}

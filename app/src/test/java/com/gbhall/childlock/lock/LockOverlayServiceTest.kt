package com.gbhall.childlock.lock

import android.app.NotificationManager
import android.content.Intent
import android.view.WindowManager
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class LockOverlayServiceTest {
    private var controller: ServiceController<LockOverlayService>? = null

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After
    fun tearDown() {
        controller?.destroy()
        TestSupport.resetLock()
    }

    private fun lockIntent(pkg: String? = "com.example.call", delay: Long = 0) =
        Intent(TestSupport.app, LockOverlayService::class.java)
            .setAction(LockOverlayService.ACTION_LOCK)
            .putExtra(LockOverlayService.EXTRA_PACKAGE, pkg)
            .putExtra(LockOverlayService.EXTRA_DELAY_MS, delay)

    private fun start(intent: Intent): LockOverlayService {
        val c = Robolectric.buildService(LockOverlayService::class.java, intent).create().startCommand(0, 1)
        controller = c
        return c.get()
    }

    private fun allWindows(): List<android.view.View> {
        val wm = TestSupport.app.getSystemService(WindowManager::class.java)
        return Shadow.extract<ShadowWindowManagerImpl>(wm).views
    }

    private fun overlayViews(): List<android.view.View> = allWindows().filter { it is OverlayRoot }

    private fun banners(): List<android.view.View> = allWindows().filter {
        (it.layoutParams as? WindowManager.LayoutParams)?.title == "ChildLockBanner"
    }

    private fun bannerText(v: android.view.View): String {
        val out = StringBuilder()
        fun walk(x: android.view.View) {
            if (x is android.widget.TextView) out.append(x.text).append(' ')
            if (x is android.view.ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
        }
        walk(v)
        return out.toString()
    }

    @Test
    fun `immediate lock goes foreground, attaches the overlay and reports Locked`() {
        val service = start(lockIntent())
        idle()
        val state = LockController.state
        assertTrue("expected Locked, was $state", state is LockState.Locked)
        assertEquals("com.example.call", (state as LockState.Locked).protectedPackage)

        val views = overlayViews()
        assertEquals(1, views.size)
        assertTrue(views.single() is OverlayRoot)
        val lp = views.single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, lp.type)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, lp.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, lp.height)
        assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue("overlay must be touchable", lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0)
        assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)

        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertFalse(shadowOf(service).isForegroundStopped)
    }

    @Test
    fun `unlock removes the overlay at once, shows an OFF banner, then stops the service`() {
        val service = start(lockIntent())
        idle()
        assertEquals(1, overlayViews().size)
        assertEquals("ON banner while locking", 1, banners().size)
        idle(2000)
        assertEquals("ON banner gone", 0, banners().size)
        LockController.unlock()
        idle()
        assertEquals("touch freed immediately", 0, overlayViews().size)
        assertEquals(1, banners().size)
        assertTrue(bannerText(banners().single()).contains("Child Lock off"))
        assertFalse(shadowOf(service).isStoppedBySelf)
        idle(2000)
        assertEquals(0, banners().size)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `delayed lock arms first and only attaches after the delay`() {
        start(lockIntent(delay = 3000))
        idle()
        assertTrue(LockController.state is LockState.Arming)
        assertEquals(0, overlayViews().size)
        idle(2500)
        assertTrue(LockController.state is LockState.Arming)
        idle(600)
        assertTrue(LockController.state is LockState.Locked)
        assertEquals(1, overlayViews().size)
    }

    @Test
    fun `cancelling during the arm countdown never locks`() {
        val service = start(lockIntent(delay = 3000))
        idle()
        LockController.unlock()
        idle(4000)
        assertEquals(LockState.Unlocked, LockController.state)
        assertEquals(0, overlayViews().size)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `without overlay permission nothing locks and the service stops`() {
        ShadowSettings.setCanDrawOverlays(false)
        val service = start(lockIntent())
        idle()
        assertEquals(LockState.Unlocked, LockController.state)
        assertEquals(0, overlayViews().size)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `keep screen on setting controls the window flag`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(keepScreenOn = false) }
        start(lockIntent())
        idle()
        val lp = overlayViews().single().layoutParams as WindowManager.LayoutParams
        assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0)
    }

    @Test
    fun `a second lock request while locked is ignored`() {
        val service = start(lockIntent())
        idle()
        service.onStartCommand(lockIntent(pkg = "other"), 0, 2)
        idle()
        assertEquals(1, overlayViews().size)
        assertEquals("com.example.call", (LockController.state as LockState.Locked).protectedPackage)
    }

    @Test
    fun `service restart without a lock action stops itself`() {
        val service = start(Intent(TestSupport.app, LockOverlayService::class.java))
        idle()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(LockState.Unlocked, LockController.state)
    }

    @Test
    fun `destroying the service while locked leaves the phone unlocked`() {
        start(lockIntent())
        idle()
        assertTrue(LockController.isLocked)
        controller!!.destroy()
        controller = null
        idle()
        assertEquals(LockState.Unlocked, LockController.state)
        assertEquals(0, overlayViews().size)
    }

    @Test
    fun `locked notification names the unlock gesture and is ongoing`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.VOLUME_CHORD) }
        start(lockIntent())
        idle()
        val nm = TestSupport.app.getSystemService(NotificationManager::class.java)
        val n = shadowOf(nm).allNotifications.single()
        assertTrue(n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        val text = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.contains("volume", ignoreCase = true))
    }
}

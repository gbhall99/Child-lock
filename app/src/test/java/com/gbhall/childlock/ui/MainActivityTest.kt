package com.gbhall.childlock.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class MainActivityTest {
    @Before fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        SettingsRepository.get(TestSupport.app).setupDismissed = true
    }
    @After fun tearDown() = TestSupport.resetLock()

    private fun armButton(activity: MainActivity): Button {
        fun find(v: View): Button? {
            val delay = SettingsRepository.get(activity).load().armDelaySec
            if (v is Button && v.text == activity.getString(com.gbhall.childlock.R.string.arm_button, delay)) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return find(activity.window.decorView) ?: error("arm button not found")
    }

    @Test
    fun `screen builds and arm is disabled without overlay permission`() {
        ShadowSettings.setCanDrawOverlays(false)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertFalse(armButton(activity).isEnabled)
    }

    @Test
    fun `arm refuses a volume gesture when the helper is not running`() {
        ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.VOLUME_SEQUENCE) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        armButton(activity).performClick()
        assertNull("locking with no way to unlock must be refused", shadowOf(TestSupport.app).nextStartedService)
    }

    @Test
    fun `arm starts the lock service with the configured delay and backgrounds the app`() {
        ShadowSettings.setCanDrawOverlays(true)
        // A touch gesture needs no helper, so this exercises the normal path.
        SettingsRepository.get(TestSupport.app).update { it.copy(armDelaySec = 7, gesture = GestureType.CORNER_HOLD) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val button = armButton(activity)
        assertTrue(button.isEnabled)
        button.performClick()
        val intent = shadowOf(TestSupport.app).nextStartedService
        assertNotNull(intent)
        assertEquals(LockOverlayService.ACTION_LOCK, intent.action)
        assertEquals(7000L, intent.getLongExtra(LockOverlayService.EXTRA_DELAY_MS, -1))
    }

    @Test
    fun `arm refuses badge-pin mode until a pin exists`() {
        ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.BADGE_PIN) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        armButton(activity).performClick()
        assertNull(shadowOf(TestSupport.app).nextStartedService)
    }

    @Test
    fun `every tile opens its own page`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val expected = mapOf(
            com.gbhall.childlock.R.string.section_gesture to SettingsActivity.Page.UNLOCK,
            com.gbhall.childlock.R.string.section_autolock to SettingsActivity.Page.AUTO_LOCK,
            com.gbhall.childlock.R.string.section_inside to SettingsActivity.Page.INSIDE,
            com.gbhall.childlock.R.string.session_title to SettingsActivity.Page.TIMER,
            com.gbhall.childlock.R.string.section_advanced to SettingsActivity.Page.MORE,
            com.gbhall.childlock.R.string.section_about to SettingsActivity.Page.ABOUT,
        )
        for ((titleRes, page) in expected) {
            val tile = UiTestSupport.tile(activity, activity.getString(titleRes))
            assertNotNull(page.name, tile)
            tile!!.performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(SettingsActivity::class.java.name, intent.component?.className)
            assertEquals(page.name, intent.getStringExtra(SettingsActivity.EXTRA_PAGE))
        }
    }

    @Test
    fun `tiles show the current state and pick up changes made on their pages`() {
        val repo = SettingsRepository.get(TestSupport.app)
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = c.get()
        fun sub(res: Int) = UiTestSupport.tileSubtitle(UiTestSupport.tile(activity, activity.getString(res))!!)
        assertEquals(activity.getString(com.gbhall.childlock.R.string.home_off), sub(com.gbhall.childlock.R.string.section_autolock))
        assertEquals(activity.getString(com.gbhall.childlock.R.string.home_on), sub(com.gbhall.childlock.R.string.section_inside))
        assertEquals(activity.getString(com.gbhall.childlock.R.string.session_off), sub(com.gbhall.childlock.R.string.session_title))
        assertTrue(sub(com.gbhall.childlock.R.string.section_gesture).startsWith("Volume"))

        repo.update {
            it.copy(
                autoLockRules = mapOf("com.example.tv" to com.gbhall.childlock.settings.AutoLockTrigger.OPEN),
                blockShade = false, sessionMinutes = 25, gesture = GestureType.BADGE_PIN, armDelaySec = 9,
            )
        }
        c.pause().resume()
        assertEquals("rules without the helper cannot fire", activity.getString(com.gbhall.childlock.R.string.home_needs_helper), sub(com.gbhall.childlock.R.string.section_autolock))
        assertEquals(activity.getString(com.gbhall.childlock.R.string.home_partly), sub(com.gbhall.childlock.R.string.section_inside))
        assertEquals(activity.getString(com.gbhall.childlock.R.string.session_minutes, 25), sub(com.gbhall.childlock.R.string.session_title))
        assertEquals(activity.getString(com.gbhall.childlock.R.string.gesture_name_pin), sub(com.gbhall.childlock.R.string.section_gesture))
        assertEquals("the lock button follows the countdown setting", activity.getString(com.gbhall.childlock.R.string.arm_button, 9), armButton(activity).text)

        val flat = android.content.ComponentName(TestSupport.app, com.gbhall.childlock.guard.GuardAccessibilityService::class.java).flattenToString()
        android.provider.Settings.Secure.putString(TestSupport.app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, flat)
        c.pause().resume()
        assertEquals(activity.resources.getQuantityString(com.gbhall.childlock.R.plurals.home_autolock_apps, 1, 1), sub(com.gbhall.childlock.R.string.section_autolock))
    }

    @Test
    fun `activity survives pause resume and destroy`() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        c.pause().resume().pause().stop().destroy()
    }
}

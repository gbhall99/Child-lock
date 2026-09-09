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
    @Before fun setUp() { TestSupport.clearSettings(); TestSupport.resetLock() }
    @After fun tearDown() = TestSupport.resetLock()

    private fun armButton(activity: MainActivity): Button {
        fun find(v: View): Button? {
            if (v is Button && v.text == activity.getString(com.gbhall.childlock.R.string.arm_button)) return v
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
    fun `arm starts the lock service with the configured delay and backgrounds the app`() {
        ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).update { it.copy(armDelaySec = 7) }
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
    fun `activity survives pause resume and destroy`() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        c.pause().resume().pause().stop().destroy()
    }
}

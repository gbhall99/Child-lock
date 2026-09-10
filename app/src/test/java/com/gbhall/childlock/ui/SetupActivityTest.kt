package com.gbhall.childlock.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.settings.SettingsRepository
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class SetupActivityTest {
    @Before fun setUp() { TestSupport.clearSettings(); TestSupport.resetLock() }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun button(activity: SetupActivity, label: String): Button? {
        fun find(v: View): Button? {
            if (v is Button && v.text == label) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return find(activity.window.decorView)
    }

    @Test
    fun `nothing granted, first step is overlay and finishing is blocked`() {
        ShadowSettings.setCanDrawOverlays(false)
        val a = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val done = button(a, a.getString(com.gbhall.childlock.R.string.setup_done_later))
        assertNotNull(done)
        assertFalse(done!!.isEnabled)
        assertTrue(texts(a.window.decorView).any { it.contains("Display over other apps") })
    }

    @Test
    fun `main hands off to the assistant until required steps are done`() {
        ShadowSettings.setCanDrawOverlays(false)
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val next = shadowOf(main).nextStartedActivity
        assertNotNull(next)
        assertEquals(SetupActivity::class.java.name, next.component?.className)
    }

    @Test
    fun `skip dismisses the assistant and main stops handing off`() {
        ShadowSettings.setCanDrawOverlays(false)
        val a = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        fun findText(v: View, label: String): TextView? {
            if (v is TextView && v.text == label) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), label)?.let { return it }
            return null
        }
        findText(a.window.decorView, a.getString(com.gbhall.childlock.R.string.setup_skip))!!.performClick()
        assertTrue(a.isFinishing)
        assertTrue(SettingsRepository.get(TestSupport.app).setupDismissed)
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(null, shadowOf(main).nextStartedActivity)
    }

    @Test
    fun `all required done enables finishing`() {
        ShadowSettings.setCanDrawOverlays(true)
        val flat = android.content.ComponentName(TestSupport.app, com.gbhall.childlock.guard.GuardAccessibilityService::class.java).flattenToString()
        android.provider.Settings.Secure.putString(TestSupport.app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, flat)
        val a = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val done = button(a, a.getString(com.gbhall.childlock.R.string.setup_done))
        assertNotNull(done)
        assertTrue(done!!.isEnabled)
        done.performClick()
        assertTrue(a.isFinishing)
    }

    @Test
    fun `app picker lists launchable apps and toggles selection`() {
        val pm = shadowOf(TestSupport.app.packageManager)
        val call = android.content.ComponentName("com.example.call", "com.example.call.Main")
        pm.addActivityIfNotPresent(call)
        pm.addIntentFilterForActivity(call, android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) })
        val a = Robolectric.buildActivity(AppPickerActivity::class.java).setup().get()
        fun findList(v: View): android.widget.ListView? {
            if (v is android.widget.ListView) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) findList(v.getChildAt(i))?.let { return it }
            return null
        }
        val list = findList(a.window.decorView)!!
        assertTrue(list.adapter.count >= 1)
        val row = list.adapter.getView(0, null, list)
        list.performItemClick(row, 0, 0)
        val rules = SettingsRepository.get(TestSupport.app).load().autoLockRules
        assertEquals(com.gbhall.childlock.settings.AutoLockTrigger.VIDEO_CALL, rules["com.example.call"])
        list.performItemClick(row, 0, 0)
        assertTrue(SettingsRepository.get(TestSupport.app).load().autoLockRules.isEmpty())
    }

    @Test
    fun `floating accessibility button is detected`() {
        val ctx = TestSupport.app
        val flat = android.content.ComponentName(ctx, com.gbhall.childlock.guard.GuardAccessibilityService::class.java).flattenToString()
        assertFalse(com.gbhall.childlock.guard.GuardAccessibilityService.isShortcutButtonOn(ctx))
        android.provider.Settings.Secure.putString(ctx.contentResolver, "accessibility_button_targets", "com.other/.Menu:$flat")
        assertTrue(com.gbhall.childlock.guard.GuardAccessibilityService.isShortcutButtonOn(ctx))
    }
}

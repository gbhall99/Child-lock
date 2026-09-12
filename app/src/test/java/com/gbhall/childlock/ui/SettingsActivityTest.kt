package com.gbhall.childlock.ui

import android.content.pm.ApplicationInfo
import com.gbhall.childlock.R
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class SettingsActivityTest {
    private val repo get() = SettingsRepository.get(TestSupport.app)

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
    }

    private fun page(p: SettingsActivity.Page): SettingsActivity =
        Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(TestSupport.app, p)).setup().get()

    private fun texts(a: SettingsActivity) = UiTestSupport.texts(a.window.decorView)

    @Test
    fun `every page builds with its title and survives a lifecycle`() {
        for (p in SettingsActivity.Page.entries) {
            val c = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(TestSupport.app, p)).setup()
            val a = c.get()
            assertEquals(p, a.page)
            assertTrue(p.name, texts(a).contains(a.getString(p.titleRes)))
            c.pause().resume().pause().stop().destroy()
        }
    }

    @Test
    fun `a bad or missing page extra falls back to unlock rather than crashing`() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, android.content.Intent(TestSupport.app, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, "nope")).setup().get()
        assertEquals(SettingsActivity.Page.UNLOCK, a.page)
    }

    @Test
    fun `unlock page - several ways can be allowed at once, each with its own controls`() {
        val a = page(SettingsActivity.Page.UNLOCK)
        assertNotNull("pattern order shows for the volume gesture", UiTestSupport.find(a.window.decorView, android.widget.TextView::class.java) { it.text == a.getString(R.string.pattern_option_up_down) })
        assertNull("hold time is not a volume-pattern setting", UiTestSupport.seek(a, a.getString(R.string.hold_duration))?.takeIf { it.isShown })
        assertFalse("the other ways are behind a button", UiTestSupport.switch(a, a.getString(R.string.gesture_corner_hold))!!.isShown)
        UiTestSupport.button(a, a.getString(R.string.section_other_unlock))!!.performClick()
        UiTestSupport.switch(a, a.getString(R.string.gesture_corner_hold))!!.performClick()
        assertEquals("both allowed now", setOf(GestureType.VOLUME_SEQUENCE, GestureType.CORNER_HOLD), repo.load().gestures)
        assertEquals("volume stays the main one", GestureType.VOLUME_SEQUENCE, repo.load().gesture)
        assertTrue("hold time appears for a hold gesture", UiTestSupport.seek(a, a.getString(R.string.hold_duration))!!.isShown)
        assertTrue(texts(a).contains(a.getString(R.string.pair_tl_br)))
        assertTrue("the volume pattern controls stay while volume is allowed", UiTestSupport.switch(a, a.getString(R.string.pattern_twice_title))!!.isShown)
        UiTestSupport.switch(a, a.getString(R.string.gesture_volume_sequence))!!.performClick()
        assertEquals(setOf(GestureType.CORNER_HOLD), repo.load().gestures)
        assertFalse(UiTestSupport.switch(a, a.getString(R.string.pattern_twice_title))!!.isShown)
    }

    @Test
    fun `unlock page - the last way to unlock cannot be switched off`() {
        val a = page(SettingsActivity.Page.UNLOCK)
        val volume = UiTestSupport.switch(a, a.getString(R.string.gesture_volume_sequence))!!
        volume.performClick()
        assertEquals(setOf(GestureType.VOLUME_SEQUENCE), repo.load().gestures)
        assertTrue("the switch springs back on", volume.isChecked)
    }

    @Test
    fun `unlock page - twice switch and pattern order persist`() {
        val a = page(SettingsActivity.Page.UNLOCK)
        UiTestSupport.switch(a, a.getString(R.string.pattern_twice_title))!!.performClick()
        assertEquals(2, repo.load().volumeRepeats)
        UiTestSupport.find(a.window.decorView, android.widget.TextView::class.java) { it.text == a.getString(R.string.pattern_option_down_up) }!!.performClick()
        assertEquals(com.gbhall.childlock.gesture.VolumePattern.DOWN_THEN_UP, repo.load().volumePattern)
    }

    @Test
    fun `auto-lock page - lists the rules, the add button opens the picker, switches persist`() {
        UiTestSupport.installApp("com.example.tv", "Telly", ApplicationInfo.CATEGORY_VIDEO)
        var a = page(SettingsActivity.Page.AUTO_LOCK)
        assertTrue(texts(a).contains(a.getString(R.string.autolock_none)))
        UiTestSupport.button(a, a.getString(R.string.autolock_add))!!.performClick()
        val next = shadowOf(a).nextStartedActivity
        assertNotNull("nothing is for sale yet, so nothing may stand in the way", next)
        assertEquals(AppPickerActivity::class.java.name, next.component?.className)

        repo.update { it.copy(autoLockRules = mapOf("com.example.tv" to AutoLockTrigger.FULLSCREEN_PLAYBACK)) }
        a = page(SettingsActivity.Page.AUTO_LOCK)
        val shown = texts(a)
        assertTrue(shown.contains("Telly"))
        assertTrue(shown.contains(a.getString(R.string.rule_summary, a.getString(R.string.trigger_fullscreen))))
        assertFalse(shown.contains(a.getString(R.string.autolock_none)))

        UiTestSupport.switch(a, a.getString(R.string.relock_title))!!.performClick()
        assertFalse(repo.load().relockSameApp)
        UiTestSupport.drag(UiTestSupport.seek(a, a.getString(R.string.autolock_delay))!!, 30)
        assertEquals(30, repo.load().autoLockDelaySec)
        assertTrue("the helper note shows while the helper is off", texts(a).contains(a.getString(R.string.autolock_note)))
    }

    @Test
    fun `keep them inside page - every switch persists and swipe blocking is on by default`() {
        val a = page(SettingsActivity.Page.INSIDE)
        val gestures = UiTestSupport.switch(a, a.getString(R.string.block_gestures))!!
        assertTrue(gestures.isChecked)
        gestures.performClick()
        assertFalse(repo.load().blockGestures)
        UiTestSupport.switch(a, a.getString(R.string.block_shade))!!.performClick()
        assertFalse(repo.load().blockShade)
        UiTestSupport.switch(a, a.getString(R.string.relaunch_app))!!.performClick()
        assertFalse(repo.load().relaunchApp)
        UiTestSupport.switch(a, a.getString(R.string.block_keys))!!.performClick()
        assertFalse(repo.load().blockKeys)
    }

    @Test
    fun `timer page - the slider sets the hand-back time in five minute steps`() {
        val a = page(SettingsActivity.Page.TIMER)
        UiTestSupport.drag(UiTestSupport.seek(a, a.getString(R.string.session_title))!!, 4)
        assertEquals(20, repo.load().sessionMinutes)
        UiTestSupport.drag(UiTestSupport.seek(a, a.getString(R.string.session_title))!!, 0)
        assertEquals(0, repo.load().sessionMinutes)
    }

    @Test
    fun `more page - countdown, screen and lock icon corner persist`() {
        val a = page(SettingsActivity.Page.MORE)
        UiTestSupport.drag(UiTestSupport.seek(a, a.getString(R.string.arm_delay))!!, 8)
        assertEquals(8, repo.load().armDelaySec)
        UiTestSupport.switch(a, a.getString(R.string.keep_screen_on))!!.performClick()
        assertFalse(repo.load().keepScreenOn)
        UiTestSupport.radio(a, a.getString(R.string.corner_br))!!.performClick()
        assertEquals(Corner.BOTTOM_RIGHT, repo.load().badgeCorner)
        UiTestSupport.button(a, a.getString(R.string.open))!!.performClick()
        assertEquals(SetupActivity::class.java.name, shadowOf(a).nextStartedActivity.component?.className)
    }

    @Test
    fun `about page - names the version and links out`() {
        val a = page(SettingsActivity.Page.ABOUT)
        assertTrue(texts(a).contains(a.getString(R.string.about_version)))
        assertTrue("the stuck advice lives here too", texts(a).contains(a.getString(R.string.about_stuck_body)))
        assertTrue(texts(a).contains(a.getString(R.string.about_privacy)))
        // The privacy policy is the first link row, so its Open button is the first.
        UiTestSupport.button(a, a.getString(R.string.open))!!.performClick()
        assertEquals(SettingsActivity.URL_PRIVACY, shadowOf(a).nextStartedActivity.dataString)
    }
}

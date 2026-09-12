package com.gbhall.childlock.ui

import android.app.AlertDialog
import android.widget.ListView
import com.gbhall.childlock.R
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class RuleEditorTest {
    private val repo get() = SettingsRepository.get(TestSupport.app)
    private lateinit var activity: MainActivity

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        repo.setupDismissed = true
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private fun dialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
    private fun options(d: AlertDialog): List<String> {
        val list = d.listView as ListView
        return (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
    }

    @Test
    fun `offers the moments that suit the app, with the current one selected`() {
        repo.update { it.copy(autoLockRules = mapOf("com.google.android.youtube" to AutoLockTrigger.PLAYBACK)) }
        var done = 0
        RuleEditor.show(activity, "com.google.android.youtube", "YouTube") { done++ }
        val d = dialog()
        assertEquals(
            listOf(R.string.trigger_fullscreen, R.string.trigger_playback, R.string.trigger_open).map(activity::getString),
            options(d),
        )
        assertEquals(1, d.listView.checkedItemPosition)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        TestSupport.idle()
        assertEquals(1, done)
        assertEquals(AutoLockTrigger.PLAYBACK, repo.load().autoLockRules["com.google.android.youtube"])
    }

    @Test
    fun `choosing another moment saves it`() {
        repo.update { it.copy(autoLockRules = mapOf("com.whatsapp" to AutoLockTrigger.VIDEO_CALL)) }
        RuleEditor.show(activity, "com.whatsapp", "WhatsApp")
        val d = dialog()
        val voice = options(d).indexOf(activity.getString(R.string.trigger_voice_call))
        d.listView.performItemClick(null, voice, voice.toLong())
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        TestSupport.idle()
        assertEquals(AutoLockTrigger.VOICE_CALL, repo.load().autoLockRules["com.whatsapp"])
    }

    @Test
    fun `remove takes the app out of the list, cancel changes nothing`() {
        repo.update { it.copy(autoLockRules = mapOf("com.whatsapp" to AutoLockTrigger.VIDEO_CALL, "com.netflix.mediaclient" to AutoLockTrigger.OPEN)) }
        RuleEditor.show(activity, "com.whatsapp", "WhatsApp")
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        TestSupport.idle()
        assertEquals(2, repo.load().autoLockRules.size)
        RuleEditor.show(activity, "com.whatsapp", "WhatsApp")
        dialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        TestSupport.idle()
        assertNull(repo.load().autoLockRules["com.whatsapp"])
        assertEquals(AutoLockTrigger.OPEN, repo.load().autoLockRules["com.netflix.mediaclient"])
    }

    @Test
    fun `a choice the profile would not offer stays editable`() {
        repo.update { it.copy(autoLockRules = mapOf("com.netflix.mediaclient" to AutoLockTrigger.CALL)) }
        RuleEditor.show(activity, "com.netflix.mediaclient", "Netflix")
        val d = dialog()
        assertTrue(activity.getString(R.string.trigger_call) in options(d))
        assertEquals(options(d).indexOf(activity.getString(R.string.trigger_call)), d.listView.checkedItemPosition)
    }
}

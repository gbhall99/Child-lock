package com.gbhall.childlock.ui

import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.widget.EditText
import android.widget.ListView
import com.gbhall.childlock.R
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class AppPickerActivityTest {
    @Before
    fun setUp() {
        TestSupport.clearSettings()
        UiTestSupport.installApp("com.example.tv", "Telly", ApplicationInfo.CATEGORY_VIDEO)
        UiTestSupport.installApp("com.example.chat", "Chat")
        UiTestSupport.installApp("com.whatsapp", "WhatsApp")
    }

    private fun picker() = Robolectric.buildActivity(AppPickerActivity::class.java).setup().get()
    private fun list(a: AppPickerActivity) = UiTestSupport.find(a.window.decorView, ListView::class.java) { true }!!
    private fun labels(a: AppPickerActivity): List<String> {
        val l = list(a)
        return (0 until l.adapter.count).map { (l.adapter.getItem(it) as AppPickerActivity.AppEntry).label }
    }

    @Test
    fun `lists every launchable app alphabetically, never itself`() {
        val a = picker()
        val shown = labels(a)
        assertEquals(listOf("Chat", "Telly", "WhatsApp"), shown.filter { it in setOf("Chat", "Telly", "WhatsApp") })
        assertTrue("Child Lock must not offer to lock itself", shown.none { it == a.getString(R.string.app_name) })
    }

    @Test
    fun `search narrows the list by name or package`() {
        val a = picker()
        val search = UiTestSupport.find(a.window.decorView, EditText::class.java) { true }!!
        search.setText("tel")
        assertEquals(listOf("Telly"), labels(a))
        search.setText("whatsapp")
        assertEquals(listOf("WhatsApp"), labels(a))
        search.setText("")
        assertTrue(labels(a).size >= 3)
    }

    @Test
    fun `tapping an app adds it with the best default and opens the editor`() {
        val a = picker()
        val l = list(a)
        val position = (0 until l.adapter.count).first { (l.adapter.getItem(it) as AppPickerActivity.AppEntry).packageName == "com.example.tv" }
        l.performItemClick(l.adapter.getView(position, null, l), position, position.toLong())
        assertEquals(
            "a video app locks when a video goes full screen",
            AutoLockTrigger.FULLSCREEN_PLAYBACK, SettingsRepository.get(TestSupport.app).load().autoLockRules["com.example.tv"],
        )
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("the moment is shown straight away so it can be changed", dialog)
        assertTrue(shadowOf(dialog).title.toString().contains("Telly"))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        TestSupport.idle()
        assertTrue("done returns to the list of rules", a.isFinishing)
    }

    @Test
    fun `an app already added is marked and is not added twice`() {
        SettingsRepository.get(TestSupport.app).update { it.copy(autoLockRules = mapOf("com.whatsapp" to AutoLockTrigger.VOICE_CALL)) }
        val a = picker()
        val l = list(a)
        val position = (0 until l.adapter.count).first { (l.adapter.getItem(it) as AppPickerActivity.AppEntry).packageName == "com.whatsapp" }
        val row = l.adapter.getView(position, null, l)
        assertTrue(UiTestSupport.texts(row).any { it.contains(a.getString(R.string.picker_added)) })
        l.performItemClick(row, position, position.toLong())
        assertEquals("the parent's own choice is kept", AutoLockTrigger.VOICE_CALL, SettingsRepository.get(TestSupport.app).load().autoLockRules["com.whatsapp"])
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog())
    }
}

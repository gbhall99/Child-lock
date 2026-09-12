package com.gbhall.childlock.ui

import android.widget.Button
import android.widget.TextView
import com.gbhall.childlock.R
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.billing.FakeBilling
import com.gbhall.childlock.billing.FeatureGate
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings

/** The home screen through the trial, its end, and the purchase. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTrialTest {
    private val day = 24L * 60 * 60 * 1000
    private var now = System.currentTimeMillis()

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        FeatureGate.clock = { now }
        FeatureGate.trialStart(TestSupport.app)
        ShadowSettings.setCanDrawOverlays(true)
        SettingsRepository.get(TestSupport.app).setupDismissed = true
        // A touch gesture needs no helper, so the lock can actually start here.
        SettingsRepository.get(TestSupport.app).update { it.copy(gesture = GestureType.CORNER_HOLD) }
    }

    @After
    fun tearDown() {
        FeatureGate.clock = { System.currentTimeMillis() }
        TestSupport.resetLock()
    }

    private fun launch(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun trialRow(a: MainActivity) = a.window.decorView.findViewWithTag<android.view.View>("trial")
    private fun bigButton(a: MainActivity): Button =
        UiTestSupport.find(a.window.decorView, Button::class.java) { it.height > 0 && (it.text.startsWith("Lock") || it.text.startsWith("Buy")) }!!

    @Test
    fun `the trial row counts down and the big button still locks`() {
        val a = launch()
        assertTrue("the store is asked to restore on launch", FakeBilling.current.connects >= 1)
        val row = trialRow(a)
        assertEquals(android.view.View.VISIBLE, row.visibility)
        assertTrue(UiTestSupport.texts(row).contains("30 days left"))
        assertEquals(a.getString(R.string.arm_button, 10), bigButton(a).text)
        bigButton(a).performClick()
        assertNotNull("locks as normal", shadowOf(TestSupport.app).nextStartedService)
    }

    @Test
    fun `after the trial the hero, the button and the tap all lead to the purchase`() {
        now += 31 * day
        val a = launch()
        assertEquals(android.view.View.GONE, trialRow(a).visibility)
        assertTrue(UiTestSupport.texts(a.window.decorView).contains(a.getString(R.string.trial_over)))
        assertEquals("Buy for £2.99", bigButton(a).text)
        bigButton(a).performClick()
        assertNull("no lock starts", shadowOf(TestSupport.app).nextStartedService)
        assertNotNull("the purchase sheet opens instead", ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun `a purchase arriving from the store puts the lock button back at once`() {
        now += 31 * day
        val a = launch()
        assertEquals("Buy for £2.99", bigButton(a).text)
        FakeBilling.purchase(a)
        assertEquals(a.getString(R.string.arm_button, 10), bigButton(a).text)
        assertEquals(android.view.View.GONE, trialRow(a).visibility)
        assertTrue(UiTestSupport.texts(a.window.decorView).contains(a.getString(R.string.state_unlocked)))
        bigButton(a).performClick()
        assertNotNull(shadowOf(TestSupport.app).nextStartedService)
    }

    @Test
    fun `the about page shows the purchase state with buy and restore, then just bought`() {
        val c = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(TestSupport.app, SettingsActivity.Page.ABOUT)).setup()
        val a = c.get()
        val root = a.window.decorView
        assertTrue(UiTestSupport.texts(root).contains("Free trial: 30 days left"))
        assertNotNull(root.findViewWithTag<Button>("buy"))
        root.findViewWithTag<Button>("restore").performClick()
        assertEquals(1, FakeBilling.current.restores)
        FakeBilling.purchase(a)
        assertTrue(UiTestSupport.texts(root).contains(a.getString(R.string.buy_status_purchased)))
        assertNull(root.findViewWithTag<Button>("buy"))
        assertNull(root.findViewWithTag<Button>("restore"))
        c.pause().stop().destroy()
    }

    @Test
    fun `a sideload style build shows no trial anywhere`() {
        FakeBilling.current.canSell = false
        FeatureGate.trialStart(TestSupport.app)
        now += 400 * day
        val a = launch()
        assertEquals(android.view.View.GONE, trialRow(a).visibility)
        assertEquals(a.getString(R.string.arm_button, 10), bigButton(a).text)
        val about = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(TestSupport.app, SettingsActivity.Page.ABOUT)).setup().get()
        assertNull(about.window.decorView.findViewWithTag<TextView>("buy"))
    }
}

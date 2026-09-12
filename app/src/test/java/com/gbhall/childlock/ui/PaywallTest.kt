package com.gbhall.childlock.ui

import android.app.AlertDialog
import android.widget.TextView
import com.gbhall.childlock.R
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.billing.FakeBilling
import com.gbhall.childlock.billing.FeatureGate
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class PaywallTest {
    private lateinit var activity: MainActivity
    private val fake get() = FakeBilling.current
    private val day = 24L * 60 * 60 * 1000
    private var now = System.currentTimeMillis()

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        FeatureGate.clock = { now }
        FeatureGate.trialStart(TestSupport.app)
        SettingsRepository.get(TestSupport.app).setupDismissed = true
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        FeatureGate.clock = { System.currentTimeMillis() }
    }

    private fun dialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog().also { assertNotNull(it) }
    private fun message(d: AlertDialog) = d.findViewById<TextView>(android.R.id.message).text.toString()
    private fun click(d: AlertDialog, which: Int) {
        d.getButton(which).performClick()
        TestSupport.idle()
    }

    @Test
    fun `during the trial the sheet says how long is left and offers the price`() {
        Paywall.show(activity)
        val d = dialog()
        val text = message(d)
        assertTrue(text, text.contains("Free trial: 30 days left"))
        assertTrue(text, text.contains(activity.getString(R.string.buy_body)))
        assertEquals("Buy for £2.99", d.getButton(AlertDialog.BUTTON_POSITIVE).text)
        assertEquals(activity.getString(R.string.buy_restore), d.getButton(AlertDialog.BUTTON_NEUTRAL).text)
        click(d, AlertDialog.BUTTON_POSITIVE)
        assertEquals(1, fake.buys)
    }

    @Test
    fun `after the trial the sheet says so, and Buy without a known price still buys`() {
        now += 31 * day
        fake.price = null
        Paywall.show(activity)
        val d = dialog()
        assertTrue(message(d).startsWith(activity.getString(R.string.trial_over)))
        assertEquals(activity.getString(R.string.buy), d.getButton(AlertDialog.BUTTON_POSITIVE).text)
        click(d, AlertDialog.BUTTON_POSITIVE)
        assertEquals(1, fake.buys)
    }

    @Test
    fun `a store that cannot open the purchase sheet is reported, not swallowed`() {
        fake.buyResult = false
        Paywall.show(activity)
        click(dialog(), AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.toast_store_unavailable), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `restore reports whether the account owns the app`() {
        Paywall.show(activity)
        click(dialog(), AlertDialog.BUTTON_NEUTRAL)
        assertEquals(1, fake.restores)
        assertEquals(activity.getString(R.string.toast_restore_none), ShadowToast.getTextOfLatestToast())
        fake.restoreResult = true
        Paywall.restore(activity)
        assertEquals(activity.getString(R.string.toast_restored), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `once bought the sheet only says thank you`() {
        FakeBilling.purchase(activity)
        Paywall.show(activity)
        val d = dialog()
        assertEquals(activity.getString(R.string.buy_done), message(d))
        assertFalse(d.getButton(AlertDialog.BUTTON_NEUTRAL).isShown)
    }
}

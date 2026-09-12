package com.gbhall.childlock.billing

import android.app.AlertDialog
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.settings.SettingsRepository
import com.gbhall.childlock.ui.MainActivity
import com.gbhall.childlock.ui.Paywall
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

@RunWith(RobolectricTestRunner::class)
class FeatureGateTest {
    @Before
    fun setUp() {
        TestSupport.clearSettings()
        FeatureGate.setPreviewFree(TestSupport.app, false)
    }

    @Test
    fun `nothing is withheld while nothing is for sale, even in a release-style build`() {
        // Gradle runs these tests as the debug build; strip the flag so this
        // proves the release case, which is where the gate used to bite.
        val info = TestSupport.app.applicationInfo
        val original = info.flags
        info.flags = original and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
        try {
            assertFalse(FeatureGate.isDebuggable(TestSupport.app))
            assertFalse(FeatureGate.BILLING_READY)
            assertTrue(FeatureGate.isPro(TestSupport.app))
            for (f in FeatureGate.Feature.entries) assertTrue(f.name, FeatureGate.has(TestSupport.app, f))
        } finally {
            info.flags = original
        }
    }

    @Test
    fun `the free preview shows the gate, and the gate explains then lets the parent through`() {
        FeatureGate.setPreviewFree(TestSupport.app, true)
        assertFalse(FeatureGate.isPro(TestSupport.app))
        SettingsRepository.get(TestSupport.app).setupDismissed = true
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var allowed = 0
        Paywall.require(activity, FeatureGate.Feature.AUTO_LOCK) { allowed++ }
        assertEquals("explained first", 0, allowed)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        TestSupport.idle()
        assertEquals("never blocks what it says is unlocked", 1, allowed)
    }
}

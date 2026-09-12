package com.gbhall.childlock.guard

import android.media.AudioManager
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.billing.FakeBilling
import com.gbhall.childlock.billing.FeatureGate
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

/** Auto-lock stops asking for locks once the trial is over, and resumes on purchase. */
@RunWith(RobolectricTestRunner::class)
class GuardTrialTest {
    private lateinit var service: GuardAccessibilityService
    private lateinit var audio: AudioManager
    private val day = 24L * 60 * 60 * 1000
    private var now = System.currentTimeMillis()

    @Before
    fun setUp() {
        FeatureGate.clock = { now }
        service = GuardRig.start()
        ShadowSettings.setCanDrawOverlays(true)
        audio = TestSupport.app.getSystemService(AudioManager::class.java)
        FeatureGate.trialStart(TestSupport.app)
        rule()
    }

    /** The rig clears settings on start, so the rule goes in after it. */
    private fun rule() {
        SettingsRepository.get(TestSupport.app).update {
            it.copy(autoLockRules = mapOf(GuardRig.APP to AutoLockTrigger.VOICE_CALL), autoLockDelaySec = 5)
        }
        idle()
    }

    @After
    fun tearDown() {
        audio.mode = AudioManager.MODE_NORMAL
        shadowOf(audio).setIsMusicActive(false)
        service.onUnbind(null)
        FeatureGate.clock = { System.currentTimeMillis() }
        TestSupport.resetLock()
    }

    private fun call() {
        service.onAccessibilityEvent(GuardRig.windowEvent(GuardRig.APP))
        idle()
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        idle(2500)
    }

    @Test
    fun `an expired trial turns a satisfied rule into nothing`() {
        now += 31 * day
        call()
        assertNull(GuardRig.nextLockRequest())
    }

    @Test
    fun `the same rule arms during the trial and again once bought`() {
        call()
        assertNotNull("trial", GuardRig.nextLockRequest())
        TestSupport.resetLock()
        service.onUnbind(null)

        now += 31 * day
        service = GuardRig.start()
        FeatureGate.trialStart(TestSupport.app)
        rule()
        FakeBilling.purchase(TestSupport.app)
        audio.mode = AudioManager.MODE_NORMAL
        idle()
        call()
        assertNotNull("bought", GuardRig.nextLockRequest())
    }
}

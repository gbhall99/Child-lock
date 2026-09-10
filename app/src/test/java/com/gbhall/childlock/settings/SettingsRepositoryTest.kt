package com.gbhall.childlock.settings

import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.gesture.CornerPair
import com.gbhall.childlock.gesture.VolumePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        TestSupport.clearSettings()
        repo = SettingsRepository.get(TestSupport.app)
    }

    @Test
    fun `defaults are sane`() {
        val s = repo.load()
        assertEquals(GestureType.VOLUME_SEQUENCE, s.gesture)
        assertEquals(VolumePattern.UP_THEN_DOWN, s.volumePattern)
        assertEquals(1, s.volumeRepeats)
        assertEquals(1500L, s.holdMs)
        assertEquals(CornerPair.TOP_LEFT_BOTTOM_RIGHT, s.cornerPair)
        assertEquals(Corner.TOP_LEFT, s.badgeCorner)
        assertFalse(s.hasPin)
        assertTrue(s.keepScreenOn)
        assertEquals(5, s.armDelaySec)
        assertTrue(s.blockKeys && s.blockShade && s.relaunchApp)
    }

    @Test
    fun `settings round-trip`() {
        val wanted = LockSettings(
            gesture = GestureType.BADGE_PIN, holdMs = 2200, cornerPair = CornerPair.TOP_RIGHT_BOTTOM_LEFT,
            badgeCorner = Corner.BOTTOM_RIGHT, pinHash = "abc", pinSalt = "beef", pinLength = 6, keepScreenOn = false,
            volumePattern = VolumePattern.DOWN_THEN_UP, volumeRepeats = 2,
            armDelaySec = 8, blockKeys = false, blockShade = false, relaunchApp = false, blockGestures = false,
            autoLockRules = mapOf("com.a" to AutoLockTrigger.CALL, "com.b" to AutoLockTrigger.FULLSCREEN_PLAYBACK), autoLockDelaySec = 30,
        )
        repo.save(wanted)
        assertEquals(wanted, repo.load())
        assertTrue(repo.load().hasPin)
    }

    @Test
    fun `out-of-range values are clamped on load`() {
        repo.save(LockSettings(holdMs = 99_999, armDelaySec = 0))
        val s = repo.load()
        assertEquals(LockSettings.MAX_HOLD_MS, s.holdMs)
        assertEquals(LockSettings.MIN_ARM_DELAY_SEC, s.armDelaySec)
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        TestSupport.app.getSharedPreferences("childlock", 0).edit().putString("gesture", "LASER").commit()
        assertEquals(GestureType.VOLUME_SEQUENCE, repo.load().gesture)
    }

    @Test
    fun `update applies a transform`() {
        repo.update { it.copy(armDelaySec = 3) }
        assertEquals(3, repo.load().armDelaySec)
    }
}

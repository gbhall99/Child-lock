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
        assertEquals(10, s.armDelaySec)
        assertEquals(5, s.autoLockDelaySec)
        assertEquals(setOf(GestureType.VOLUME_SEQUENCE), s.gestures)
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

    @Test
    fun `explore-by-touch swipe blocking is on by default`() {
        // Off, every system swipe lands and is undone a moment later, which on
        // hardware reads as a lock that does not lock.
        assertTrue(repo.load().blockGestures)
    }

    @Test
    fun `the version 4 migration that forced gesture blocking off is undone`() {
        val prefs = TestSupport.app.getSharedPreferences("childlock", 0)
        prefs.edit().putBoolean("block_gestures", false).putInt("settings_version", 4).commit()
        SettingsRepository.resetForTests()
        assertTrue("v4 stored an off for everyone", SettingsRepository.get(TestSupport.app).load().blockGestures)

        // And once undone, a parent who deliberately turns it off keeps it off.
        SettingsRepository.get(TestSupport.app).update { it.copy(blockGestures = false) }
        SettingsRepository.resetForTests()
        assertFalse(SettingsRepository.get(TestSupport.app).load().blockGestures)
    }

    @Test
    fun `the migration does not fire twice`() {
        val prefs = TestSupport.app.getSharedPreferences("childlock", 0)
        repo.update { it.copy(blockGestures = false) }
        assertEquals(6, prefs.getInt("settings_version", 0))
        SettingsRepository.resetForTests()
        assertFalse("a settled choice must survive every later start", SettingsRepository.get(TestSupport.app).load().blockGestures)
    }

    @Test
    fun `three repeats survive a round trip`() {
        repo.update { it.copy(volumeRepeats = 3) }
        assertEquals(3, repo.load().volumeRepeats)
    }

    @Test
    fun `the version 6 migration hands everyone the new countdowns, then keeps their choice`() {
        val prefs = TestSupport.app.getSharedPreferences("childlock", 0)
        prefs.edit().putInt("arm_delay_sec", 5).putInt("auto_lock_delay_sec", 15).putInt("settings_version", 5).commit()
        SettingsRepository.resetForTests()
        val s = SettingsRepository.get(TestSupport.app).load()
        assertEquals(10, s.armDelaySec)
        assertEquals(5, s.autoLockDelaySec)
        SettingsRepository.get(TestSupport.app).update { it.copy(armDelaySec = 4, autoLockDelaySec = 20) }
        SettingsRepository.resetForTests()
        assertEquals(4, SettingsRepository.get(TestSupport.app).load().armDelaySec)
        assertEquals(20, SettingsRepository.get(TestSupport.app).load().autoLockDelaySec)
    }

    @Test
    fun `several ways to unlock survive a round trip, with the main one first`() {
        repo.save(LockSettings().withGestures(setOf(GestureType.BADGE_PIN, GestureType.VOLUME_CHORD, GestureType.CORNER_HOLD)))
        val s = repo.load()
        assertEquals(setOf(GestureType.BADGE_PIN, GestureType.VOLUME_CHORD, GestureType.CORNER_HOLD), s.gestures)
        assertEquals("first in the fixed order is the main one", GestureType.CORNER_HOLD, s.gesture)
        assertEquals(setOf(GestureType.BADGE_PIN, GestureType.VOLUME_CHORD), s.extraGestures)
        assertTrue(s.hasVolumeGesture)
        assertTrue(s.hasTouchGesture)
        // The main one is never also listed as an extra, even if the stored set says so.
        val prefs = TestSupport.app.getSharedPreferences("childlock", 0)
        prefs.edit().putString("gesture", "VOLUME_SEQUENCE").putStringSet("extra_gestures", setOf("VOLUME_SEQUENCE", "BADGE_PIN")).commit()
        SettingsRepository.resetForTests()
        assertEquals(setOf(GestureType.BADGE_PIN), SettingsRepository.get(TestSupport.app).load().extraGestures)
    }
}

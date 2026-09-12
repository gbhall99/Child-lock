package com.gbhall.childlock.lock

import com.gbhall.childlock.settings.GestureType
import org.junit.Assert.assertEquals
import org.junit.Test

class LockPreflightTest {
    @Test
    fun `a volume gesture needs the helper running`() {
        assertEquals(
            LockPreflight.Result.HelperNeeded,
            LockPreflight.check(GestureType.VOLUME_SEQUENCE, helperConnected = false, touchExplorationOn = false),
        )
        assertEquals(
            LockPreflight.Result.Ok,
            LockPreflight.check(GestureType.VOLUME_SEQUENCE, helperConnected = true, touchExplorationOn = false),
        )
    }

    @Test
    fun `touch unlocks are refused while a screen reader explores by touch`() {
        assertEquals(
            LockPreflight.Result.ScreenReaderNeedsVolume,
            LockPreflight.check(GestureType.CORNER_HOLD, helperConnected = true, touchExplorationOn = true),
        )
        assertEquals(
            LockPreflight.Result.ScreenReaderNeedsVolume,
            LockPreflight.check(GestureType.BADGE_PIN, helperConnected = false, touchExplorationOn = true),
        )
        assertEquals(
            "volume still works under a screen reader",
            LockPreflight.Result.Ok,
            LockPreflight.check(GestureType.VOLUME_SEQUENCE, helperConnected = true, touchExplorationOn = true),
        )
    }

    @Test
    fun `volume unlocks are refused while switches use the volume keys`() {
        assertEquals(
            LockPreflight.Result.SwitchAccessNeedsTouch,
            LockPreflight.check(
                GestureType.VOLUME_SEQUENCE, helperConnected = true,
                touchExplorationOn = false, keyFilteringToolActive = true,
            ),
        )
        assertEquals(
            "a screen reader is the other way round: touch is the problem, not keys",
            LockPreflight.Result.Ok,
            LockPreflight.check(
                GestureType.VOLUME_SEQUENCE, helperConnected = true,
                touchExplorationOn = true, keyFilteringToolActive = true,
            ),
        )
    }

    @Test
    fun `touch unlocks are fine without a screen reader and need no helper`() {
        assertEquals(
            LockPreflight.Result.Ok,
            LockPreflight.check(GestureType.CORNER_HOLD, helperConnected = false, touchExplorationOn = false),
        )
    }

    @Test
    fun `with several ways out, the lock is safe as long as one of them works`() {
        val both = setOf(GestureType.VOLUME_SEQUENCE, GestureType.CORNER_HOLD)
        assertEquals("no helper, but the corners work", LockPreflight.Result.Ok, LockPreflight.check(both, helperConnected = false, touchExplorationOn = false))
        assertEquals("screen reader, but the volume works", LockPreflight.Result.Ok, LockPreflight.check(both, helperConnected = true, touchExplorationOn = true))
        assertEquals("switches on the volume keys, but the corners work", LockPreflight.Result.Ok, LockPreflight.check(both, helperConnected = true, touchExplorationOn = false, keyFilteringToolActive = true))
        val volumeOnly = setOf(GestureType.VOLUME_SEQUENCE, GestureType.VOLUME_CHORD)
        assertEquals(LockPreflight.Result.HelperNeeded, LockPreflight.check(volumeOnly, helperConnected = false, touchExplorationOn = false))
        val touchOnly = setOf(GestureType.CORNER_HOLD, GestureType.BADGE_PIN)
        assertEquals(LockPreflight.Result.ScreenReaderNeedsVolume, LockPreflight.check(touchOnly, helperConnected = true, touchExplorationOn = true))
    }
}

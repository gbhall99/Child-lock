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
    fun `touch unlocks are fine without a screen reader and need no helper`() {
        assertEquals(
            LockPreflight.Result.Ok,
            LockPreflight.check(GestureType.CORNER_HOLD, helperConnected = false, touchExplorationOn = false),
        )
    }
}

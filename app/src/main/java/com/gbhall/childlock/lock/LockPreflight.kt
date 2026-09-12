package com.gbhall.childlock.lock

import com.gbhall.childlock.settings.GestureType

/**
 * Refuses to lock when the chosen way out would not work. Pure, so every
 * stranding case is unit tested.
 *
 * Two ways a parent could otherwise be stranded:
 *  - A volume gesture is chosen but the helper is not running, so nothing
 *    sees the keys, while the shield still swallows every touch.
 *  - A screen reader is exploring by touch, which turns the shield's touches
 *    into hover events, so the corner hold and the badge PIN never fire.
 */
object LockPreflight {
    sealed interface Result {
        data object Ok : Result
        data object HelperNeeded : Result
        data object ScreenReaderNeedsVolume : Result
        data object SwitchAccessNeedsTouch : Result
    }

    /**
     * @param keyFilteringToolActive another accessibility tool is filtering key
     *   events, which is how Switch Access maps switches to the volume keys.
     *   Consuming those keys would take the person's switches away.
     */
    fun check(
        gesture: GestureType,
        helperConnected: Boolean,
        touchExplorationOn: Boolean,
        keyFilteringToolActive: Boolean = false,
    ): Result = check(setOf(gesture), helperConnected, touchExplorationOn, keyFilteringToolActive)

    /** With several ways out allowed, the lock is safe as long as one of them will work. */
    fun check(
        gestures: Set<GestureType>,
        helperConnected: Boolean,
        touchExplorationOn: Boolean,
        keyFilteringToolActive: Boolean = false,
    ): Result {
        val volume = gestures.any { it.needsGuard }
        val touch = gestures.any { !it.needsGuard }
        return when {
            !touch && !helperConnected -> Result.HelperNeeded
            !touch && keyFilteringToolActive && !touchExplorationOn -> Result.SwitchAccessNeedsTouch
            !volume && touchExplorationOn -> Result.ScreenReaderNeedsVolume
            else -> Result.Ok
        }
    }
}

package com.gbhall.childlock.lock

import android.content.Context
import com.gbhall.childlock.R
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings

/**
 * Why a lock must not start right now, as the message to show, or null when
 * it is safe. Every way of locking asks this first: the Lock button, the
 * Quick Settings tile, auto-lock and practising.
 */
fun lockRefusal(context: Context, s: LockSettings): Int? {
    // A PIN unlock with no PIN set opens nothing, so it is not a way out.
    val usable = if (s.hasPin) s.gestures else s.gestures - GestureType.BADGE_PIN
    if (usable.isEmpty()) return R.string.toast_need_pin
    val exploring = try {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    } catch (e: Exception) {
        false
    }
    return when (LockPreflight.check(usable, GuardAccessibilityService.isConnected, exploring, GuardAccessibilityService.keyToolActive)) {
        LockPreflight.Result.HelperNeeded -> R.string.toast_needs_helper
        LockPreflight.Result.ScreenReaderNeedsVolume -> R.string.toast_screen_reader
        LockPreflight.Result.SwitchAccessNeedsTouch -> R.string.toast_switch_access
        LockPreflight.Result.Ok -> null
    }
}

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

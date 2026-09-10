package com.gbhall.childlock.guard

/**
 * Pure decision logic for the accessibility guard, kept free of Android types
 * so it can be unit tested. The service gathers facts; this decides.
 */
object GuardPolicy {
    const val SYSTEM_UI = "com.android.systemui"

    /** Fraction of the screen height a system window must cover to count as the open shade. */
    const val SHADE_MIN_HEIGHT_FRACTION = 0.4f

    const val RELAUNCH_DEBOUNCE_MS = 1500L
    const val SHADE_DISMISS_DEBOUNCE_MS = 300L

    /** A tall SystemUI system-type window is the notification shade (or quick settings). */
    fun isShadeWindow(isSystemType: Boolean, windowHeight: Int, screenHeight: Int, packageName: String?): Boolean =
        isSystemType &&
            packageName == SYSTEM_UI &&
            screenHeight > 0 &&
            windowHeight >= screenHeight * SHADE_MIN_HEIGHT_FRACTION

    sealed interface RelaunchDecision {
        data object Relaunch : RelaunchDecision
        data class Skip(val reason: String) : RelaunchDecision
    }

    /**
     * Whether the protected app should be brought back to the front.
     * Never fights a real phone call, the keyguard, or SystemUI itself.
     */
    fun relaunchDecision(
        protectedPackage: String?,
        foregroundPackage: String?,
        selfPackage: String,
        dialerPackage: String?,
        keyguardLocked: Boolean,
        nowMs: Long,
        lastRelaunchMs: Long,
    ): RelaunchDecision {
        if (protectedPackage == null) return RelaunchDecision.Skip("nothing to protect")
        if (foregroundPackage == null) return RelaunchDecision.Skip("foreground unknown")
        if (foregroundPackage == protectedPackage) return RelaunchDecision.Skip("already in front")
        if (foregroundPackage == selfPackage) return RelaunchDecision.Skip("child lock itself")
        if (foregroundPackage == SYSTEM_UI) return RelaunchDecision.Skip("system ui")
        if (dialerPackage != null && foregroundPackage == dialerPackage) return RelaunchDecision.Skip("phone call")
        if (foregroundPackage.contains("incallui")) return RelaunchDecision.Skip("phone call")
        if (keyguardLocked) return RelaunchDecision.Skip("keyguard")
        if (nowMs - lastRelaunchMs < RELAUNCH_DEBOUNCE_MS) return RelaunchDecision.Skip("debounce")
        return RelaunchDecision.Relaunch
    }

    /**
     * Accessibility-service flags to add while locked. Touch-exploration mode
     * plus multi-finger gestures is what stops one-finger home/back swipes at
     * the system level (screen readers rely on the same switch). It is only
     * safe with a volume gesture, because touch exploration turns the
     * overlay's touches into hover events, so the touch-based unlocks stop.
     */
    fun gestureBlockFlags(locked: Boolean, blockGestures: Boolean, gestureNeedsGuard: Boolean, sdkInt: Int): Int =
        if (locked && blockGestures && gestureNeedsGuard && sdkInt >= 30) FLAG_TOUCH_EXPLORATION or FLAG_MULTI_FINGER else 0

    /** AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE. */
    const val FLAG_TOUCH_EXPLORATION = 0x00000004
    /** AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES. */
    const val FLAG_MULTI_FINGER = 0x00001000

    sealed interface AutoLockDecision {
        data object Arm : AutoLockDecision
        data object CancelArm : AutoLockDecision
        data object None : AutoLockDecision
    }

    /**
     * What to do when [foreground] comes to the front. Arms when a chosen app
     * appears while unlocked, unless it is the app the parent just unlocked
     * from ([suppressedPackage], cleared once another app has been in front).
     * Cancels a pending auto-arm if the parent leaves the app before it fires.
     */
    fun autoLockDecision(
        foreground: String,
        autoLockApps: Set<String>,
        locked: Boolean,
        arming: Boolean,
        armedPackage: String?,
        suppressedPackage: String?,
    ): AutoLockDecision = when {
        locked -> AutoLockDecision.None
        arming -> if (armedPackage != null && foreground != armedPackage) AutoLockDecision.CancelArm else AutoLockDecision.None
        foreground in autoLockApps && foreground != suppressedPackage -> AutoLockDecision.Arm
        else -> AutoLockDecision.None
    }

    /** Whether the guard should swallow a hardware key while locked. */
    fun consumeKey(key: com.gbhall.childlock.gesture.HardwareKey, blockKeys: Boolean, chordActive: Boolean): Boolean =
        when (key) {
            com.gbhall.childlock.gesture.HardwareKey.VOLUME_UP,
            com.gbhall.childlock.gesture.HardwareKey.VOLUME_DOWN -> blockKeys || chordActive
            com.gbhall.childlock.gesture.HardwareKey.BACK -> blockKeys
        }
}

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
    const val SHADE_FIGHT_WINDOW_MS = 20_000L
    const val MAX_SHADE_FIGHTS = 3

    /** A tall SystemUI system-type window is the notification shade (or quick settings). */
    fun isShadeWindow(isSystemType: Boolean, windowHeight: Int, screenHeight: Int, packageName: String?): Boolean =
        isSystemType &&
            packageName == SYSTEM_UI &&
            screenHeight > 0 &&
            windowHeight >= screenHeight * SHADE_MIN_HEIGHT_FRACTION

    /** Windows the system puts up over apps; fighting them only causes a loop. */
    fun isSystemDialogPackage(pkg: String): Boolean =
        pkg == "android" || pkg.contains("permissioncontroller") || pkg == "com.google.android.gms" ||
            pkg == "com.android.settings" || pkg.endsWith(".packageinstaller") || pkg == "com.android.vending"

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
        if (isSystemDialogPackage(foregroundPackage)) return RelaunchDecision.Skip("system dialog")
        if (dialerPackage != null && foregroundPackage == dialerPackage) return RelaunchDecision.Skip("phone call")
        if (foregroundPackage.contains("incallui")) return RelaunchDecision.Skip("phone call")
        if (keyguardLocked) return RelaunchDecision.Skip("keyguard")
        if (nowMs - lastRelaunchMs < RELAUNCH_DEBOUNCE_MS) return RelaunchDecision.Skip("debounce")
        return RelaunchDecision.Relaunch
    }

    /** Running tally of how often the notifications panel has been pulled down during one lock. */
    data class ShadeFights(val opens: Int = 0, val windowStartMs: Long = 0L, val lastDismissMs: Long = 0L)

    /**
     * Whether to close the notifications panel, and the tally to carry forward.
     *
     * The panel is closed for a child batting at the screen, but someone who
     * keeps pulling it down is the parent reaching for the Unlock button in
     * our own notification. After [MAX_SHADE_FIGHTS] tries in
     * [SHADE_FIGHT_WINDOW_MS] we stop fighting and let them through.
     */
    fun shadeDecision(nowMs: Long, state: ShadeFights): Pair<Boolean, ShadeFights> {
        if (nowMs - state.lastDismissMs < SHADE_DISMISS_DEBOUNCE_MS) return false to state
        val fresh = nowMs - state.windowStartMs > SHADE_FIGHT_WINDOW_MS
        val start = if (fresh) nowMs else state.windowStartMs
        val opens = (if (fresh) 0 else state.opens) + 1
        if (opens > MAX_SHADE_FIGHTS) return false to state.copy(opens = opens, windowStartMs = start)
        return true to ShadeFights(opens = opens, windowStartMs = start, lastDismissMs = nowMs)
    }

    /**
     * Accessibility-service flags to add while locked. Touch-exploration mode
     * plus multi-finger gestures is what stops one-finger home/back swipes at
     * the system level (screen readers rely on the same switch). It is only
     * safe with a volume gesture, because touch exploration turns the
     * overlay's touches into hover events, so the touch-based unlocks stop.
     */
    fun gestureBlockFlags(locked: Boolean, blockGestures: Boolean, gestureNeedsGuard: Boolean, sdkInt: Int): Int = when {
        !(locked && blockGestures && gestureNeedsGuard) -> 0
        // Multi-finger gestures need API 30; touch exploration, which is what
        // actually stops the swipes, has been there since long before minSdk.
        sdkInt >= 30 -> FLAG_TOUCH_EXPLORATION or FLAG_MULTI_FINGER
        else -> FLAG_TOUCH_EXPLORATION
    }

    /** AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE. */
    /** AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS; without it no volume press reaches us. */
    const val FLAG_REQUEST_FILTER_KEY_EVENTS = 0x00000020
    const val FLAG_TOUCH_EXPLORATION = 0x00000004
    /** AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES. */
    const val FLAG_MULTI_FINGER = 0x00001000

    /** Default trigger by package name only (no Context); AppCatalog.profile is the full version. */
    fun smartTrigger(packageName: String): com.gbhall.childlock.settings.AutoLockTrigger {
        com.gbhall.childlock.settings.AppCatalog.curated[packageName]?.let { return it.default }
        val p = packageName.lowercase()
        val call = listOf("whatsapp", "tachyon", "teams", "voip", "zoom", "skype", "signal", "telegram", "viber", "messenger", "orca", "dialer", "telecom", "discord", "webex", "jitsi")
        val video = listOf("youtube", "netflix", "iplayer", "disney", "primevideo", "amazon.avod", "twitch", "plex", "vlc", "mxtech", "itv", "channel4", "hulu", "hbo", "paramount", "peacock", "cbeebies", "pbskids", "nowtv", "skygo")
        return when {
            call.any { it in p } -> com.gbhall.childlock.settings.AutoLockTrigger.VIDEO_CALL
            video.any { it in p } -> com.gbhall.childlock.settings.AutoLockTrigger.FULLSCREEN_PLAYBACK
            else -> com.gbhall.childlock.settings.AutoLockTrigger.OPEN
        }
    }

    /** AudioManager.MODE_IN_CALL and MODE_IN_COMMUNICATION. */
    private const val MODE_IN_CALL = 2
    private const val MODE_IN_COMMUNICATION = 3

    /** Whether the moment a rule waits for has arrived, from signals the guard can read without screen content. */
    fun triggerSatisfied(
        trigger: com.gbhall.childlock.settings.AutoLockTrigger,
        audioMode: Int,
        mediaPlaying: Boolean,
        statusBarVisible: Boolean,
        cameraInUse: Boolean = false,
    ): Boolean = when (trigger) {
        com.gbhall.childlock.settings.AutoLockTrigger.OPEN -> true
        com.gbhall.childlock.settings.AutoLockTrigger.CALL -> inCall(audioMode)
        com.gbhall.childlock.settings.AutoLockTrigger.VIDEO_CALL -> inCall(audioMode) && cameraInUse
        com.gbhall.childlock.settings.AutoLockTrigger.VOICE_CALL -> inCall(audioMode) && !cameraInUse
        com.gbhall.childlock.settings.AutoLockTrigger.PLAYBACK -> mediaPlaying
        com.gbhall.childlock.settings.AutoLockTrigger.FULLSCREEN_PLAYBACK -> mediaPlaying && !statusBarVisible
    }

    private fun inCall(audioMode: Int) = audioMode == MODE_IN_CALL || audioMode == MODE_IN_COMMUNICATION

    /** The status bar is a thin system window pinned to the top edge. */
    fun isStatusBarWindow(isSystemType: Boolean, top: Int, height: Int, screenHeight: Int): Boolean =
        isSystemType && top <= 0 && height > 0 && height < screenHeight * 0.07f

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

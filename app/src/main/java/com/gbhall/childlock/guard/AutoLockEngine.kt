package com.gbhall.childlock.guard

import com.gbhall.childlock.settings.AutoLockTrigger

/**
 * Pure state machine for automatic locking, driven by foreground changes,
 * once-a-second condition polls and lock-state changes. No Android types.
 *
 * IDLE ─foreground chosen app─▶ WATCHING ─condition true─▶ ARMING ─▶ LOCKED
 * LOCKED ─unlock─▶ HOT (condition still true) ─false ×3─▶ REARMABLE ─true ×2─▶ ARMING
 * ARMING ─parent cancels─▶ DISARMED (stays until the app leaves the front)
 * any but LOCKED ─another app in front─▶ IDLE (LOCKED only changes the app it is over)
 */
class AutoLockEngine(private val listener: Listener) {
    interface Listener {
        fun requestArm(packageName: String)
        fun cancelArm()
    }

    enum class State { IDLE, WATCHING, ARMING, LOCKED, HOT, REARMABLE, DISARMED }

    var rules: Map<String, AutoLockTrigger> = emptyMap()
    var relockEnabled: Boolean = true

    var state: State = State.IDLE
        private set
    var currentPackage: String? = null
        private set
    private var trueStreak = 0
    private var falseStreak = 0

    /** Polls of peace owed to the parent after they deliberately unlocked. */
    private var graceLeft = 0

    /** Deliberate unlocks of this app since the parent last left it. */
    private var unlocksHere = 0

    val trigger: AutoLockTrigger? get() = currentPackage?.let { rules[it] }

    /** Whether the host should keep polling the condition. */
    val wantsTicks: Boolean get() = state == State.WATCHING || state == State.HOT || state == State.REARMABLE

    /** A launchable app or the home screen came to the front. Dialogs and keyboards must not be reported. */
    fun onForeground(packageName: String) {
        if (packageName == currentPackage) return
        if (state == State.ARMING) listener.cancelArm()
        currentPackage = packageName
        trueStreak = 0
        falseStreak = 0
        unlocksHere = 0
        graceLeft = 0
        state = when {
            // The lock is still on; only the app under it changed (the child
            // reached the home screen, the guard brought the app back). Watching
            // again here would send a second lock request into a running lock,
            // and the parent's eventual unlock would land in the wrong state.
            state == State.LOCKED -> State.LOCKED
            rules.containsKey(packageName) -> State.WATCHING
            else -> State.IDLE
        }
    }

    /** Condition poll for the current app: is its moment happening right now? */
    fun onTick(conditionTrue: Boolean) {
        if (conditionTrue) { trueStreak++; falseStreak = 0 } else { falseStreak++; trueStreak = 0 }
        if (graceLeft > 0) graceLeft--
        val pkg = currentPackage ?: return
        when (state) {
            State.WATCHING -> if (conditionTrue) arm(pkg)
            State.HOT -> if (falseStreak >= FALL_POLLS) state = State.REARMABLE
            State.REARMABLE ->
                if (graceLeft == 0 && trueStreak >= RISE_POLLS && relockEnabled && isRelockable(rules[pkg])) arm(pkg)
            else -> Unit
        }
    }

    fun onLocked() {
        state = State.LOCKED
    }

    /** The lock ended. [byParent] is a deliberate unlock or a cancelled countdown; anything else is IDLE. */
    fun onUnlocked(byParent: Boolean) {
        val pkg = currentPackage
        if (pkg == null || !rules.containsKey(pkg)) { state = State.IDLE; return }
        state = when {
            state == State.ARMING -> if (byParent) State.DISARMED else State.WATCHING
            state == State.LOCKED && relockEnabled && isRelockable(rules[pkg]) -> State.HOT
            else -> State.DISARMED
        }
        // Whatever the parent unlocked to do, they get time to do it.
        if (byParent) {
            graceLeft = GRACE_POLLS
            unlocksHere++
            // If they keep having to unlock, stop locking. Repeatedly
            // re-locking an app the parent is plainly trying to use is
            // indistinguishable, to them, from a lock that will not switch off.
            if (unlocksHere >= MAX_UNLOCKS_BEFORE_BACKING_OFF) state = State.DISARMED
        }
        trueStreak = 0
        falseStreak = 0
    }

    fun reset() {
        state = State.IDLE
        currentPackage = null
        trueStreak = 0
        falseStreak = 0
        graceLeft = 0
        unlocksHere = 0
    }

    private fun arm(pkg: String) {
        state = State.ARMING
        listener.requestArm(pkg)
    }

    companion object {
        /**
         * How long the moment must be over before it counts as over. A video
         * player showing its controls hides the status bar again within a few
         * seconds, and that must never be mistaken for leaving full screen:
         * doing so re-locks the phone seconds after the parent unlocked it,
         * which feels exactly like a lock that cannot be switched off.
         */
        const val FALL_POLLS = 12

        const val RISE_POLLS = 2

        /** No automatic re-lock at all for this long after a deliberate unlock. */
        const val GRACE_POLLS = 20

        /**
         * After this many deliberate unlocks without leaving the app, auto-lock
         * gives up on it until the parent goes somewhere else and comes back.
         */
        const val MAX_UNLOCKS_BEFORE_BACKING_OFF = 3

        /** Only moments that can end and happen again make sense to re-lock on. */
        fun isRelockable(t: AutoLockTrigger?): Boolean = when (t) {
            AutoLockTrigger.VIDEO_CALL, AutoLockTrigger.VOICE_CALL, AutoLockTrigger.CALL, AutoLockTrigger.FULLSCREEN_PLAYBACK -> true
            AutoLockTrigger.PLAYBACK, AutoLockTrigger.OPEN, null -> false
        }
    }
}

package com.gbhall.childlock.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.gesture.GestureEvent
import com.gbhall.childlock.gesture.HardwareKey
import com.gbhall.childlock.gesture.UnlockGesture
import com.gbhall.childlock.gesture.VolumeChordGesture
import com.gbhall.childlock.gesture.VolumeSequenceGesture
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

/**
 * Optional hardening. While unlocked it only remembers the foreground app and
 * listens (without consuming) for the volume pattern that arms the lock.
 * While locked it swallows back and volume keys, closes the notification
 * shade, brings the protected app back if the child leaves it, and recognises
 * the volume gesture that unlocks.
 */
class GuardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var settings = LockSettings()
    private var keyGesture: UnlockGesture? = null
    private var lastRelaunchMs = 0L
    private var lastShadeDismissMs = 0L
    private val launchable = HashMap<String, Boolean>()

    /** Key codes whose press we swallowed; their release must be swallowed too, whatever happened between. */
    private val consumedKeys = HashSet<Int>()

    /** Flags we last asked the system for, so tests and rebuilds can reason about them. */
    @Volatile
    var requestedFlags: Int = 0
        private set

    /** Auto-lock bookkeeping: the app we armed for, and the app not to re-arm for until it leaves. */
    private var autoArmedPackage: String? = null
    private var suppressedPackage: String? = null
    private var lastState: LockState = LockState.Unlocked

    private val stateListener: (LockState) -> Unit = { state ->
        val previous = lastState
        lastState = state
        if (state is LockState.Unlocked) {
            // Do not re-arm for the app the parent just unlocked from until it has left the front.
            if (previous is LockState.Locked) suppressedPackage = previous.protectedPackage
            autoArmedPackage = null
        }
        rebuildKeyGesture()
    }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rebuildKeyGesture() }

    private val tick = object : Runnable {
        override fun run() {
            val g = keyGesture ?: return
            g.onTick(SystemClock.uptimeMillis())
            if (g.wantsTicks) handler.postDelayed(this, TICK_MS)
        }
    }

    public override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        SettingsRepository.get(this).addChangeListener(settingsListener)
        LockController.addListener(stateListener)
        rebuildKeyGesture()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        LockController.removeListener(stateListener)
        SettingsRepository.get(this).removeChangeListener(settingsListener)
        keyGesture = null
        handler.removeCallbacks(tick)
        return super.onUnbind(intent)
    }

    /** Which key gesture applies right now depends on both settings and lock state. */
    private fun rebuildKeyGesture() {
        settings = SettingsRepository.get(this).load()
        handler.removeCallbacks(tick)
        val locked = LockController.isLocked
        applyServiceFlags(locked)
        keyGesture = when (settings.gesture) {
            GestureType.VOLUME_SEQUENCE ->
                VolumeSequenceGesture(settings.volumePattern, settings.volumeRepeats, ::onKeyGestureEvent)
            GestureType.VOLUME_CHORD ->
                if (locked) VolumeChordGesture(settings.holdMs, ::onKeyGestureEvent) else null
            GestureType.CORNER_HOLD, GestureType.BADGE_PIN -> null
        }
    }

    /**
     * Base flags always; touch-exploration + multi-finger only while locked with a
     * volume gesture, which is what blocks one-finger home/back swipes. Cleared
     * on unlock, unbind and reconnect so the phone can never be left in that mode.
     */
    private fun applyServiceFlags(locked: Boolean) {
        val extra = GuardPolicy.gestureBlockFlags(locked, settings.blockGestures, settings.gesture.needsGuard, Build.VERSION.SDK_INT)
        val wanted = BASE_FLAGS or extra
        if (wanted == requestedFlags && locked) return
        requestedFlags = wanted
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = (flags and (GuardPolicy.FLAG_TOUCH_EXPLORATION or GuardPolicy.FLAG_MULTI_FINGER).inv()) or wanted
        }
    }

    /** With multi-finger gestures on, a three-finger triple tap is the touch fallback to unlock. */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            gestureEvent.gestureId == GESTURE_3_FINGER_TRIPLE_TAP &&
            LockController.isLocked
        ) {
            LockController.unlock()
            return true
        }
        return false
    }

    private fun onKeyGestureEvent(event: GestureEvent) {
        if (event != GestureEvent.Unlocked) return
        // Decide now, act later: the key callback must return immediately.
        val stateAtPress = LockController.state
        handler.post { toggleLock(stateAtPress) }
    }

    private fun toggleLock(stateAtPress: LockState) {
        when (stateAtPress) {
            is LockState.Locked -> LockController.unlock()
            is LockState.Arming -> LockController.unlock() // the pattern during a countdown cancels it
            LockState.Unlocked -> {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, R.string.toast_no_overlay_permission, Toast.LENGTH_SHORT).show()
                } else if (!LockController.requestLock(this, ForegroundTracker.lastApp, 0)) {
                    Toast.makeText(this, R.string.toast_lock_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && !isIgnoredForeground(pkg)) {
                if (isLaunchable(pkg)) ForegroundTracker.lastApp = pkg
                // Home screens often have no launcher activity, yet going home must
                // still cancel a countdown and clear re-arm suppression.
                onForegroundApp(pkg)
            }
        }
        val locked = LockController.state as? LockState.Locked ?: return
        if (settings.blockShade && isShadeOpen()) {
            dismissShade()
            return
        }
        if (settings.relaunchApp) maybeRelaunch(locked.protectedPackage)
    }

    /**
     * Must answer fast: the system gives us 500 ms, then treats the key as not
     * consumed. So no heavy work happens here; gesture results are posted.
     *
     * Only presses (and their auto-repeats) are ever consumed. A release always
     * passes through: a stray release is harmless, but a swallowed release
     * leaves the input dispatcher believing the key is still held, and it keeps
     * synthesising repeats straight to the app, which is how the volume drained.
     */
    private fun onForegroundApp(pkg: String) {
        val state = LockController.state
        val decision = GuardPolicy.autoLockDecision(
            foreground = pkg,
            autoLockApps = settings.autoLockApps,
            locked = state is LockState.Locked,
            arming = state is LockState.Arming,
            armedPackage = autoArmedPackage,
            suppressedPackage = suppressedPackage,
        )
        if (pkg != suppressedPackage) suppressedPackage = null
        when (decision) {
            GuardPolicy.AutoLockDecision.Arm -> {
                if (Settings.canDrawOverlays(this) &&
                    LockController.requestLock(this, pkg, settings.autoLockDelaySec * 1000L)
                ) {
                    autoArmedPackage = pkg
                }
            }
            GuardPolicy.AutoLockDecision.CancelArm -> {
                autoArmedPackage = null
                LockController.unlock()
            }
            GuardPolicy.AutoLockDecision.None -> Unit
        }
    }

    public override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> HardwareKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> HardwareKey.VOLUME_DOWN
            KeyEvent.KEYCODE_BACK -> HardwareKey.BACK
            else -> return false
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount > 0) {
            // Auto-repeat of a held key: same fate as its first press, never fed to gestures.
            return event.keyCode in consumedKeys
        }
        keyGesture?.let { g ->
            g.onKey(key, down, event.eventTime)
            handler.removeCallbacks(tick)
            if (g.wantsTicks) handler.postDelayed(tick, TICK_MS)
        }
        if (!down) {
            consumedKeys.remove(event.keyCode)
            return false
        }
        // While unlocked the phone must behave normally: observe only, never consume.
        val consume = LockController.isLocked && GuardPolicy.consumeKey(key, settings.blockKeys, chordActive = keyGesture != null)
        if (consume) consumedKeys.add(event.keyCode) else consumedKeys.remove(event.keyCode)
        return consume
    }

    override fun onInterrupt() = Unit

    private val imePackages: Set<String> by lazy {
        try {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Windows that come and go without meaning the user moved to another app. */
    private fun isIgnoredForeground(pkg: String): Boolean =
        pkg == packageName || pkg == GuardPolicy.SYSTEM_UI || pkg in imePackages

    private fun isLaunchable(pkg: String): Boolean = launchable.getOrPut(pkg) {
        packageManager.getLaunchIntentForPackage(pkg) != null
    }

    private fun isShadeOpen(): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val bounds = android.graphics.Rect()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            w.getBoundsInScreen(bounds)
            // Cheap size check first; only then pay for the window root.
            if (!GuardPolicy.isShadeWindow(true, bounds.height(), screenHeight, GuardPolicy.SYSTEM_UI)) continue
            val pkg = w.root?.packageName?.toString()
            if (GuardPolicy.isShadeWindow(true, bounds.height(), screenHeight, pkg)) return true
        }
        return false
    }

    private fun dismissShade() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShadeDismissMs < GuardPolicy.SHADE_DISMISS_DEBOUNCE_MS) return
        lastShadeDismissMs = now
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun maybeRelaunch(protectedPackage: String?) {
        if (protectedPackage == null) return
        val now = SystemClock.uptimeMillis()
        val decision = GuardPolicy.relaunchDecision(
            protectedPackage = protectedPackage,
            foregroundPackage = rootInActiveWindow?.packageName?.toString(),
            selfPackage = packageName,
            dialerPackage = defaultDialer(),
            keyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            nowMs = now,
            lastRelaunchMs = lastRelaunchMs,
        )
        if (decision !is GuardPolicy.RelaunchDecision.Relaunch) return
        lastRelaunchMs = now
        val intent = packageManager.getLaunchIntentForPackage(protectedPackage) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Relaunch of $protectedPackage failed", e)
        }
    }

    private fun defaultDialer(): String? =
        try { getSystemService(TelecomManager::class.java)?.defaultDialerPackage } catch (e: Exception) { null }

    companion object {
        private const val TAG = "GuardService"
        private const val TICK_MS = 33L
        private const val BASE_FLAGS =
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        @Volatile
        var isConnected: Boolean = false
            private set

        private fun component(context: Context) = ComponentName(context, GuardAccessibilityService::class.java)

        /**
         * True when Android's floating accessibility button is pointed at this
         * service. The app never asks for it; the Settings UI offers it as a
         * shortcut when the service is enabled. It is the only icon Android adds.
         */
        fun isShortcutButtonOn(context: Context): Boolean {
            val flat = component(context).flattenToString()
            val targets = Settings.Secure.getString(context.contentResolver, "accessibility_button_targets") ?: return false
            return targets.split(':').any { it.equals(flat, ignoreCase = true) }
        }

        /** Opens accessibility settings scrolled to (and highlighting) this service where the OS supports it. */
        fun settingsIntent(context: Context): Intent {
            val flat = component(context).flattenToString()
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                putExtra(EXTRA_FRAGMENT_ARG_KEY, flat)
                putExtra(EXTRA_SHOW_FRAGMENT_ARGS, android.os.Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, flat) })
            }
        }

        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

        /** True when the user has enabled this service in accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, GuardAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}

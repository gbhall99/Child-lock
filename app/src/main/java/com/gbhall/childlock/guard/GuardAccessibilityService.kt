package com.gbhall.childlock.guard

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.accessibility.AccessibilityNodeInfo
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
 * The "Child Lock helper". While unlocked it only remembers the foreground app,
 * listens (without consuming) for the volume pattern that locks, and runs the
 * auto-lock engine. While locked it swallows back and volume keys, blocks
 * system gestures, closes the notifications panel, brings the protected app
 * back, recognises the volume gesture that unlocks, and optionally taps
 * "Skip ad" buttons in the app the child is watching.
 */
class GuardAccessibilityService : AccessibilityService(), AutoLockEngine.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var settings = LockSettings()
    private var keyGesture: UnlockGesture? = null
    private var lastRelaunchMs = 0L
    private var shadeFights = GuardPolicy.ShadeFights()
    private val launchable = HashMap<String, Boolean>()
    private val consumedKeys = HashSet<Int>()
    private var lastState: LockState = LockState.Unlocked
    private var screenOn = true

    /**
     * Flags we last asked the system for, so tests and rebuilds can reason about them.
     * They start at what the manifest already declares, so a run that needs nothing
     * extra never calls setServiceInfo at all.
     */
    @Volatile
    var requestedFlags: Int = BASE_FLAGS
        private set
    private var requestedEvents: Int = MANIFEST_EVENTS

    // ---- auto-lock ------------------------------------------------------------

    internal val engine = AutoLockEngine(this)

    /** Cameras some app currently holds open; a video call shows up here. Visible for tests. */
    internal val camerasInUse = HashSet<String>()
    private var cameraCallback: android.hardware.camera2.CameraManager.AvailabilityCallback? = null

    private val autoLockTick = object : Runnable {
        override fun run() {
            if (!screenOn) return
            val trigger = engine.trigger
            if (trigger != null) {
                engine.onTick(GuardPolicy.triggerSatisfied(trigger, audioMode(), mediaPlaying(), statusBarVisible(), camerasInUse.isNotEmpty()))
            }
            if (engine.wantsTicks) handler.postDelayed(this, WATCH_MS)
        }
    }

    private fun ensureAutoLockTicking() {
        handler.removeCallbacks(autoLockTick)
        if (engine.wantsTicks && screenOn) handler.post(autoLockTick)
    }

    override fun requestArm(packageName: String) {
        if (!Settings.canDrawOverlays(this) || !LockController.requestLock(this, packageName, settings.autoLockDelaySec * 1000L)) {
            engine.onUnlocked(byParent = false)
        }
    }

    override fun cancelArm() {
        if (LockController.state is LockState.Arming) LockController.unlock()
    }

    // ---- lifecycle ----------------------------------------------------------

    private val stateListener: (LockState) -> Unit = { state ->
        val previous = lastState
        lastState = state
        when (state) {
            is LockState.Locked -> {
                shadeFights = GuardPolicy.ShadeFights()
                engine.onLocked()
            }
            is LockState.Unlocked -> if (previous !is LockState.Unlocked) engine.onUnlocked(byParent = previous is LockState.Locked)
            is LockState.Arming -> Unit
        }
        rebuildKeyGesture()
        ensureAutoLockTicking()
    }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rebuildKeyGesture() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            ensureAutoLockTicking()
        }
    }

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
        keyToolActive = keyFilteringToolActive()
        // Never inherit a stored touch-exploration request from a previous run.
        applyServiceFlags(locked = false)
        startCameraTracking()
        try {
            val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screen receiver not registered", e)
        }
        SettingsRepository.get(this).addChangeListener(settingsListener)
        LockController.addListener(stateListener)
        rebuildKeyGesture()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        keyToolActive = false
        // Leaving the phone in explore-by-touch would strand the user.
        try {
            applyServiceFlags(locked = false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear service flags", e)
        }
        LockController.removeListener(stateListener)
        SettingsRepository.get(this).removeChangeListener(settingsListener)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) { Log.w(TAG, "Screen receiver already gone") }
        keyGesture = null
        handler.removeCallbacks(tick)
        handler.removeCallbacks(autoLockTick)
        engine.reset()
        stopCameraTracking()
        return super.onUnbind(intent)
    }

    /** Which key gesture applies right now depends on both settings and lock state. */
    private fun rebuildKeyGesture() {
        settings = SettingsRepository.get(this).load()
        keyToolActive = keyFilteringToolActive()
        engine.rules = settings.autoLockRules
        engine.relockEnabled = settings.relockSameApp
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
     * volume gesture (blocks one-finger home/back swipes); content-change events
     * and view ids only while skip-ad tapping can actually run.
     */
    private fun applyServiceFlags(locked: Boolean) {
        val extra = if (otherScreenReaderActive()) {
            0 // TalkBack and friends own explore-by-touch; competing breaks both.
        } else {
            GuardPolicy.gestureBlockFlags(locked, settings.blockGestures, settings.gesture.needsGuard, Build.VERSION.SDK_INT)
        }
        val wanted = BASE_FLAGS or extra or (if (skipAdsActive()) AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS else 0)
        val events = MANIFEST_EVENTS or (if (skipAdsActive()) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
        if (wanted == requestedFlags && events == requestedEvents) return
        // Mutate the info the system granted us where we can: it carries the manifest's
        // settings, and only the handful of properties in MUTABLE_FLAGS are ours to change.
        val info = serviceInfo ?: AccessibilityServiceInfo().apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
        info.eventTypes = events
        info.flags = (info.flags and MUTABLE_FLAGS.inv()) or wanted
        serviceInfo = info
        // Only record what actually took effect.
        requestedFlags = wanted
        requestedEvents = events
    }

    /** With multi-finger gestures on, a three-finger triple tap is the touch fallback to unlock. */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            gestureEvent.gestureId == GESTURE_3_FINGER_TRIPLE_TAP &&
            LockController.isLocked &&
            !otherScreenReaderActive()
        ) {
            LockController.unlock()
            return true
        }
        return false
    }

    private fun onKeyGestureEvent(event: GestureEvent) {
        if (event != GestureEvent.Unlocked) return
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

    // ---- events -------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> if (pkg != null) onWindowChanged(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (pkg != null) onContentChanged(pkg)
        }
        val locked = LockController.state as? LockState.Locked ?: return
        if (keyguardLocked()) return // the device lock screen is the user's, not ours
        // Window changes are the only events worth this cost; content churn is not.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (settings.blockShade && isShadeOpen()) {
            dismissShade()
            return
        }
        if (settings.relaunchApp) maybeRelaunch(locked.protectedPackage)
    }

    /** Only real apps and the home screen count as "the user moved"; dialogs, keyboards and SystemUI do not. */
    private fun onWindowChanged(pkg: String) {
        if (isIgnoredForeground(pkg)) return
        val app = isLaunchable(pkg)
        val home = isHome(pkg)
        if (!app && !home) return // a system dialog or a transient window over the app
        if (app) ForegroundTracker.lastApp = pkg
        engine.onForeground(pkg)
        ensureAutoLockTicking()
    }

    // ---- skip ads -------------------------------------------------------------

    private var lastScanMs = 0L
    private var lastClickMs = 0L
    private val clickTimes = ArrayDeque<Long>()
    private var scanFailures = 0

    private fun skipAdsActive(): Boolean {
        if (!SkipAdFeature.AVAILABLE) return false
        val locked = LockController.state as? LockState.Locked ?: return false
        val pkg = locked.protectedPackage ?: return false
        return settings.skipAds && SkipAdFeature.isSupported(pkg) && scanFailures < 3
    }

    private fun onContentChanged(pkg: String) {
        if (!skipAdsActive()) return
        val locked = LockController.state as? LockState.Locked ?: return
        if (pkg != locked.protectedPackage) return
        val now = SystemClock.uptimeMillis()
        if (now - lastScanMs < SCAN_MS || now - lastClickMs < CLICK_COOLDOWN_MS) return
        while (clickTimes.isNotEmpty() && now - clickTimes.first() > 60_000) clickTimes.removeFirst()
        if (clickTimes.size >= MAX_CLICKS_PER_MINUTE) return
        lastScanMs = now
        try {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != pkg) return
            val bounds = windowBounds()
            if (SkipAdFeature.clickSkip(root, pkg, bounds.width(), bounds.height())) {
                lastClickMs = now
                clickTimes.addLast(now)
            }
            scanFailures = 0
        } catch (e: Exception) {
            scanFailures++
            Log.w(TAG, "Skip-ad scan failed", e)
        }
    }

    // ---- keys -----------------------------------------------------------------

    /**
     * Must answer fast: the system gives us 500 ms, then treats the key as not
     * consumed. So no heavy work happens here; gesture results are posted.
     * Only presses (and their auto-repeats) are ever consumed; a release always
     * passes through, otherwise the input dispatcher believes the key is held.
     */
    public override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> HardwareKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> HardwareKey.VOLUME_DOWN
            KeyEvent.KEYCODE_BACK -> HardwareKey.BACK
            else -> return false
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        // On the device lock screen the keys belong to the user (alarms, the PIN pad).
        if (keyguardLocked()) return false
        if (down && event.repeatCount > 0) return event.keyCode in consumedKeys
        keyGesture?.let { g ->
            g.onKey(key, down, event.eventTime)
            handler.removeCallbacks(tick)
            if (g.wantsTicks) handler.postDelayed(tick, TICK_MS)
        }
        if (!down) {
            consumedKeys.remove(event.keyCode)
            return false
        }
        val consume = LockController.isLocked && GuardPolicy.consumeKey(key, settings.blockKeys, chordActive = keyGesture != null)
        if (consume) consumedKeys.add(event.keyCode) else consumedKeys.remove(event.keyCode)
        return consume
    }

    override fun onInterrupt() = Unit

    // ---- signals ----------------------------------------------------------------

    private fun startCameraTracking() {
        if (cameraCallback != null) return
        val cm = getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return
        val cb = object : android.hardware.camera2.CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) { camerasInUse.remove(cameraId) }
            override fun onCameraUnavailable(cameraId: String) { camerasInUse.add(cameraId) }
        }
        try {
            cm.registerAvailabilityCallback(cb, handler)
            cameraCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "Camera availability not tracked", e)
        }
    }

    private fun stopCameraTracking() {
        val cb = cameraCallback ?: return
        cameraCallback = null
        try {
            getSystemService(android.hardware.camera2.CameraManager::class.java)?.unregisterAvailabilityCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "Camera callback already gone", e)
        }
        camerasInUse.clear()
    }

    /** The window's own bounds. A Service's displayMetrics is wrong in split screen and on foldables. */
    private fun windowBounds(): android.graphics.Rect =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Rect(getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds)
            } else {
                val dm = resources.displayMetrics
                android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
            }
        } catch (e: Exception) {
            val dm = resources.displayMetrics
            android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }

    private fun keyguardLocked(): Boolean =
        try {
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        } catch (e: Exception) {
            false
        }

    /**
     * Another enabled accessibility tool is filtering key events. Switch Access
     * commonly maps physical switches to the volume keys, so consuming them
     * would take away the person's only means of operating the phone.
     */
    internal fun keyFilteringToolActive(): Boolean =
        try {
            val am = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            val mine = packageName
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any {
                    it.resolveInfo?.serviceInfo?.packageName != mine &&
                        (it.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
                } == true
        } catch (e: Exception) {
            false
        }

    /**
     * True when some *other* tool is already exploring by touch. Once we ask for
     * touch exploration ourselves the system reports it as on, so asking the
     * question naively makes us stand down from our own request a moment after
     * making it, and the flags flip back and forth for as long as the lock lasts.
     */
    internal fun otherScreenReaderActive(): Boolean {
        val on = try {
            getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        } catch (e: Exception) {
            return false
        }
        return on && (requestedFlags and GuardPolicy.FLAG_TOUCH_EXPLORATION) == 0
    }

    private fun audioMode(): Int =
        try { getSystemService(android.media.AudioManager::class.java)?.mode ?: 0 } catch (e: Exception) { 0 }

    private fun mediaPlaying(): Boolean =
        try { getSystemService(android.media.AudioManager::class.java)?.isMusicActive == true } catch (e: Exception) { false }

    private fun statusBarVisible(): Boolean {
        val screenHeight = windowBounds().height()
        val bounds = android.graphics.Rect()
        return try {
            windows.any { w ->
                w.getBoundsInScreen(bounds)
                GuardPolicy.isStatusBarWindow(w.type == AccessibilityWindowInfo.TYPE_SYSTEM, bounds.top, bounds.height(), screenHeight)
            }
        } catch (e: Exception) {
            true
        }
    }

    private var imePackages: Set<String> = emptySet()
    private var imeCheckedMs = 0L

    private fun imePackages(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - imeCheckedMs > 60_000) {
            imeCheckedMs = now
            imePackages = try {
                getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        return imePackages
    }

    private var homePackagesCache: Set<String>? = null
    private var homeCheckedMs = 0L

    private val homePackages: Set<String>
        get() {
            val now = SystemClock.uptimeMillis()
            val cached = homePackagesCache
            if (cached != null && now - homeCheckedMs < CACHE_MS) return cached
            homeCheckedMs = now
            return loadHomePackages().also { homePackagesCache = it }
        }

    private fun loadHomePackages(): Set<String> {
        try {
            @Suppress("DEPRECATION")
            return packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            return emptySet()
        }
    }

    private fun isHome(pkg: String) = pkg in homePackages

    private fun isIgnoredForeground(pkg: String): Boolean =
        pkg == packageName || pkg == GuardPolicy.SYSTEM_UI || pkg in imePackages()

    private fun isLaunchable(pkg: String): Boolean {
        // Bounded so a long session cannot grow it without limit, and cheap to rebuild.
        if (launchable.size > MAX_CACHED_PACKAGES) launchable.clear()
        return launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }
    }

    private fun isShadeOpen(): Boolean {
        val screenHeight = windowBounds().height()
        val bounds = android.graphics.Rect()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            w.getBoundsInScreen(bounds)
            if (!GuardPolicy.isShadeWindow(true, bounds.height(), screenHeight, GuardPolicy.SYSTEM_UI)) continue
            val pkg = w.root?.packageName?.toString()
            if (GuardPolicy.isShadeWindow(true, bounds.height(), screenHeight, pkg)) return true
        }
        return false
    }

    private fun dismissShade() {
        val (dismiss, next) = GuardPolicy.shadeDecision(SystemClock.uptimeMillis(), shadeFights)
        shadeFights = next
        if (!dismiss) return
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
        private const val WATCH_MS = 1000L
        private const val CACHE_MS = 5 * 60 * 1000L
        private const val MAX_CACHED_PACKAGES = 200
        private const val SCAN_MS = 500L
        private const val CLICK_COOLDOWN_MS = 3000L
        private const val MAX_CLICKS_PER_MINUTE = 6
        /** Event types the manifest already declares, so requesting them is a no-op. */
        private const val MANIFEST_EVENTS =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED

        /** The only flags we ever add or remove at runtime; everything else stays as granted. */
        private const val MUTABLE_FLAGS =
            GuardPolicy.FLAG_TOUCH_EXPLORATION or GuardPolicy.FLAG_MULTI_FINGER or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS

        private const val BASE_FLAGS =
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        @Volatile
        var isConnected: Boolean = false
            private set

        /** Set while the helper is running, so the UI can pre-flight a lock. */
        @Volatile
        var keyToolActive: Boolean = false
            private set

        private fun component(context: Context) = ComponentName(context, GuardAccessibilityService::class.java)

        /** True when Android's floating accessibility button is pointed at this service. */
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
            val expected = component(context).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}

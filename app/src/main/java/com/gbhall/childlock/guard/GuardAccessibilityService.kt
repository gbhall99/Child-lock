package com.gbhall.childlock.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.gbhall.childlock.gesture.GestureEvent
import com.gbhall.childlock.gesture.HardwareKey
import com.gbhall.childlock.gesture.VolumeChordGesture
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

/**
 * Optional hardening. Completely passive while unlocked (it only remembers the
 * foreground app). While locked it swallows back and volume keys, closes the
 * notification shade, and brings the protected app back if the child leaves it.
 */
class GuardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var settings = LockSettings()
    private var chord: VolumeChordGesture? = null
    private var lastRelaunchMs = 0L
    private var lastShadeDismissMs = 0L
    private val launchable = HashMap<String, Boolean>()

    private val stateListener: (LockState) -> Unit = { state ->
        if (state is LockState.Locked) onLocked() else onUnlocked()
    }

    private val tick = object : Runnable {
        override fun run() {
            val g = chord ?: return
            g.onTick(SystemClock.uptimeMillis())
            if (g.wantsTicks) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        isConnected = true
        LockController.addListener(stateListener)
        stateListener(LockController.state)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        LockController.removeListener(stateListener)
        onUnlocked()
        return super.onUnbind(intent)
    }

    private fun onLocked() {
        settings = SettingsRepository.get(this).load()
        chord = if (settings.gesture == GestureType.VOLUME_CHORD) {
            VolumeChordGesture(settings.holdMs) { event ->
                if (event == GestureEvent.Unlocked) LockController.unlock()
            }
        } else {
            null
        }
    }

    private fun onUnlocked() {
        chord = null
        handler.removeCallbacks(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName && isLaunchable(pkg)) ForegroundTracker.lastApp = pkg
        }
        val locked = LockController.state as? LockState.Locked ?: return
        if (settings.blockShade && isShadeOpen()) {
            dismissShade()
            return
        }
        if (settings.relaunchApp) maybeRelaunch(locked.protectedPackage)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!LockController.isLocked) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                chord?.let { g ->
                    val key = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) HardwareKey.VOLUME_UP else HardwareKey.VOLUME_DOWN
                    g.onKey(key, down, event.eventTime)
                    handler.removeCallbacks(tick)
                    if (g.wantsTicks) handler.postDelayed(tick, TICK_MS)
                }
                settings.blockKeys || chord != null
            }
            KeyEvent.KEYCODE_BACK -> {
                chord?.onKey(HardwareKey.BACK, down, event.eventTime)
                settings.blockKeys
            }
            else -> false
        }
    }

    override fun onInterrupt() = Unit

    private fun isLaunchable(pkg: String): Boolean = launchable.getOrPut(pkg) {
        packageManager.getLaunchIntentForPackage(pkg) != null
    }

    private fun isShadeOpen(): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val bounds = android.graphics.Rect()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            w.getBoundsInScreen(bounds)
            // The status bar is a thin system window; the open shade is a tall one.
            if (bounds.height() < screenHeight * SHADE_MIN_HEIGHT_FRACTION) continue
            val pkg = w.root?.packageName?.toString() ?: continue
            if (pkg == SYSTEM_UI) return true
        }
        return false
    }

    private fun dismissShade() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShadeDismissMs < SHADE_DISMISS_DEBOUNCE_MS) return
        lastShadeDismissMs = now
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun maybeRelaunch(protectedPackage: String?) {
        if (protectedPackage == null) return
        val foreground = rootInActiveWindow?.packageName?.toString() ?: return
        if (foreground == protectedPackage || foreground == packageName || foreground == SYSTEM_UI) return
        if (foreground == defaultDialer() || foreground.contains("incallui")) return // never fight a real phone call
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val now = SystemClock.uptimeMillis()
        if (now - lastRelaunchMs < RELAUNCH_DEBOUNCE_MS) return
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
        private const val SYSTEM_UI = "com.android.systemui"
        private const val TICK_MS = 33L
        private const val RELAUNCH_DEBOUNCE_MS = 1500L
        private const val SHADE_DISMISS_DEBOUNCE_MS = 300L
        private const val SHADE_MIN_HEIGHT_FRACTION = 0.4f

        @Volatile
        var isConnected: Boolean = false
            private set

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

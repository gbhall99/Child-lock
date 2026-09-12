package com.gbhall.childlock.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.gbhall.childlock.TestSupport
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** Builds a connected guard service with a fake chosen app and home screen installed. */
internal object GuardRig {
    const val APP = "com.example.call"
    const val HOME = "com.android.launcher"

    fun start(): GuardAccessibilityService {
        TestSupport.clearSettings()
        TestSupport.resetLock()
        ForegroundTracker.lastApp = null
        val pm = shadowOf(TestSupport.app.packageManager)
        val app = ComponentName(APP, "$APP.Main")
        pm.addActivityIfNotPresent(app)
        pm.addIntentFilterForActivity(app, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        val home = ComponentName(HOME, "$HOME.Home")
        pm.addActivityIfNotPresent(home)
        pm.addIntentFilterForActivity(home, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) })
        val service = Robolectric.buildService(GuardAccessibilityService::class.java).create().get()
        service.onServiceConnected()
        // Robolectric's uptime clock starts near zero, where the guard's
        // "not twice within a moment" debounces would swallow a first event.
        TestSupport.idle(GuardPolicy.RELAUNCH_DEBOUNCE_MS + GuardPolicy.SHADE_DISMISS_DEBOUNCE_MS)
        return service
    }

    fun windowEvent(pkg: String): AccessibilityEvent =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = pkg }

    fun windowsChanged(): AccessibilityEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED)

    /** The screen as the service measures it, so fake windows can be sized against it. */
    fun screen(context: Context): Rect {
        val fromMetrics = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect(context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        if (fromMetrics != null && !fromMetrics.isEmpty) return fromMetrics
        val dm = context.resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    fun window(type: Int, bounds: Rect, rootPackage: String?): AccessibilityWindowInfo {
        val w = AccessibilityWindowInfo.obtain()
        shadowOf(w).setType(type)
        shadowOf(w).setBoundsInScreen(bounds)
        if (rootPackage != null) {
            shadowOf(w).setRoot(AccessibilityNodeInfo.obtain().apply { packageName = rootPackage })
        }
        return w
    }

    /** The open notification shade: a SystemUI system window covering most of the screen. */
    fun shade(service: AccessibilityService): AccessibilityWindowInfo {
        val s = screen(service)
        return window(AccessibilityWindowInfo.TYPE_SYSTEM, Rect(0, 0, s.width(), s.height()), GuardPolicy.SYSTEM_UI)
    }

    /** The status bar: a thin SystemUI system window pinned to the top. */
    fun statusBar(service: AccessibilityService): AccessibilityWindowInfo {
        val s = screen(service)
        return window(AccessibilityWindowInfo.TYPE_SYSTEM, Rect(0, 0, s.width(), (s.height() * 0.04f).toInt()), GuardPolicy.SYSTEM_UI)
    }

    fun setWindows(service: AccessibilityService, vararg windows: AccessibilityWindowInfo) =
        shadowOf(service).setWindows(windows.toList())

    fun globalActions(service: AccessibilityService): List<Int> = shadowOf(service).globalActionsPerformed

    fun nextLockRequest(): Intent? = shadowOf(TestSupport.app).nextStartedService
}

package com.gbhall.childlock.lock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import com.gbhall.childlock.ChildLockApp
import com.gbhall.childlock.R
import com.gbhall.childlock.settings.GestureText
import com.gbhall.childlock.settings.SettingsRepository
import com.gbhall.childlock.ui.MainActivity

/**
 * Foreground service that owns the overlay window. It starts on a lock
 * request, attaches the touch shield after the arm delay, and stops itself as
 * soon as LockController reports Unlocked.
 */
class LockOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: OverlayRoot? = null
    private var pendingAttach: Runnable? = null
    private val banner by lazy { BannerWindow(this) }
    private val stopAfterBanner = Runnable { stopSelf() }

    private var wasLocked = false

    private val stateListener: (LockState) -> Unit = { state ->
        if (state is LockState.Locked) wasLocked = true
        if (state is LockState.Unlocked) {
            teardown()
            if (wasLocked) {
                // Touch is already free (overlay gone); keep the process alive just
                // long enough for the OFF banner to be seen.
                banner.show(getString(R.string.banner_off), on = false)
                handler.postDelayed(stopAfterBanner, BannerWindow.DURATION_MS + 100)
            } else {
                stopSelf()
            }
            wasLocked = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        startForegroundCompat(getString(R.string.notif_arming))
        LockController.addListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOCK -> handleLock(
                intent.getStringExtra(EXTRA_PACKAGE),
                intent.getLongExtra(EXTRA_DELAY_MS, 0L).coerceAtLeast(0L),
            )
            ACTION_UNLOCK -> LockController.unlock()
            else -> if (LockController.state is LockState.Unlocked) stopSelf()
        }
        // Never restart on our own after being killed: a restarted service
        // would have no lock state and would only confuse the parent.
        return START_NOT_STICKY
    }

    private fun handleLock(protectedPackage: String?, delayMs: Long) {
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_no_overlay_permission)
            abort()
            return
        }
        if (overlay != null) return // already locked
        pendingAttach?.let(handler::removeCallbacks)

        val lockAt = SystemClock.uptimeMillis() + delayMs
        LockController.set(LockState.Arming(lockAt, protectedPackage))
        if (delayMs > 0) {
            val seconds = ((delayMs + 999) / 1000).toInt()
            toast(getString(R.string.toast_locking_in, seconds))
            updateNotification(getString(R.string.notif_locking_in, seconds))
            val r = Runnable { attach(protectedPackage) }
            pendingAttach = r
            handler.postDelayed(r, delayMs)
        } else {
            attach(protectedPackage)
        }
    }

    private fun attach(protectedPackage: String?) {
        pendingAttach = null
        if (LockController.state !is LockState.Arming) return // cancelled meanwhile
        val settings = SettingsRepository.get(this).load()
        val root = OverlayRoot(this, settings, onUnlock = { LockController.unlock() })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (settings.keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "ChildLock"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
            }
        }
        try {
            windowManager.addView(root, params)
            overlay = root
            LockController.set(LockState.Locked(protectedPackage, SystemClock.uptimeMillis()))
            updateNotification(getString(R.string.notif_locked, GestureText.unlockHint(this, settings)))
            banner.show(getString(R.string.banner_on), on = true)
        } catch (e: Exception) {
            // Half-locking is worse than not locking: fail loudly and stay unlocked.
            Log.e(TAG, "Could not attach overlay", e)
            toast(R.string.toast_lock_failed)
            abort()
        }
    }

    /** Give up: state back to Unlocked (even if it never left), overlay gone, service stopped. */
    private fun abort() {
        LockController.unlock()
        teardown()
        stopSelf()
    }

    private fun teardown() {
        pendingAttach?.let(handler::removeCallbacks)
        pendingAttach = null
        overlay?.let { view ->
            view.dispose()
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: Exception) {
                Log.w(TAG, "Overlay already gone", e)
            }
        }
        overlay = null
    }

    override fun onDestroy() {
        LockController.removeListener(stateListener)
        handler.removeCallbacks(stopAfterBanner)
        banner.dismiss()
        teardown()
        if (LockController.state !is LockState.Unlocked) LockController.set(LockState.Unlocked)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, ChildLockApp.CHANNEL_LOCK)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun toast(resId: Int) = toast(getString(resId))
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "LockOverlayService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_LOCK = "com.gbhall.childlock.action.LOCK"
        const val ACTION_UNLOCK = "com.gbhall.childlock.action.UNLOCK"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_DELAY_MS = "delay_ms"

        /** Type-safe insets helper shared with the views. */
        fun systemInsets(insets: WindowInsets?): IntArray {
            if (insets == null) return intArrayOf(0, 0, 0, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                intArrayOf(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                intArrayOf(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
        }
    }
}

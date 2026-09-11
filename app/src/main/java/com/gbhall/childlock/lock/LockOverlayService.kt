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
    private var latestStartId = 0

    /** Only stop if nothing has locked again in the meantime. */
    private val stopAfterBanner = Runnable {
        if (LockController.state is LockState.Unlocked) stopSelfResult(latestStartId)
    }

    /** Safety net: a lock never outlives this, so a parent can never be stranded. */
    private val maxDurationStop = Runnable {
        if (LockController.isLocked) {
            Log.i(TAG, "Session over; unlocking")
            LockController.unlock()
        }
    }

    /**
     * A ringing or connected phone call must never be blocked. The audio mode
     * is the signal every dialer sets, and needs no telephony permission.
     */
    private val callWatch = object : Runnable {
        override fun run() {
            if (!LockController.isLocked) return
            val mode = try {
                getSystemService(android.media.AudioManager::class.java)?.mode ?: 0
            } catch (e: Exception) {
                0
            }
            if (mode == android.media.AudioManager.MODE_RINGTONE || mode == android.media.AudioManager.MODE_IN_CALL) {
                Log.i(TAG, "Phone call in progress; unlocking so it can be answered")
                LockController.unlock()
                return
            }
            handler.postDelayed(this, CALL_WATCH_MS)
        }
    }

    private var wasLocked = false

    private val stateListener: (LockState) -> Unit = { state ->
        if (state is LockState.Locked) wasLocked = true
        if (state is LockState.Unlocked) {
            teardown()
            handler.removeCallbacks(maxDurationStop)
            handler.removeCallbacks(callWatch)
            if (wasLocked) {
                // Touch is already free (overlay gone); keep the process alive just
                // long enough for the OFF banner to be seen.
                banner.show(BannerWindow.Kind.OFF, getString(R.string.banner_off_detail))
                handler.postDelayed(stopAfterBanner, BannerWindow.DURATION_MS + 100)
            } else {
                stopSelfResult(latestStartId)
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
        latestStartId = startId
        when (intent?.action) {
            ACTION_LOCK -> handleLock(
                intent.getStringExtra(EXTRA_PACKAGE),
                intent.getLongExtra(EXTRA_DELAY_MS, 0L).coerceAtLeast(0L),
                intent.getBooleanExtra(EXTRA_REHEARSAL, false),
            )
            ACTION_UNLOCK -> onUnlockAction()
            else -> if (LockController.state is LockState.Unlocked) stopSelf()
        }
        // Never restart on our own after being killed: a restarted service
        // would have no lock state and would only confuse the parent.
        return START_NOT_STICKY
    }

    private var rehearsal = false

    private fun handleLock(protectedPackage: String?, delayMs: Long, isRehearsal: Boolean = false) {
        rehearsal = isRehearsal
        // A lock arriving during the OFF banner must cancel the pending stop.
        handler.removeCallbacks(stopAfterBanner)
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
            banner.show(BannerWindow.Kind.ARMING, getString(R.string.banner_arming_detail, seconds))
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
            // The topmost window's requested orientation wins: freezing it here keeps a
            // full-screen video in the orientation it had when the lock engaged.
            if (settings.keepOrientation) {
                screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
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
            updateNotification(notificationText())
            banner.show(BannerWindow.Kind.ON, GestureText.unlockShort(this, settings))
            handler.removeCallbacks(maxDurationStop)
            // The parent's own timer if they set one, and the hard cap regardless.
            val sessionMs = settings.sessionMinutes.takeIf { it > 0 }?.times(60_000L) ?: MAX_LOCK_MS
            // A practice run always ends by itself, so trying it out can never strand anyone.
            val cap = if (rehearsal) REHEARSAL_MS else minOf(sessionMs, MAX_LOCK_MS)
            handler.postDelayed(maxDurationStop, cap)
            handler.removeCallbacks(callWatch)
            handler.postDelayed(callWatch, CALL_WATCH_MS)
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
        handler.removeCallbacks(maxDurationStop)
        handler.removeCallbacks(callWatch)
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

    /** The locked notification's body: how to get out, from current settings. */
    private fun notificationText(): String {
        val s = SettingsRepository.get(this).load()
        return getString(
            R.string.notif_locked,
            GestureText.unlockHint(this, s) + " " + GestureText.fallbackHint(this, s),
        )
    }

    private fun updateNotification(text: String) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * The notification's Unlock button needs two taps, and the second one must
     * come at least [UNLOCK_ARM_MS] after the first.
     *
     * The button exists so a parent whose gesture is not being recognised is
     * never trapped. But the notifications panel is reachable by anyone: the
     * guard stops fighting it after a few pull-downs, so a child who keeps
     * swiping down gets a panel that stays open. One tap there would have been
     * the whole lock. Mashing produces taps milliseconds apart, which the arming
     * delay rejects; a parent reading "Tap again to unlock" takes longer than
     * that without trying.
     */
    private var unlockArmedAt = -1L

    private fun onUnlockAction() {
        val now = SystemClock.uptimeMillis()
        val armed = unlockArmedAt
        val elapsed = now - armed
        if (armed >= 0 && elapsed in UNLOCK_ARM_MS..UNLOCK_CONFIRM_WINDOW_MS) {
            unlockArmedAt = -1
            LockController.unlock()
            return
        }
        // Too fast counts as a fresh first tap rather than progress, so a child
        // drumming on the button never accumulates a confirmation.
        unlockArmedAt = now
        updateNotification(notificationText())
        handler.removeCallbacks(disarmUnlock)
        handler.postDelayed(disarmUnlock, UNLOCK_CONFIRM_WINDOW_MS)
    }

    private val disarmUnlock = Runnable {
        if (unlockArmedAt >= 0) {
            unlockArmedAt = -1
            if (LockController.isLocked) updateNotification(notificationText())
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val unlock = PendingIntent.getService(
            this, 1,
            Intent(this, LockOverlayService::class.java).setAction(ACTION_UNLOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, ChildLockApp.CHANNEL_LOCK)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_lock_open),
                    getString(
                        if (unlockArmedAt >= 0) R.string.notif_unlock_confirm else R.string.notif_unlock_action,
                    ),
                    unlock,
                ).build(),
            )
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            // Not on the lock screen: the unlock button must need the phone unlocked first.
            .setVisibility(Notification.VISIBILITY_SECRET)
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

        /** No lock outlives this. The parent is never stranded, whatever else fails. */
        const val MAX_LOCK_MS = 90 * 60 * 1000L
        private const val CALL_WATCH_MS = 1000L

        /** A practice lock releases itself after this long, whatever happens. */
        const val REHEARSAL_MS = 60_000L
        private const val NOTIFICATION_ID = 1
        const val ACTION_LOCK = "com.gbhall.childlock.action.LOCK"
        const val ACTION_UNLOCK = "com.gbhall.childlock.action.UNLOCK"

        /** A second tap sooner than this is drumming, not a decision. */
        const val UNLOCK_ARM_MS = 900L

        /** ...and later than this is a new thought, so it starts over. */
        const val UNLOCK_CONFIRM_WINDOW_MS = 10_000L
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_DELAY_MS = "delay_ms"
        const val EXTRA_REHEARSAL = "rehearsal"

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

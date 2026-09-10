package com.gbhall.childlock.lock

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

sealed interface LockState {
    data object Unlocked : LockState

    /** Countdown before the overlay attaches, giving the parent time to return to the call. */
    data class Arming(val lockAtMs: Long, val protectedPackage: String?) : LockState

    data class Locked(val protectedPackage: String?, val sinceMs: Long) : LockState
}

/**
 * Single source of truth for lock state. It lives only in process memory:
 * a crash, a force stop or a reboot always leaves the phone unlocked, which is
 * the most important safety property of the whole app.
 */
object LockController {
    private const val TAG = "LockController"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(LockState) -> Unit>()

    @Volatile
    var state: LockState = LockState.Unlocked
        private set

    val isLocked: Boolean get() = state is LockState.Locked

    fun addListener(listener: (LockState) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (LockState) -> Unit) {
        listeners -= listener
    }

    /**
     * Asks the overlay service to lock, after [delayMs]. Returns false if the
     * system refused to start the service (Android 12+ background-start rules),
     * in which case nothing is locked and the caller should tell the user.
     */
    fun requestLock(
        context: Context,
        protectedPackage: String?,
        delayMs: Long,
        rehearsal: Boolean = false,
    ): Boolean {
        val intent = Intent(context, LockOverlayService::class.java)
            .setAction(LockOverlayService.ACTION_LOCK)
            .putExtra(LockOverlayService.EXTRA_PACKAGE, protectedPackage)
            .putExtra(LockOverlayService.EXTRA_DELAY_MS, delayMs)
            .putExtra(LockOverlayService.EXTRA_REHEARSAL, rehearsal)
        return try {
            context.startForegroundService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start lock service", e)
            false
        }
    }

    /** Releases the lock or cancels a pending arm. The service observes this and tears down. */
    fun unlock() = set(LockState.Unlocked)

    /**
     * State changes synchronously; listeners always hear about it on a later
     * main-loop turn. Callers such as the accessibility key filter must return
     * quickly, and listeners do heavy work (window add/remove, binder calls).
     */
    internal fun set(newState: LockState) {
        if (newState == state) return
        state = newState
        mainHandler.post { notify(newState) }
    }

    private fun notify(s: LockState) {
        for (l in listeners) l(s)
    }
}

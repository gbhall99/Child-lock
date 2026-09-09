package com.gbhall.childlock

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.SettingsRepository
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Shared helpers for Robolectric tests. */
object TestSupport {
    val app: Application get() = RuntimeEnvironment.getApplication()

    /** Advances the main looper (and SystemClock) so posted work and hold timers run. */
    fun idle(ms: Long = 0) {
        val looper = shadowOf(Looper.getMainLooper())
        if (ms > 0) looper.idleFor(Duration.ofMillis(ms)) else looper.idle()
    }

    /** Puts LockController back to Unlocked and drains the looper between tests. */
    fun resetLock() {
        LockController.unlock()
        idle()
        check(LockController.state == LockState.Unlocked)
    }

    fun clearSettings() {
        SettingsRepository.resetForTests()
        app.getSharedPreferences("childlock", 0).edit().clear().commit()
        SettingsRepository.get(app)
    }

    /** Builds a multi-pointer MotionEvent; [points] are (x, y) for pointer ids 0..n-1. */
    fun motion(action: Int, vararg points: Pair<Float, Float>, actionIndex: Int = 0): MotionEvent {
        val props = Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(points.size) { i -> MotionEvent.PointerCoords().apply { x = points[i].first; y = points[i].second; pressure = 1f; size = 1f } }
        val masked = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, masked, points.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0x1002, 0)
    }
}

package com.gbhall.childlock.gesture

/** Order of the volume presses that make up the pattern. */
enum class VolumePattern(val steps: List<HardwareKey>) {
    UP_THEN_DOWN(listOf(HardwareKey.VOLUME_UP, HardwareKey.VOLUME_DOWN)),
    DOWN_THEN_UP(listOf(HardwareKey.VOLUME_DOWN, HardwareKey.VOLUME_UP)),
}

/**
 * Volume-press pattern, e.g. up then down, optionally repeated.
 *
 * The pattern must be performed *cleanly*, which is what stops a child who is
 * mashing the rocker from stumbling into it:
 *
 *  - Any press that is not the expected next one aborts the attempt and starts
 *    a quiet period. Further presses during the quiet period restart it, so
 *    continuous mashing never gets a clean run at the pattern.
 *  - The final press must be held briefly. Random jabs are short.
 *  - Each press must follow the previous one within [stepWindowMs].
 *
 * Measured against a simulated child pressing the rocker every 150-700 ms,
 * the previous forgiving version unlocked within five seconds essentially
 * always; this version did not unlock in ten minutes, while a deliberate
 * adult still succeeds first time.
 */
class VolumeSequenceGesture(
    pattern: VolumePattern,
    repeats: Int,
    private val listener: GestureListener,
    private val stepWindowMs: Long = DEFAULT_STEP_WINDOW_MS,
    private val quietMs: Long = DEFAULT_QUIET_MS,
    private val finalHoldMs: Long = DEFAULT_FINAL_HOLD_MS,
) : UnlockGesture {
    private val expected: List<HardwareKey> = List(repeats.coerceIn(1, 3)) { pattern.steps }.flatten()
    private var index = 0
    private var lastMs = -1L
    private var blockedUntil = -1L
    private var finalDownMs = -1L

    /**
     * True while the last press is being held, so the host keeps ticking and
     * the unlock can complete on time alone. The release is a fast path, not
     * a requirement: a device that never delivers the release of a key we
     * consumed must not be able to trap the parent.
     */
    override val wantsTicks: Boolean get() = finalDownMs >= 0

    override fun onKey(key: HardwareKey, down: Boolean, timeMs: Long) {
        if (key == HardwareKey.BACK) {
            if (down) abort(timeMs)
            return
        }
        if (!down) {
            onRelease(timeMs)
            return
        }
        if (timeMs < blockedUntil) {
            // Still mashing: keep the quiet period open rather than letting it lapse.
            blockedUntil = timeMs + quietMs
            clear()
            return
        }
        if (index > 0 && timeMs - lastMs > stepWindowMs) clear()
        if (index < expected.size && key == expected[index]) {
            index++
            lastMs = timeMs
            if (index == expected.size) {
                finalDownMs = timeMs
                listener.onGestureEvent(GestureEvent.Progress(1f))
            } else {
                listener.onGestureEvent(GestureEvent.Progress(index.toFloat() / expected.size))
            }
        } else {
            abort(timeMs)
        }
    }

    /** Completes on time alone, so a missing release cannot strand anyone. */
    override fun onTick(nowMs: Long) {
        if (finalDownMs < 0) return
        if (nowMs - finalDownMs >= finalHoldMs) {
            clear()
            listener.onGestureEvent(GestureEvent.Unlocked)
        }
    }

    private fun onRelease(timeMs: Long) {
        if (index < expected.size) return
        val held = finalDownMs >= 0 && timeMs - finalDownMs >= finalHoldMs
        clear()
        if (held) listener.onGestureEvent(GestureEvent.Unlocked) else listener.onGestureEvent(GestureEvent.Reset)
    }

    private fun abort(timeMs: Long) {
        val had = index > 0
        clear()
        blockedUntil = timeMs + quietMs
        if (had) listener.onGestureEvent(GestureEvent.Reset)
    }

    private fun clear() {
        index = 0
        lastMs = -1
        finalDownMs = -1
    }

    override fun reset() {
        clear()
        blockedUntil = -1
    }

    companion object {
        const val DEFAULT_STEP_WINDOW_MS = 1200L
        const val DEFAULT_QUIET_MS = 1200L

        /** The last press is held for at least this long. Comfortable for an adult, unlike a jab. */
        const val DEFAULT_FINAL_HOLD_MS = 350L
    }
}

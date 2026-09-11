package com.gbhall.childlock.gesture

/** Order of the volume presses that make up the pattern. */
enum class VolumePattern(val steps: List<HardwareKey>) {
    UP_THEN_DOWN(listOf(HardwareKey.VOLUME_UP, HardwareKey.VOLUME_DOWN)),
    DOWN_THEN_UP(listOf(HardwareKey.VOLUME_DOWN, HardwareKey.VOLUME_UP)),
}

/**
 * Short volume-press pattern, e.g. up then down, optionally repeated. Each press
 * must follow the previous one within [stepWindowMs], and the pattern completes
 * on the key-down of its last press: press, press, unlocked.
 *
 * Nothing has to be held and a fumble costs nothing. An earlier version added a
 * held final press and a quiet period after any wrong key, to stop a child
 * mashing the rocker from stumbling in. It worked, but it made the parent's own
 * unlock slow and unpredictable, which is worse: a parent who cannot get out
 * reliably has no product. Length is the lever instead - a parent who wants more
 * resistance sets the pattern to repeat, which stays instant per press.
 *
 * Only key-down transitions count, so a held key (auto-repeat) is one press.
 */
class VolumeSequenceGesture(
    pattern: VolumePattern,
    repeats: Int,
    private val listener: GestureListener,
    private val stepWindowMs: Long = DEFAULT_STEP_WINDOW_MS,
) : UnlockGesture {
    private val expected: List<HardwareKey> = List(repeats.coerceIn(1, 3)) { pattern.steps }.flatten()
    private var index = 0
    private var lastMs = -1L

    override val wantsTicks: Boolean get() = false

    override fun onKey(key: HardwareKey, down: Boolean, timeMs: Long) {
        if (!down) return
        if (key == HardwareKey.BACK) {
            cancel()
            return
        }
        if (index > 0 && timeMs - lastMs > stepWindowMs) {
            index = 0
            listener.onGestureEvent(GestureEvent.Reset)
        }
        if (key == expected[index]) {
            index++
            lastMs = timeMs
            if (index == expected.size) {
                index = 0
                lastMs = -1
                listener.onGestureEvent(GestureEvent.Unlocked)
            } else {
                listener.onGestureEvent(GestureEvent.Progress(index.toFloat() / expected.size))
            }
        } else {
            // Wrong key: it may still be the first press of a fresh attempt, so a
            // parent who fumbles once can carry straight on rather than wait.
            val restart = key == expected[0]
            index = if (restart) 1 else 0
            lastMs = timeMs
            listener.onGestureEvent(if (restart) GestureEvent.Progress(1f / expected.size) else GestureEvent.Reset)
        }
    }

    override fun reset() = cancel()

    private fun cancel() {
        if (index > 0) listener.onGestureEvent(GestureEvent.Reset)
        index = 0
        lastMs = -1
    }

    companion object {
        /** Generous: a deliberate second press lands well inside this. */
        const val DEFAULT_STEP_WINDOW_MS = 1200L
    }
}

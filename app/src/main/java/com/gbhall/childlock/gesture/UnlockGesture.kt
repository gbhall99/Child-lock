package com.gbhall.childlock.gesture

sealed interface GestureEvent {
    /** A hold is under way; [fraction] runs 0..1. Used to draw the progress ring. */
    data class Progress(val fraction: Float) : GestureEvent

    /** The adult gesture completed. The lock must be released. */
    data object Unlocked : GestureEvent

    /** A hold was abandoned or broken (extra finger, finger left its zone, key released). */
    data object Reset : GestureEvent

    /** Badge long-press completed; the host should reveal the PIN pad. */
    data object ShowPinPad : GestureEvent
}

fun interface GestureListener {
    fun onGestureEvent(event: GestureEvent)
}

/**
 * A recogniser for the adult unlock gesture. Implementations are pure state
 * machines: the host feeds touches, keys and clock ticks and reacts to events.
 * Timing is driven entirely by the timestamps the host supplies, never by a
 * wall clock, so tests are deterministic.
 */
interface UnlockGesture {
    /** True while a hold is in progress and the host should keep calling [onTick]. */
    val wantsTicks: Boolean

    fun setBounds(width: Int, height: Int) {}
    fun onTouch(sample: TouchSample) {}
    fun onKey(key: HardwareKey, down: Boolean, timeMs: Long) {}
    fun onTick(nowMs: Long) {}
    fun reset()
}

/**
 * Shared hold-timer logic: starts on [begin], reports progress on every tick,
 * fires Unlocked (or another terminal event) once [holdMs] has elapsed.
 */
internal class HoldTimer(
    private val holdMs: Long,
    private val listener: GestureListener,
    private val completion: GestureEvent = GestureEvent.Unlocked,
) {
    private var startMs = -1L
    private var lastFraction = -1f

    val active: Boolean get() = startMs >= 0

    fun begin(nowMs: Long) {
        if (active) return
        startMs = nowMs
        lastFraction = 0f
        listener.onGestureEvent(GestureEvent.Progress(0f))
    }

    fun tick(nowMs: Long) {
        if (!active) return
        val fraction = ((nowMs - startMs).toFloat() / holdMs).coerceIn(0f, 1f)
        if (fraction >= 1f) {
            startMs = -1
            lastFraction = -1f
            listener.onGestureEvent(completion)
        } else if (fraction != lastFraction) {
            lastFraction = fraction
            listener.onGestureEvent(GestureEvent.Progress(fraction))
        }
    }

    fun cancel() {
        if (!active) return
        startMs = -1
        lastFraction = -1f
        listener.onGestureEvent(GestureEvent.Reset)
    }
}

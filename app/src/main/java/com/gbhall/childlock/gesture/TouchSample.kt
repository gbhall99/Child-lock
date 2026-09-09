package com.gbhall.childlock.gesture

/** One finger on the screen, in overlay pixel coordinates. */
data class Pointer(val id: Int, val x: Float, val y: Float)

enum class TouchAction { DOWN, MOVE, UP, CANCEL }

/**
 * Platform-independent snapshot of a touch event. [pointers] lists every finger
 * still on the screen *after* [action] has been applied, so an UP that lifts the
 * last finger carries an empty list. Keeping this free of android.view types is
 * what lets the gesture recognisers run in plain JVM unit tests.
 */
data class TouchSample(val action: TouchAction, val pointers: List<Pointer>, val timeMs: Long)

enum class HardwareKey { VOLUME_UP, VOLUME_DOWN, BACK }

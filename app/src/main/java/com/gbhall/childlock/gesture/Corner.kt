package com.gbhall.childlock.gesture

enum class Corner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    /** True when ([x], [y]) is inside this corner's zone, a square of [fraction] × the shorter screen side. */
    fun contains(x: Float, y: Float, width: Int, height: Int, fraction: Float): Boolean {
        val zone = minOf(width, height) * fraction
        val inLeft = x < zone
        val inRight = x > width - zone
        val inTop = y < zone
        val inBottom = y > height - zone
        return when (this) {
            TOP_LEFT -> inLeft && inTop
            TOP_RIGHT -> inRight && inTop
            BOTTOM_LEFT -> inLeft && inBottom
            BOTTOM_RIGHT -> inRight && inBottom
        }
    }
}

enum class CornerPair(val first: Corner, val second: Corner) {
    TOP_LEFT_BOTTOM_RIGHT(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT),
    TOP_RIGHT_BOTTOM_LEFT(Corner.TOP_RIGHT, Corner.BOTTOM_LEFT),
}

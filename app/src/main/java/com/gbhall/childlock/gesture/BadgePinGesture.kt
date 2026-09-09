package com.gbhall.childlock.gesture

/**
 * Single-finger long press on the badge. Completes with [GestureEvent.ShowPinPad];
 * PIN entry itself is handled by the host UI. A second finger or drifting off
 * the badge resets the press.
 */
class BadgePinGesture(
    longPressMs: Long,
    private val listener: GestureListener,
) : UnlockGesture {
    private val timer = HoldTimer(longPressMs, listener, completion = GestureEvent.ShowPinPad)
    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f

    override val wantsTicks: Boolean get() = timer.active

    /** Badge hit area in overlay coordinates. Give it generous slop; adult thumbs are big. */
    fun setBadgeBounds(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        timer.cancel()
    }

    override fun onTouch(sample: TouchSample) {
        val p = sample.pointers.singleOrNull()
        if (sample.action == TouchAction.CANCEL || p == null || !contains(p)) {
            timer.cancel()
            return
        }
        timer.begin(sample.timeMs)
        timer.tick(sample.timeMs)
    }

    override fun onTick(nowMs: Long) = timer.tick(nowMs)

    override fun reset() = timer.cancel()

    private fun contains(p: Pointer) = p.x in left..right && p.y in top..bottom
}

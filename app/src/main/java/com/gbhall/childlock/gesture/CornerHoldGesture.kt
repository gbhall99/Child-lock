package com.gbhall.childlock.gesture

/**
 * Two-finger diagonal corner hold: one finger in each corner of [pair], held
 * together for [holdMs] with no third finger and neither finger leaving its
 * zone. Any deviation resets the timer, which is what defeats random mashing.
 */
class CornerHoldGesture(
    holdMs: Long,
    private val pair: CornerPair,
    private val listener: GestureListener,
    private val zoneFraction: Float = DEFAULT_ZONE_FRACTION,
) : UnlockGesture {
    private val timer = HoldTimer(holdMs, listener)
    private var width = 0
    private var height = 0

    override val wantsTicks: Boolean get() = timer.active

    override fun setBounds(width: Int, height: Int) {
        this.width = width
        this.height = height
        timer.cancel()
    }

    override fun onTouch(sample: TouchSample) {
        if (width <= 0 || height <= 0) return
        if (sample.action == TouchAction.CANCEL || !isValidHold(sample.pointers)) {
            timer.cancel()
            return
        }
        timer.begin(sample.timeMs)
        timer.tick(sample.timeMs)
    }

    override fun onTick(nowMs: Long) = timer.tick(nowMs)

    override fun reset() = timer.cancel()

    private fun isValidHold(pointers: List<Pointer>): Boolean {
        if (pointers.size != 2) return false
        val (a, b) = pointers
        return (inZone(a, pair.first) && inZone(b, pair.second)) ||
            (inZone(a, pair.second) && inZone(b, pair.first))
    }

    private fun inZone(p: Pointer, corner: Corner) = corner.contains(p.x, p.y, width, height, zoneFraction)

    companion object {
        const val DEFAULT_ZONE_FRACTION = 0.22f
    }
}

package com.gbhall.childlock.gesture

/**
 * Volume up and volume down pressed together and held for [holdMs]. Fed by the
 * accessibility service, which is the only component that sees hardware keys.
 */
class VolumeChordGesture(
    holdMs: Long,
    private val listener: GestureListener,
) : UnlockGesture {
    private val timer = HoldTimer(holdMs, listener)
    private var upDown = false
    private var downDown = false

    override val wantsTicks: Boolean get() = timer.active

    override fun onKey(key: HardwareKey, down: Boolean, timeMs: Long) {
        when (key) {
            HardwareKey.VOLUME_UP -> upDown = down
            HardwareKey.VOLUME_DOWN -> downDown = down
            HardwareKey.BACK -> {
                // Back during a chord is a stray press; treat it as a break.
                if (down) timer.cancel()
                return
            }
        }
        if (upDown && downDown) {
            timer.begin(timeMs)
            timer.tick(timeMs)
        } else {
            timer.cancel()
        }
    }

    override fun onTick(nowMs: Long) = timer.tick(nowMs)

    override fun reset() {
        upDown = false
        downDown = false
        timer.cancel()
    }
}

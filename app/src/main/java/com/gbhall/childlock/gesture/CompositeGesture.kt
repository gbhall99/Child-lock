package com.gbhall.childlock.gesture

/**
 * Several unlock recognisers fed the same input; whichever completes first
 * unlocks. Lets a parent allow, say, the volume pattern and a PIN at once.
 */
class CompositeGesture(private val parts: List<UnlockGesture>) : UnlockGesture {
    override val wantsTicks: Boolean get() = parts.any { it.wantsTicks }
    override fun setBounds(width: Int, height: Int) = parts.forEach { it.setBounds(width, height) }
    override fun onTouch(sample: TouchSample) = parts.forEach { it.onTouch(sample) }
    override fun onKey(key: HardwareKey, down: Boolean, timeMs: Long) = parts.forEach { it.onKey(key, down, timeMs) }
    override fun onTick(nowMs: Long) = parts.forEach { it.onTick(nowMs) }
    override fun reset() = parts.forEach { it.reset() }
}

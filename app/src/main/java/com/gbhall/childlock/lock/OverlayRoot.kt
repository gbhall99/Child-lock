package com.gbhall.childlock.lock

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.gesture.PinHasher
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings

/**
 * Root of the overlay window: the touch shield underneath and, for the badge
 * PIN gesture, a keypad that appears beside the badge on demand.
 */
class OverlayRoot(
    context: Context,
    private val settings: LockSettings,
    private val onUnlock: () -> Unit,
) : FrameLayout(context), TouchShieldView.Host {

    private val shield = TouchShieldView(context, settings, this)
    private val pinPad: PinPadView? =
        if (settings.gesture == GestureType.BADGE_PIN && settings.hasPin) {
            PinPadView(context, settings.pinLength, ::onPinEntered)
        } else {
            null
        }
    private var wrongAttempts = 0
    private var padCooldownUntil = 0L
    private val hidePad = Runnable { hidePinPad() }

    init {
        addView(shield, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pinPad?.let { pad ->
            pad.visibility = GONE
            pad.onKeyPressed = { scheduleAutoHide() }
            val margin = (TouchShieldView.BADGE_MARGIN_DP * 2 * resources.displayMetrics.density).toInt()
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, gravityFor(settings.badgeCorner))
            lp.setMargins(margin, margin, margin, margin)
            addView(pad, lp)
        }
    }

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        pinPad?.let { pad ->
            val i = LockOverlayService.systemInsets(insets)
            val extra = (TouchShieldView.BADGE_MARGIN_DP * 2 + TouchShieldView.BADGE_DIAMETER_DP) *
                resources.displayMetrics.density
            val lp = pad.layoutParams as LayoutParams
            lp.setMargins(
                (i[0] + extra).toInt(), (i[1] + extra).toInt(),
                (i[2] + extra).toInt(), (i[3] + extra).toInt(),
            )
            pad.layoutParams = lp
        }
        return super.onApplyWindowInsets(insets)
    }

    private fun gravityFor(corner: Corner): Int = when (corner) {
        Corner.TOP_LEFT -> Gravity.TOP or Gravity.START
        Corner.TOP_RIGHT -> Gravity.TOP or Gravity.END
        Corner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        Corner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
    }

    override fun onUnlock() = onUnlock.invoke()

    override fun onShowPinPad() {
        val pad = pinPad ?: return
        if (SystemClock.uptimeMillis() < padCooldownUntil) return
        pad.clear()
        pad.visibility = VISIBLE
        scheduleAutoHide()
    }

    private fun scheduleAutoHide() {
        removeCallbacks(hidePad)
        postDelayed(hidePad, PAD_IDLE_HIDE_MS)
    }

    private fun hidePinPad() {
        removeCallbacks(hidePad)
        pinPad?.visibility = GONE
    }

    private fun onPinEntered(pin: String) {
        if (PinHasher.matches(pin, settings.pinHash)) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            hidePinPad()
            onUnlock.invoke()
            return
        }
        wrongAttempts++
        pinPad?.showError()
        if (wrongAttempts >= MAX_WRONG_ATTEMPTS) {
            wrongAttempts = 0
            padCooldownUntil = SystemClock.uptimeMillis() + PAD_COOLDOWN_MS
            hidePinPad()
        } else {
            scheduleAutoHide()
        }
    }

    /** A tap anywhere outside the keypad dismisses it; the shield still swallows the touch. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val pad = pinPad
        if (pad != null && pad.visibility == VISIBLE && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val r = Rect()
            pad.getHitRect(r)
            if (!r.contains(ev.x.toInt(), ev.y.toInt())) hidePinPad()
        }
        return super.dispatchTouchEvent(ev)
    }

    fun dispose() {
        removeCallbacks(hidePad)
        shield.dispose()
    }

    companion object {
        private const val PAD_IDLE_HIDE_MS = 8_000L
        private const val PAD_COOLDOWN_MS = 30_000L
        private const val MAX_WRONG_ATTEMPTS = 3
    }
}

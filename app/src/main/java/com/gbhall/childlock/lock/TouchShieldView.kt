package com.gbhall.childlock.lock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.gbhall.childlock.R
import com.gbhall.childlock.gesture.BadgePinGesture
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.gesture.CornerHoldGesture
import com.gbhall.childlock.gesture.GestureEvent
import com.gbhall.childlock.gesture.GestureListener
import com.gbhall.childlock.gesture.Pointer
import com.gbhall.childlock.gesture.TouchAction
import com.gbhall.childlock.gesture.TouchSample
import com.gbhall.childlock.gesture.UnlockGesture
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings

/**
 * Transparent full-screen view that swallows every touch, draws the small
 * badge, and feeds touches to the active unlock gesture.
 */
class TouchShieldView(
    context: Context,
    private val settings: LockSettings,
    private val host: Host,
) : View(context), GestureListener {

    interface Host {
        fun onUnlock()
        fun onShowPinPad()
    }

    private val density = resources.displayMetrics.density
    private val badgeRadius = BADGE_DIAMETER_DP / 2 * density
    private val badgeMargin = BADGE_MARGIN_DP * density
    private val ringStroke = 3f * density
    private var insets = intArrayOf(0, 0, 0, 0)

    private val badgeCenterX get() = badgeRect.centerX()
    private val badgeCenterY get() = badgeRect.centerY()
    val badgeRect = RectF()

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val ringTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStroke
        color = Color.argb(70, 255, 255, 255)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStroke
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val lockIcon: Drawable? = context.getDrawable(R.drawable.ic_lock)?.mutate()?.apply {
        setTint(Color.WHITE)
    }

    private var progress = 0f
    private var disposed = false
    private val handler = Handler(Looper.getMainLooper())

    /** Corner hold is always available as a safety fallback, even in volume-chord mode. */
    private val gesture: UnlockGesture = when (settings.gesture) {
        GestureType.BADGE_PIN -> BadgePinGesture(settings.holdMs, this)
        GestureType.CORNER_HOLD, GestureType.VOLUME_CHORD ->
            CornerHoldGesture(settings.holdMs, settings.cornerPair, this)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (disposed) return
            gesture.onTick(SystemClock.uptimeMillis())
            if (gesture.wantsTicks) handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        isClickable = true
        isHapticFeedbackEnabled = true
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = context.getString(R.string.shield_content_description)
    }

    override fun onApplyWindowInsets(windowInsets: WindowInsets): WindowInsets {
        insets = LockOverlayService.systemInsets(windowInsets)
        layoutBadge(width, height)
        invalidate()
        return windowInsets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gesture.setBounds(w, h)
        layoutBadge(w, h)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Ask the system to leave the side edges to us. It grants at most
            // 200dp per edge; the accessibility guard covers what slips through.
            val edge = (EDGE_EXCLUSION_DP * density).toInt()
            systemGestureExclusionRects = listOf(Rect(0, 0, edge, h), Rect(w - edge, 0, w, h))
        }
    }

    private fun layoutBadge(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        val d = badgeRadius * 2
        val left = insets[0] + badgeMargin
        val top = insets[1] + badgeMargin
        val right = w - insets[2] - badgeMargin - d
        val bottom = h - insets[3] - badgeMargin - d
        val (x, y) = when (settings.badgeCorner) {
            Corner.TOP_LEFT -> left to top
            Corner.TOP_RIGHT -> right to top
            Corner.BOTTOM_LEFT -> left to bottom
            Corner.BOTTOM_RIGHT -> right to bottom
        }
        badgeRect.set(x, y, x + d, y + d)
        (gesture as? BadgePinGesture)?.let {
            val slop = BADGE_SLOP_DP * density
            it.setBadgeBounds(badgeRect.left - slop, badgeRect.top - slop, badgeRect.right + slop, badgeRect.bottom + slop)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (disposed) return true
        val masked = event.actionMasked
        val action = when (masked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> TouchAction.UP
            else -> TouchAction.CANCEL
        }
        val pointers = if (action == TouchAction.CANCEL || masked == MotionEvent.ACTION_UP) {
            emptyList()
        } else {
            buildList(event.pointerCount) {
                for (i in 0 until event.pointerCount) {
                    if (masked == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex) continue
                    add(Pointer(event.getPointerId(i), event.getX(i), event.getY(i)))
                }
            }
        }
        gesture.onTouch(TouchSample(action, pointers, event.eventTime))
        handler.removeCallbacks(tick)
        if (gesture.wantsTicks) handler.postDelayed(tick, TICK_MS)
        return true // consume everything: this is the whole point
    }

    override fun onGestureEvent(event: GestureEvent) {
        when (event) {
            is GestureEvent.Progress -> {
                progress = event.fraction
                invalidate()
            }
            GestureEvent.Reset -> {
                progress = 0f
                invalidate()
            }
            GestureEvent.Unlocked -> {
                haptic()
                host.onUnlock()
            }
            GestureEvent.ShowPinPad -> {
                progress = 0f
                invalidate()
                haptic()
                host.onShowPinPad()
            }
        }
    }

    private fun haptic() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        performHapticFeedback(constant)
    }

    override fun onDraw(canvas: Canvas) {
        if (badgeRect.isEmpty) return
        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgePaint)
        lockIcon?.let {
            val half = (BADGE_ICON_DP / 2 * density).toInt()
            it.setBounds(
                (badgeCenterX - half).toInt(), (badgeCenterY - half).toInt(),
                (badgeCenterX + half).toInt(), (badgeCenterY + half).toInt(),
            )
            it.draw(canvas)
        }
        if (progress > 0f) {
            val inset = ringStroke / 2
            val arc = RectF(
                badgeRect.left - inset, badgeRect.top - inset,
                badgeRect.right + inset, badgeRect.bottom + inset,
            )
            canvas.drawOval(arc, ringTrackPaint)
            canvas.drawArc(arc, -90f, 360f * progress, false, ringPaint)
        }
    }

    fun dispose() {
        disposed = true
        handler.removeCallbacks(tick)
        gesture.reset()
    }

    companion object {
        const val BADGE_DIAMETER_DP = 30f
        const val BADGE_ICON_DP = 16f
        const val BADGE_MARGIN_DP = 10f
        const val BADGE_SLOP_DP = 16f
        const val EDGE_EXCLUSION_DP = 48f
        private const val TICK_MS = 33L
    }
}

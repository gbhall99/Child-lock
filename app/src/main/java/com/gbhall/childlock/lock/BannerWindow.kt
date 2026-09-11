package com.gbhall.childlock.lock

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gbhall.childlock.R

/**
 * Short-lived status notice in the style of Android's own transient chips
 * (screen-record, casting): a dark translucent surface, a small tinted icon,
 * a title and a one-line detail. Its own small non-touchable window, so it
 * never blocks or intercepts anything.
 */
class BannerWindow(private val context: Context) {
    enum class Kind { ON, OFF, ARMING }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val hide = Runnable { dismiss(animated = true) }

    fun show(kind: Kind, detail: String) {
        dismiss(animated = false)
        val d = context.resources.displayMetrics.density
        val v = build(context, kind, detail)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (12 * d).toInt()
            title = "ChildLockBanner"
            // Keep clear of a display cutout rather than sitting under it.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }
        try {
            wm.addView(v, params)
            view = v
            v.alpha = 0f
            v.translationY = -16 * d
            v.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            handler.postDelayed(hide, DURATION_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Banner not shown", e)
        }
    }

    fun dismiss(animated: Boolean = false) {
        handler.removeCallbacks(hide)
        val v = view ?: return
        view = null
        if (!animated) {
            remove(v)
            return
        }
        v.animate().alpha(0f).translationY(-8 * context.resources.displayMetrics.density).setDuration(160)
            .withEndAction { remove(v) }.start()
    }

    private fun remove(v: View) {
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
            Log.w(TAG, "Banner already gone", e)
        }
    }

    companion object {
        private const val TAG = "BannerWindow"
        const val DURATION_MS = 1800L
        private const val SURFACE = 0xF2202124.toInt()
        private const val BLUE = 0xFF8AB4F8.toInt()
        private const val ORANGE = 0xFFFDD663.toInt()
        private const val GREY = 0xFFBDC1C6.toInt()

        /** Builds the chip; public so it can be rendered in tests and previews. */
        fun build(context: Context, kind: Kind, detail: String): View {
            val d = context.resources.displayMetrics.density
            val (iconRes, tint, title) = when (kind) {
                Kind.ON -> Triple(R.drawable.ic_lock, BLUE, context.getString(R.string.banner_on))
                Kind.OFF -> Triple(R.drawable.ic_lock_open, GREY, context.getString(R.string.banner_off))
                Kind.ARMING -> Triple(R.drawable.ic_lock, ORANGE, context.getString(R.string.banner_arming_title))
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 24 * d
                    setColor(SURFACE)
                }
                elevation = 8 * d
                addView(
                    ImageView(context).apply {
                        setImageResource(iconRes)
                        imageTintList = ColorStateList.valueOf(tint)
                    },
                    LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()).apply { marginEnd = (12 * d).toInt() },
                )
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(context).apply {
                            text = title
                            textSize = 15f
                            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            setTextColor(Color.WHITE)
                            includeFontPadding = false
                        })
                        addView(TextView(context).apply {
                            text = detail
                            textSize = 13f
                            setTextColor(GREY)
                            includeFontPadding = false
                            setPadding(0, (2 * d).toInt(), 0, 0)
                        })
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            }
        }
    }
}

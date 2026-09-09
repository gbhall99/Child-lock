package com.gbhall.childlock.lock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Big, short-lived on-screen confirmation ("Child Lock ON" / "OFF"). Its own
 * small non-touchable window, so it never blocks or intercepts anything.
 */
class BannerWindow(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private val hide = Runnable { dismiss() }

    fun show(text: String, on: Boolean) {
        dismiss()
        val d = context.resources.displayMetrics.density
        val tv = TextView(context).apply {
            this.text = text
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((28 * d).toInt(), (16 * d).toInt(), (28 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 999 * d
                setColor(if (on) BLUE else GREY)
            }
            elevation = 12 * d
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (72 * d).toInt()
            title = "ChildLockBanner"
        }
        try {
            wm.addView(tv, params)
            view = tv
            tv.animate().alpha(1f).setDuration(150).start()
            handler.postDelayed(hide, DURATION_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Banner not shown", e)
        }
    }

    fun dismiss() {
        handler.removeCallbacks(hide)
        view?.let {
            try {
                wm.removeViewImmediate(it)
            } catch (e: Exception) {
                Log.w(TAG, "Banner already gone", e)
            }
        }
        view = null
    }

    companion object {
        private const val TAG = "BannerWindow"
        const val DURATION_MS = 1500L
        private const val BLUE = 0xFF0072B2.toInt()
        private const val GREY = 0xFF5A5A5F.toInt()
    }
}

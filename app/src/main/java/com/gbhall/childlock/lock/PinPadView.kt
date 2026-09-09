package com.gbhall.childlock.lock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.gbhall.childlock.R

/**
 * Compact numeric keypad shown next to the badge after a badge long-press.
 * Submits automatically once [pinLength] digits are entered.
 */
class PinPadView(
    context: Context,
    private val pinLength: Int,
    private val onPinEntered: (String) -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val display = TextView(context)
    private val entered = StringBuilder()
    var onKeyPressed: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = (10 * density).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = 16 * density
            setColor(Color.argb(235, 28, 28, 30))
        }
        elevation = 8 * density

        display.apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            minHeight = (36 * density).toInt()
        }
        addView(display, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val grid = GridLayout(context).apply { columnCount = 3 }
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕")
        for (label in labels) grid.addView(makeKey(label))
        addView(grid, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        render()
    }

    private fun makeKey(label: String): TextView {
        val size = (52 * density).toInt()
        val gap = (3 * density).toInt()
        return TextView(context).apply {
            text = label
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(90, 255, 255, 255)),
                GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(Color.argb(40, 255, 255, 255))
                },
                null,
            )
            layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(gap, gap, gap, gap)
            }
            setOnClickListener { onKey(label) }
        }
    }

    private fun onKey(label: String) {
        onKeyPressed?.invoke()
        when (label) {
            "⌫" -> if (entered.isNotEmpty()) entered.setLength(entered.length - 1)
            "✕" -> entered.setLength(0)
            else -> if (entered.length < pinLength) entered.append(label)
        }
        render()
        if (entered.length == pinLength) {
            val pin = entered.toString()
            entered.setLength(0)
            onPinEntered(pin)
        }
    }

    fun clear() {
        entered.setLength(0)
        render()
    }

    fun showError() {
        entered.setLength(0)
        display.text = context.getString(R.string.pin_wrong)
        postDelayed({ render() }, 900)
    }

    private fun render() {
        display.text = "•".repeat(entered.length) + "◦".repeat((pinLength - entered.length).coerceAtLeast(0))
    }
}

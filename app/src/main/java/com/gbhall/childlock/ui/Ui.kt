package com.gbhall.childlock.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Tiny helpers for building the settings screen without layout XML. */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

internal val Context.isNight: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

internal fun Context.themeColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
}

internal fun Context.vertical(build: LinearLayout.() -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        build()
    }

internal fun Context.card(title: String, build: LinearLayout.() -> Unit): LinearLayout =
    vertical {
        val p = dp(16)
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (isNight) Color.rgb(36, 36, 40) else Color.rgb(243, 244, 248))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
        addView(heading(title))
        build()
    }

internal fun Context.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 18f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(themeColor(android.R.attr.textColorPrimary))
    setPadding(0, 0, 0, dp(8))
}

internal fun Context.body(text: CharSequence, secondary: Boolean = false, size: Float = 15f): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(themeColor(if (secondary) android.R.attr.textColorSecondary else android.R.attr.textColorPrimary))
    setPadding(0, dp(2), 0, dp(6))
}

/** Title + subtitle on the left, an optional action view on the right. */
internal fun Context.row(title: String, subtitle: CharSequence?, action: View? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(
            vertical {
                addView(body(title))
                if (subtitle != null) addView(body(subtitle, secondary = true, size = 13f))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (action != null) {
            addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12)
            })
        }
    }

internal fun Context.switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    val sw = Switch(this).apply {
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }
    return row(title, subtitle, sw)
}

internal fun Context.radioGroup(
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    horizontal: Boolean = false,
    onSelect: (Int) -> Unit,
): RadioGroup = RadioGroup(this).apply {
    orientation = if (horizontal) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
    options.forEachIndexed { index, (label, description) ->
        val button = RadioButton(context).apply {
            id = View.generateViewId()
            text = if (description == null) label else "$label\n$description"
            textSize = 15f
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isChecked = index == selectedIndex
            setOnClickListener { onSelect(index) }
        }
        addView(button)
    }
}

internal fun Context.seekRow(
    title: String,
    min: Int,
    max: Int,
    value: Int,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
): LinearLayout = vertical {
    val label = body("$title: ${format(value)}")
    addView(label)
    val seek = SeekBar(context).apply {
        this.min = min
        this.max = max
        progress = value
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, p: Int, fromUser: Boolean) {
                label.text = "$title: ${format(p)}"
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }
    addView(seek)
}

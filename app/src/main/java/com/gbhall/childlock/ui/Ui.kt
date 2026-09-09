package com.gbhall.childlock.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Colour-blind-safe palette (Okabe & Ito). Nothing in the app is red or green,
 * and every status also carries a distinct glyph and word, so colour is only
 * ever a reinforcement.
 */
object Palette {
    const val BLUE = 0xFF0072B2.toInt()
    const val SKY = 0xFF56B4E9.toInt()
    const val ORANGE = 0xFFE69F00.toInt()
    const val GREY = 0xFF8C8C91.toInt()
    const val DISABLED = 0xFF5A5A5F.toInt()
}

/** A status the UI can show: glyph + colour, always paired with a word. */
enum class Tone(val glyph: String) {
    GOOD("✓"),
    ATTENTION("▲"),
    NEUTRAL("○"),
    ACTIVE("●"),
    PENDING("◐");

    fun color(context: Context): Int = when (this) {
        GOOD, ACTIVE -> if (context.isNight) Palette.SKY else Palette.BLUE
        ATTENTION, PENDING -> Palette.ORANGE
        NEUTRAL -> Palette.GREY
    }
}

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

internal fun Context.card(title: String?, build: LinearLayout.() -> Unit): LinearLayout =
    vertical {
        val p = dp(18)
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(if (isNight) Color.rgb(32, 33, 38) else Color.WHITE)
            if (!isNight) setStroke(dp(1), Color.rgb(225, 228, 235))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }
        if (title != null) addView(heading(title))
        build()
    }

internal fun Context.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 13f
    letterSpacing = 0.08f
    isAllCaps = true
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(if (isNight) Palette.SKY else Palette.BLUE)
    setPadding(0, 0, 0, dp(10))
}

internal fun Context.body(text: CharSequence, secondary: Boolean = false, size: Float = 15f): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(themeColor(if (secondary) android.R.attr.textColorSecondary else android.R.attr.textColorPrimary))
    setPadding(0, dp(2), 0, dp(4))
}

/** Rounded pill: glyph + word in the tone's colour on a tinted background. */
internal fun Context.chip(text: String, tone: Tone, large: Boolean = false): TextView = TextView(this).apply {
    val c = tone.color(context)
    this.text = "${tone.glyph}  $text"
    textSize = if (large) 20f else 13f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(c)
    val h = dp(if (large) 16 else 10)
    val v = dp(if (large) 10 else 4)
    setPadding(h, v, h, v)
    background = GradientDrawable().apply {
        cornerRadius = dp(999).toFloat()
        setColor((c and 0x00FFFFFF) or 0x24000000)
    }
    gravity = Gravity.CENTER_VERTICAL
}

/** Full-width primary action in the palette blue; grey when disabled. */
internal fun Context.primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    textSize = 17f
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = false
    setTextColor(Color.WHITE)
    stateListAnimator = null
    background = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(Color.WHITE)
    }
    backgroundTintList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(Palette.DISABLED, Palette.BLUE),
    )
    setOnClickListener { onClick() }
}

/** Compact secondary action: outlined in the palette blue. */
internal fun Context.actionButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    textSize = 14f
    isAllCaps = false
    typeface = Typeface.DEFAULT_BOLD
    val c = if (isNight) Palette.SKY else Palette.BLUE
    setTextColor(c)
    stateListAnimator = null
    minWidth = 0
    minimumWidth = 0
    setPadding(dp(16), 0, dp(16), 0)
    background = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(Color.TRANSPARENT)
        setStroke(dp(2), c)
    }
    setOnClickListener { onClick() }
}

/** Title + subtitle (+ optional status chip) on the left, an optional action on the right. */
internal fun Context.row(title: String, subtitle: CharSequence?, action: View? = null, chip: View? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
        addView(
            vertical {
                addView(body(title).apply { typeface = Typeface.DEFAULT_BOLD })
                if (subtitle != null) addView(body(subtitle, secondary = true, size = 13f))
                if (chip != null) {
                    addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(4)
                    })
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (action != null) {
            addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
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

internal fun Context.divider(): View = View(this).apply {
    setBackgroundColor(if (isNight) Color.rgb(58, 60, 66) else Color.rgb(228, 230, 236))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
        topMargin = dp(6)
        bottomMargin = dp(6)
    }
}

/** Radio list where each option has a bold label and a smaller secondary description. */
internal fun Context.radioGroup(
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
): RadioGroup = RadioGroup(this).apply {
    orientation = RadioGroup.VERTICAL
    val secondary = themeColor(android.R.attr.textColorSecondary)
    options.forEachIndexed { index, (label, description) ->
        val button = RadioButton(context).apply {
            id = View.generateViewId()
            text = SpannableStringBuilder().apply {
                append(label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (description != null) {
                    append("\n")
                    val start = length
                    append(description)
                    setSpan(RelativeSizeSpan(0.86f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(secondary), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            textSize = 15f
            setPadding(dp(8), dp(8), dp(4), dp(8))
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
    val label = body("$title: ${format(value)}").apply { typeface = Typeface.DEFAULT_BOLD }
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

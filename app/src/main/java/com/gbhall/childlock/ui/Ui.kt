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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Colour-blind-safe palette (Okabe & Ito). Nothing in the app is red or green,
 * and every status also carries a distinct icon and word, so colour is only
 * ever a reinforcement.
 */
object Palette {
    const val BLUE = 0xFF0072B2.toInt()
    const val SKY = 0xFF56B4E9.toInt()
    const val ORANGE = 0xFFE69F00.toInt()
    const val GREY = 0xFF8C8C91.toInt()
    const val DISABLED = 0xFFB0B0B5.toInt()
}

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

internal val Context.pageBackground: Int get() = if (isNight) 0xFF121317.toInt() else 0xFFF2F4F8.toInt()
internal val Context.cardBackground: Int get() = if (isNight) 0xFF1E2027.toInt() else Color.WHITE
internal val Context.hairline: Int get() = if (isNight) 0xFF34363E.toInt() else 0xFFE4E6EC.toInt()
internal val Context.accent: Int get() = if (isNight) Palette.SKY else Palette.BLUE

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

internal fun Context.horizontal(build: LinearLayout.() -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        build()
    }

internal fun Context.card(title: String?, iconRes: Int? = null, build: LinearLayout.() -> Unit): LinearLayout =
    vertical {
        val p = dp(20)
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(cardBackground)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }
        if (title != null) {
            addView(
                horizontal {
                    if (iconRes != null) addView(icon(iconRes, accent, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
                    addView(heading(title))
                }.apply { setPadding(0, 0, 0, dp(12)) },
            )
        }
        build()
    }

internal fun Context.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 17f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(themeColor(android.R.attr.textColorPrimary))
}

internal fun Context.body(text: CharSequence, secondary: Boolean = false, size: Float = 15f): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setLineSpacing(0f, 1.15f)
    setTextColor(themeColor(if (secondary) android.R.attr.textColorSecondary else android.R.attr.textColorPrimary))
}

internal fun Context.icon(resId: Int, tint: Int, sizeDp: Int = 24): ImageView = ImageView(this).apply {
    setImageResource(resId)
    imageTintList = ColorStateList.valueOf(tint)
    scaleType = ImageView.ScaleType.FIT_CENTER
    layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Icon inside a soft tinted disc. */
internal fun Context.iconDisc(resId: Int, tint: Int, discDp: Int = 44, iconDp: Int = 22): FrameLayout = FrameLayout(this).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor((tint and 0x00FFFFFF) or 0x1F000000)
    }
    addView(icon(resId, tint, iconDp), FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
    layoutParams = ViewGroup.LayoutParams(dp(discDp), dp(discDp))
}

/** Rounded pill: glyph + word in the tone's colour on a tinted background. */
internal fun Context.chip(text: String, tone: Tone): TextView = TextView(this).apply {
    val c = tone.color(context)
    this.text = "${tone.glyph} $text"
    textSize = 12.5f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(c)
    setPadding(dp(10), dp(4), dp(10), dp(4))
    background = GradientDrawable().apply {
        cornerRadius = dp(999).toFloat()
        setColor((c and 0x00FFFFFF) or 0x22000000)
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
        cornerRadius = dp(18).toFloat()
        setColor(Color.WHITE)
    }
    backgroundTintList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(Palette.DISABLED, Palette.BLUE),
    )
    setOnClickListener { onClick() }
}

/** Compact secondary action: tinted pill. */
internal fun Context.actionButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    textSize = 14f
    isAllCaps = false
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(accent)
    stateListAnimator = null
    minWidth = 0
    minimumWidth = 0
    minHeight = 0
    minimumHeight = 0
    setPadding(dp(16), dp(8), dp(16), dp(8))
    background = GradientDrawable().apply {
        cornerRadius = dp(999).toFloat()
        setColor((accent and 0x00FFFFFF) or 0x1A000000)
    }
    setOnClickListener { onClick() }
}

/** Two-option segmented control. */
internal fun Context.segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout = horizontal {
    val track = this
    background = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor((accent and 0x00FFFFFF) or 0x14000000)
    }
    setPadding(dp(4), dp(4), dp(4), dp(4))
    val buttons = ArrayList<TextView>()
    fun render(sel: Int) {
        buttons.forEachIndexed { i, b ->
            val on = i == sel
            b.setTextColor(if (on) Color.WHITE else accent)
            b.background = GradientDrawable().apply {
                cornerRadius = dp(11).toFloat()
                setColor(if (on) Palette.BLUE else Color.TRANSPARENT)
            }
        }
    }
    labels.forEachIndexed { i, label ->
        val b = TextView(context).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            setOnClickListener {
                render(i)
                onSelect(i)
            }
        }
        buttons += b
        track.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    render(selected)
}

/** Leading icon, title + subtitle (+ optional status chip), optional trailing action. */
internal fun Context.row(
    title: String,
    subtitle: CharSequence?,
    action: View? = null,
    chip: View? = null,
    iconRes: Int? = null,
    iconTint: Int = accent,
): LinearLayout = horizontal {
    setPadding(0, dp(10), 0, dp(10))
    if (iconRes != null) {
        addView(iconDisc(iconRes, iconTint), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
    }
    addView(
        vertical {
            addView(body(title).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 16f })
            if (subtitle != null) addView(body(subtitle, secondary = true, size = 13.5f).apply { setPadding(0, dp(2), 0, 0) })
            if (chip != null) {
                addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                })
            }
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

internal fun Context.divider(): View = View(this).apply {
    setBackgroundColor(hairline)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
        topMargin = dp(4)
        bottomMargin = dp(4)
    }
}

/** Soft orange call-out for something the user has to do; the action sits under the text. */
internal fun Context.callout(text: CharSequence, action: View? = null): LinearLayout = vertical {
    val p = dp(14)
    setPadding(p, p, p, p)
    background = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor((Palette.ORANGE and 0x00FFFFFF) or 0x1F000000)
    }
    addView(
        horizontal {
            gravity = Gravity.TOP
            addView(icon(com.gbhall.childlock.R.drawable.ic_warning, Palette.ORANGE, 22), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) })
            addView(body(text, size = 13.5f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        },
    )
    if (action != null) {
        addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
            topMargin = dp(8)
        })
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
            setLineSpacing(0f, 1.12f)
            setPadding(dp(10), dp(10), dp(4), dp(10))
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
    setPadding(0, dp(8), 0, dp(4))
    val label = body("$title: ${format(value)}").apply { typeface = Typeface.DEFAULT_BOLD; textSize = 16f }
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

package com.gbhall.childlock.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
}

/** Type scale: Display 28, Title 22, Section 17, Row 16, Body 14, Caption 12. */
object Type {
    const val DISPLAY = 28f
    const val TITLE = 22f
    const val SECTION = 17f
    const val ROW = 16f
    const val BODY = 14f
    const val CAPTION = 12f
}

enum class Tone(val glyph: String) {
    GOOD("✓"),
    ATTENTION("▲"),
    NEUTRAL("○"),
    ACTIVE("●"),
    PENDING("◐");

    /** For icons and large shapes, where the 3:1 non-text ratio applies. */
    fun color(context: Context): Int = when (this) {
        GOOD, ACTIVE -> context.accent
        ATTENTION, PENDING -> Palette.ORANGE
        NEUTRAL -> Palette.GREY
    }

    /** For small text on a tint of the same colour, where 4.5:1 applies. */
    fun textColor(context: Context): Int = if (context.isNight) {
        when (this) {
            GOOD, ACTIVE -> 0xFF9CD3F2.toInt()
            ATTENTION, PENDING -> 0xFFFFD37A.toInt()
            NEUTRAL -> 0xFFC2C5CC.toInt()
        }
    } else {
        when (this) {
            GOOD, ACTIVE -> 0xFF005B8F.toInt()
            ATTENTION, PENDING -> 0xFF6E4600.toInt()
            NEUTRAL -> 0xFF4F525A.toInt()
        }
    }
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

internal val Context.isNight: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

internal val Context.pageBackground: Int get() = if (isNight) 0xFF121317.toInt() else 0xFFF2F4F8.toInt()
internal val Context.cardBackground: Int get() = if (isNight) 0xFF1E2027.toInt() else Color.WHITE
internal val Context.hairline: Int get() = if (isNight) 0xFF34363E.toInt() else 0xFFE4E6EC.toInt()
internal val Context.accent: Int get() = if (isNight) Palette.SKY else Palette.BLUE
internal val Context.textPrimary: Int get() = themeColor(android.R.attr.textColorPrimary)
internal val Context.textSecondary: Int get() = themeColor(android.R.attr.textColorSecondary)

internal fun tint(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

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

internal fun Context.card(title: String?, iconRes: Int? = null, trailing: View? = null, build: LinearLayout.() -> Unit): LinearLayout =
    vertical {
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(cardBackground)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
        if (title != null) {
            addView(
                horizontal {
                    if (iconRes != null) addView(icon(iconRes, accent, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
                    addView(heading(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (trailing != null) addView(trailing)
                }.apply { setPadding(0, 0, 0, dp(10)) },
            )
        }
        build()
    }

internal fun Context.pageTitle(text: String): LinearLayout = horizontal {
    setPadding(dp(6), dp(6), dp(6), dp(20))
    addView(icon(com.gbhall.childlock.R.drawable.ic_lock, accent, 22), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) })
    addView(TextView(context).apply {
        this.text = text
        textSize = Type.TITLE
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textPrimary)
    })
}

internal fun Context.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = Type.SECTION
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(textPrimary)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) isAccessibilityHeading = true
}

internal fun Context.body(text: CharSequence, secondary: Boolean = false, size: Float = Type.BODY): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setLineSpacing(0f, 1.25f)
    setTextColor(if (secondary) textSecondary else textPrimary)
}

internal fun Context.label(text: CharSequence): TextView = body(text, size = Type.ROW).apply { typeface = Typeface.DEFAULT_BOLD }

internal fun Context.caption(text: CharSequence): TextView = body(text, secondary = true, size = Type.CAPTION)

internal fun Context.icon(resId: Int, tint: Int, sizeDp: Int = 24): ImageView = ImageView(this).apply {
    setImageResource(resId)
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    imageTintList = ColorStateList.valueOf(tint)
    scaleType = ImageView.ScaleType.FIT_CENTER
    layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Icon inside a soft tinted disc. */
internal fun Context.iconDisc(resId: Int, tint: Int, discDp: Int = 44, iconDp: Int = 22, discAlpha: Int = 0x1F): FrameLayout = FrameLayout(this).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(tint(tint, discAlpha))
    }
    addView(icon(resId, tint, iconDp), FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
    layoutParams = ViewGroup.LayoutParams(dp(discDp), dp(discDp))
}

/** Rounded pill: glyph + word in the tone's colour on a tinted background. */
internal fun Context.chip(text: String, tone: Tone): TextView = TextView(this).apply {
    val c = tone.color(context)
    this.text = SpannableStringBuilder().apply {
        append(tone.glyph, RelativeSizeSpan(0.9f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(" ").append(text)
    }
    // The glyph is decoration for sighted users; the word carries the meaning.
    contentDescription = text
    textSize = Type.CAPTION
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(tone.textColor(context))
    setPadding(dp(8), dp(4), dp(8), dp(4))
    minHeight = dp(24)
    background = GradientDrawable().apply {
        cornerRadius = dp(999).toFloat()
        setColor(tint(c, 0x22))
    }
    gravity = Gravity.CENTER_VERTICAL
}

/** Full-width primary action; when disabled, a quiet tonal "not yet" rather than a grey slab. */
internal fun Context.primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    minHeight = dp(56)
    minimumHeight = dp(56)
    textSize = Type.ROW
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = false
    stateListAnimator = null
    setTextColor(ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(if (isNight) 0xFF8C8C91.toInt() else 0xFF6B6E76.toInt(), Color.WHITE),
    ))
    background = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(Color.WHITE)
    }
    backgroundTintList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(if (isNight) 0xFF2A2C34.toInt() else 0xFFE1E3E9.toInt(), Palette.BLUE),
    )
    setOnClickListener { onClick() }
}

/** Compact secondary action: tonal pill. */
internal fun Context.actionButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    textSize = Type.BODY
    isAllCaps = false
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(accent)
    stateListAnimator = null
    minWidth = 0
    minimumWidth = 0
    minHeight = dp(48)
    minimumHeight = dp(48)
    setPadding(dp(16), dp(10), dp(16), dp(10))
    background = GradientDrawable().apply {
        cornerRadius = dp(999).toFloat()
        setColor(tint(accent, 0x1A))
    }
    setOnClickListener { onClick() }
}

/** Two-option segmented control. */
internal fun Context.segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout = horizontal {
    val track = this
    background = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(tint(accent, 0x14))
    }
    setPadding(dp(4), dp(4), dp(4), dp(4))
    val buttons = ArrayList<TextView>()
    fun render(sel: Int) {
        buttons.forEachIndexed { i, b ->
            val on = i == sel
            b.isSelected = on
            b.setTextColor(if (on) Color.WHITE else accent)
            b.background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(if (on) Palette.BLUE else Color.TRANSPARENT)
            }
        }
    }
    labels.forEachIndexed { i, label ->
        val b = TextView(context).apply {
            text = label
            textSize = Type.BODY
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            // Announced as a radio button, so selection is not colour-only.
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            }
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

/** Leading icon, title (+ inline status chip) and subtitle, optional trailing action. */
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
            addView(
                horizontal {
                    addView(label(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (chip != null) addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
                },
            )
            if (subtitle != null) addView(body(subtitle, secondary = true).apply { setPadding(0, dp(2), 0, 0) })
        },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    if (action != null) {
        addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(12)
        })
    }
}

internal fun Context.styledSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
    isChecked = checked
    // Opaque tokens: the translucent defaults fell below the 3:1 non-text ratio.
    val offTrack = if (isNight) 0xFF6B6E76.toInt() else 0xFF8C8F97.toInt()
    val offThumb = if (isNight) 0xFFC7C9D0.toInt() else Color.WHITE
    val onTrack = if (isNight) 0xFF2E6C8E.toInt() else 0xFF7FB6D6.toInt()
    thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Palette.BLUE, offThumb))
    trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(onTrack, offTrack))
    setOnCheckedChangeListener { _, value -> onChange(value) }
}

internal fun Context.switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    val sw = styledSwitch(checked, onChange).apply { contentDescription = title }
    val r = row(title, subtitle, sw)
    // One focus stop that reads the title and toggles, instead of three.
    r.isClickable = true
    r.isFocusable = true
    r.contentDescription = if (subtitle == null) title else "$title. $subtitle"
    r.setOnClickListener { sw.toggle() }
    return r
}

internal fun Context.divider(): View = View(this).apply {
    setBackgroundColor(hairline)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
        topMargin = dp(4)
        bottomMargin = dp(4)
    }
}

/** Orange call-out for something the parent has to do now: leading bar, body text, action underneath. */
internal fun Context.callout(text: CharSequence, action: View? = null): LinearLayout = vertical {
    setPadding(dp(14), dp(12), dp(14), dp(12))
    background = LayerDrawable(arrayOf(
        GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(tint(Palette.ORANGE, 0x14))
            setStroke(dp(1), tint(Palette.ORANGE, 0x66))
        },
        GradientDrawable().apply {
            cornerRadius = dp(2).toFloat()
            setColor(Palette.ORANGE)
        },
    )).apply { setLayerInset(1, 0, dp(10), 0, dp(10)); setLayerWidth(1, dp(3)); setLayerGravity(1, Gravity.START) }
    addView(
        horizontal {
            gravity = Gravity.TOP
            addView(icon(com.gbhall.childlock.R.drawable.ic_warning, Palette.ORANGE, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10); marginStart = dp(6) })
            addView(body(text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        },
    )
    if (action != null) {
        addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
            topMargin = dp(8)
        })
    }
}

/** Radio list: top-aligned tinted radios, bold label, body-size secondary description. */
internal fun Context.radioGroup(
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
): RadioGroup = RadioGroup(this).apply {
    orientation = RadioGroup.VERTICAL
    val secondary = textSecondary
    val tintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, Palette.GREY))
    options.forEachIndexed { index, (label, description) ->
        val button = RadioButton(context).apply {
            id = View.generateViewId()
            text = SpannableStringBuilder().apply {
                append(label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (description != null) {
                    append("\n")
                    val start = length
                    append(description)
                    setSpan(RelativeSizeSpan(0.875f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(secondary), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            textSize = Type.ROW
            setLineSpacing(0f, 1.2f)
            gravity = Gravity.TOP
            buttonTintList = tintList
            setPadding(dp(10), dp(8), dp(4), dp(8))
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
    val text = label("$title: ${format(value)}")
    addView(text)
    val seek = SeekBar(context).apply {
        this.min = min
        this.max = max
        progress = value
        progressTintList = ColorStateList.valueOf(accent)
        thumbTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(hairline)
        contentDescription = title
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) stateDescription = format(value)
        minimumHeight = dp(48)
        setPadding(dp(8), dp(8), dp(8), dp(4))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, p: Int, fromUser: Boolean) {
                text.text = "$title: ${format(p)}"
                seekBar.contentDescription = title
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    seekBar.stateDescription = format(p)
                }
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }
    addView(seek)
}

/** Collapsible section header with a chevron; [content] starts hidden. */
internal fun Context.expander(title: String, iconRes: Int?, content: View): LinearLayout = vertical {
    content.visibility = View.GONE
    val chevron = icon(com.gbhall.childlock.R.drawable.ic_expand, accent, 24)
    addView(
        horizontal {
            if (iconRes != null) addView(icon(iconRes, accent, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(heading(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron, LinearLayout.LayoutParams(dp(24), dp(24)))
            isClickable = true
            isFocusable = true
            minimumHeight = dp(48)
            fun describe(open: Boolean) {
                contentDescription = context.getString(
                    if (open) com.gbhall.childlock.R.string.a11y_collapse else com.gbhall.childlock.R.string.a11y_expand,
                    title,
                )
            }
            describe(false)
            setOnClickListener {
                val open = content.visibility != View.VISIBLE
                content.visibility = if (open) View.VISIBLE else View.GONE
                chevron.setImageResource(if (open) com.gbhall.childlock.R.drawable.ic_collapse else com.gbhall.childlock.R.drawable.ic_expand)
                describe(open)
                if (open) content.requestFocus()
                announceForAccessibility(contentDescription)
            }
        },
    )
    addView(content)
}

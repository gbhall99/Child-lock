package com.gbhall.childlock.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.gesture.CornerPair
import com.gbhall.childlock.gesture.PinHasher
import com.gbhall.childlock.gesture.VolumePattern
import com.gbhall.childlock.guard.ForegroundTracker
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureText
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

class MainActivity : Activity() {
    private lateinit var repo: SettingsRepository

    // Hero
    private lateinit var heroDisc: FrameLayout
    private lateinit var heroIcon: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var lockHow: TextView
    private lateinit var unlockHow: TextView
    private lateinit var guardWarning: View
    private lateinit var armButton: Button
    private lateinit var armHint: TextView

    // Setup
    private lateinit var overlayChip: LinearLayout
    private lateinit var overlayAction: View
    private lateinit var restrictedHint: View
    private lateinit var guardChip: LinearLayout
    private lateinit var guardAction: View
    private lateinit var notificationRow: View
    private lateinit var notificationChip: LinearLayout
    private lateinit var notificationAction: View
    private lateinit var shortcutHint: View

    // Gesture
    private lateinit var sequenceSection: View
    private lateinit var holdSection: View
    private lateinit var cornerPairSection: View
    private lateinit var pinSection: View
    private lateinit var pinChip: LinearLayout

    private val stateListener: (LockState) -> Unit = { renderStatus(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        setContentView(buildContent(repo.load()))
        LockController.addListener(stateListener)
    }

    override fun onResume() {
        super.onResume()
        if (!repo.setupDismissed && SetupActivity.isNeeded(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        refreshPermissions()
        renderStatus(LockController.state)
    }

    override fun onDestroy() {
        LockController.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
    }

    // ---- screen -----------------------------------------------------------

    private fun buildContent(s: LockSettings): View {
        val page = vertical {
            val p = dp(16)
            setPadding(p, dp(12), p, dp(24))
            addView(titleBar())
            addView(heroCard(s))
            addView(setupCard())
            addView(gestureCard(s))
            addView(advancedCard(s))
            addView(body(getString(R.string.safety_note), secondary = true, size = 13f).apply {
                setPadding(dp(6), dp(4), dp(6), 0)
            })
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pageBackground)
            addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Edge-to-edge: keep content clear of the status and navigation bars.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val i = LockOverlayService.systemInsets(insets)
            page.setPadding(dp(16) + i[0], dp(12) + i[1], dp(16) + i[2], dp(24) + i[3])
            insets
        }
        return scroll
    }

    private fun titleBar() = horizontal {
        setPadding(dp(6), dp(8), dp(6), dp(14))
        addView(icon(R.drawable.ic_lock, accent, 26), LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) })
        addView(TextView(context).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(themeColor(android.R.attr.textColorPrimary))
        })
    }

    private fun heroCard(s: LockSettings) = card(null) {
        addView(
            horizontal {
                heroDisc = iconDisc(R.drawable.ic_lock_open, Palette.GREY, discDp = 68, iconDp = 34)
                heroIcon = heroDisc.getChildAt(0) as ImageView
                addView(heroDisc, LinearLayout.LayoutParams(dp(68), dp(68)).apply { marginEnd = dp(16) })
                addView(
                    vertical {
                        heroTitle = TextView(context).apply {
                            textSize = 28f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(themeColor(android.R.attr.textColorPrimary))
                        }
                        addView(heroTitle)
                        heroSubtitle = body("", secondary = true, size = 14f)
                        addView(heroSubtitle)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
        )

        addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) })
        lockHow = body("", size = 14.5f)
        unlockHow = body("", size = 14.5f)
        addView(stepRow(R.drawable.ic_lock, getString(R.string.how_to_lock), lockHow))
        addView(stepRow(R.drawable.ic_lock_open, getString(R.string.how_to_unlock), unlockHow))

        guardWarning = callout(getString(R.string.guard_warning), actionButton(getString(R.string.fix)) {
            startActivity(GuardAccessibilityService.settingsIntent(this@MainActivity))
        })
        addView(guardWarning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        armButton = primaryButton(getString(R.string.arm_button, s.armDelaySec)) { arm() }
        addView(armButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply { topMargin = dp(16) })
        armHint = body(getString(R.string.arm_hint), secondary = true, size = 13f).apply { setPadding(dp(4), dp(8), dp(4), 0) }
        addView(armHint)
    }

    private fun stepRow(iconRes: Int, label: String, value: TextView) = horizontal {
        setPadding(0, dp(8), 0, dp(4))
        gravity = Gravity.TOP
        addView(icon(iconRes, accent, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12); topMargin = dp(2) })
        addView(
            vertical {
                addView(body(label, secondary = true, size = 12f).apply { isAllCaps = true; letterSpacing = 0.06f })
                addView(value)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun chipHolder() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun setChip(holder: LinearLayout, text: String, tone: Tone) {
        holder.removeAllViews()
        holder.addView(chip(text, tone))
    }

    private fun setupCard() = card(getString(R.string.section_setup), R.drawable.ic_tune) {
        overlayChip = chipHolder()
        overlayAction = actionButton(getString(R.string.grant)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        addView(row(getString(R.string.perm_overlay), getString(R.string.perm_overlay_desc), overlayAction, overlayChip, R.drawable.ic_layers))
        restrictedHint = callout(getString(R.string.restricted_desc), actionButton(getString(R.string.app_info)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        addView(restrictedHint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })
        addView(divider())

        guardChip = chipHolder()
        guardAction = actionButton(getString(R.string.enable)) { startActivity(GuardAccessibilityService.settingsIntent(this@MainActivity)) }
        addView(row(getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_desc), guardAction, guardChip, R.drawable.ic_shield))
        addView(divider())

        notificationChip = chipHolder()
        notificationAction = actionButton(getString(R.string.allow)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
        notificationRow = row(getString(R.string.perm_notifications), getString(R.string.perm_notifications_desc), notificationAction, notificationChip, R.drawable.ic_bell)
        addView(notificationRow)
        shortcutHint = callout(getString(R.string.shortcut_button_desc), actionButton(getString(R.string.open)) {
            startActivity(GuardAccessibilityService.settingsIntent(this@MainActivity))
        })
        addView(shortcutHint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        addView(divider())
        addView(row(getString(R.string.setup_assistant), getString(R.string.setup_assistant_desc), actionButton(getString(R.string.open)) {
            repo.setupDismissed = false
            startActivity(Intent(this@MainActivity, SetupActivity::class.java))
        }, null, R.drawable.ic_check))
    }

    private fun gestureCard(s: LockSettings) = card(getString(R.string.section_gesture), R.drawable.ic_touch) {
        val gestures = GestureType.entries
        addView(
            radioGroup(
                listOf(
                    getString(R.string.gesture_volume_sequence) to getString(R.string.gesture_volume_sequence_desc),
                    getString(R.string.gesture_corner_hold) to getString(R.string.gesture_corner_hold_desc),
                    getString(R.string.gesture_badge_pin) to getString(R.string.gesture_badge_pin_desc),
                    getString(R.string.gesture_volume_chord) to getString(R.string.gesture_volume_chord_desc),
                ),
                gestures.indexOf(s.gesture),
            ) { index ->
                repo.update { it.copy(gesture = gestures[index]) }
                renderGestureDependents(repo.load())
            },
        )

        sequenceSection = vertical {
            addView(divider())
            addView(body(getString(R.string.volume_pattern), size = 16f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(6), 0, dp(10)) })
            val patterns = VolumePattern.entries
            addView(
                segmented(
                    listOf(getString(R.string.pattern_option_up_down), getString(R.string.pattern_option_down_up)),
                    patterns.indexOf(s.volumePattern),
                ) { index ->
                    repo.update { it.copy(volumePattern = patterns[index]) }
                    renderGestureDependents(repo.load())
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(switchRow(getString(R.string.pattern_twice_title), getString(R.string.pattern_twice_desc), s.volumeRepeats >= 2) { v ->
                repo.update { it.copy(volumeRepeats = if (v) 2 else 1) }
                renderGestureDependents(repo.load())
            })
        }
        addView(sequenceSection)

        holdSection = seekRow(
            getString(R.string.hold_duration),
            (LockSettings.MIN_HOLD_MS / 100).toInt(), (LockSettings.MAX_HOLD_MS / 100).toInt(), (s.holdMs / 100).toInt(),
            format = { String.format("%.1f s", it / 10f) },
        ) { tenths -> repo.update { it.copy(holdMs = tenths * 100L) } }
        addView(holdSection)

        cornerPairSection = vertical {
            addView(body(getString(R.string.corner_pair), size = 16f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, 0) })
            val pairs = CornerPair.entries
            addView(
                radioGroup(
                    listOf(getString(R.string.pair_tl_br) to null, getString(R.string.pair_tr_bl) to null),
                    pairs.indexOf(s.cornerPair),
                ) { index -> repo.update { it.copy(cornerPair = pairs[index]) } },
            )
        }
        addView(cornerPairSection)

        pinSection = vertical {
            pinChip = chipHolder()
            addView(row(getString(R.string.pin_title), getString(R.string.pin_desc), actionButton(getString(R.string.pin_set)) { showPinDialog() }, pinChip))
        }
        addView(pinSection)
    }

    private fun advancedCard(s: LockSettings): LinearLayout {
        val content = vertical {
            visibility = View.GONE
            addView(divider())
            addView(
                seekRow(
                    getString(R.string.arm_delay), LockSettings.MIN_ARM_DELAY_SEC, LockSettings.MAX_ARM_DELAY_SEC, s.armDelaySec,
                    format = { "$it s" },
                ) { sec ->
                    repo.update { it.copy(armDelaySec = sec) }
                    armButton.text = getString(R.string.arm_button, sec)
                },
            )
            addView(switchRow(getString(R.string.keep_screen_on), getString(R.string.keep_screen_on_desc), s.keepScreenOn) { v ->
                repo.update { it.copy(keepScreenOn = v) }
            })
            addView(body(getString(R.string.badge_corner), size = 16f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, 0) })
            val corners = Corner.entries
            addView(
                radioGroup(
                    listOf(
                        getString(R.string.corner_tl) to null,
                        getString(R.string.corner_tr) to null,
                        getString(R.string.corner_bl) to null,
                        getString(R.string.corner_br) to null,
                    ),
                    corners.indexOf(s.badgeCorner),
                ) { index -> repo.update { it.copy(badgeCorner = corners[index]) } },
            )
            addView(divider())
            addView(body(getString(R.string.hardening_note), secondary = true, size = 13f).apply { setPadding(0, dp(6), 0, 0) })
            addView(switchRow(getString(R.string.block_gestures), getString(R.string.block_gestures_desc), s.blockGestures) { v ->
                repo.update { it.copy(blockGestures = v) }
            })
            addView(switchRow(getString(R.string.block_keys), getString(R.string.block_keys_desc), s.blockKeys) { v ->
                repo.update { it.copy(blockKeys = v) }
            })
            addView(switchRow(getString(R.string.block_shade), getString(R.string.block_shade_desc), s.blockShade) { v ->
                repo.update { it.copy(blockShade = v) }
            })
            addView(switchRow(getString(R.string.relaunch_app), getString(R.string.relaunch_app_desc), s.relaunchApp) { v ->
                repo.update { it.copy(relaunchApp = v) }
            })
        }
        return card(null) {
            val chevron = icon(R.drawable.ic_expand, accent, 24)
            val header = horizontal {
                addView(icon(R.drawable.ic_tune, accent, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
                addView(heading(getString(R.string.section_advanced)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(chevron, LinearLayout.LayoutParams(dp(24), dp(24)))
                isClickable = true
                setOnClickListener {
                    val open = content.visibility != View.VISIBLE
                    content.visibility = if (open) View.VISIBLE else View.GONE
                    chevron.setImageResource(if (open) R.drawable.ic_collapse else R.drawable.ic_expand)
                }
            }
            addView(header)
            addView(content)
        }
    }

    // ---- behaviour --------------------------------------------------------

    private fun arm() {
        val s = repo.load()
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_no_overlay_permission)
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (s.gesture == GestureType.BADGE_PIN && !s.hasPin) {
            toast(R.string.toast_need_pin)
            return
        }
        if (!LockController.requestLock(this, ForegroundTracker.lastApp, s.armDelaySec * 1000L)) {
            toast(R.string.toast_lock_failed)
            return
        }
        // Send this task behind the previous one so the parent lands back on the call.
        moveTaskToBack(true)
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.pin_hint, LockSettings.MIN_PIN_LENGTH, LockSettings.MAX_PIN_LENGTH)
            val p = dp(20)
            setPadding(p, p, p, p)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val pin = input.text.toString()
                if (pin.length !in LockSettings.MIN_PIN_LENGTH..LockSettings.MAX_PIN_LENGTH || !pin.all { it.isDigit() }) {
                    toast(R.string.toast_pin_invalid)
                } else {
                    repo.update { it.copy(pinHash = PinHasher.hash(pin), pinLength = pin.length) }
                    renderGestureDependents(repo.load())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshPermissions() {
        val overlay = Settings.canDrawOverlays(this)
        setChip(overlayChip, getString(if (overlay) R.string.status_granted else R.string.status_needed), if (overlay) Tone.GOOD else Tone.ATTENTION)
        overlayAction.visibility = if (overlay) View.GONE else View.VISIBLE
        armButton.isEnabled = overlay

        val a11y = GuardAccessibilityService.isEnabled(this)
        setChip(guardChip, getString(if (a11y) R.string.status_enabled else R.string.status_off), if (a11y) Tone.GOOD else Tone.ATTENTION)
        guardAction.visibility = if (a11y) View.GONE else View.VISIBLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            setChip(notificationChip, getString(if (granted) R.string.status_granted else R.string.status_recommended), if (granted) Tone.GOOD else Tone.NEUTRAL)
            notificationAction.visibility = if (granted) View.GONE else View.VISIBLE
        } else {
            notificationRow.visibility = View.GONE
        }

        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!overlay || !a11y)
        restrictedHint.visibility = if (restricted) View.VISIBLE else View.GONE
        shortcutHint.visibility = if (a11y && GuardAccessibilityService.isShortcutButtonOn(this)) View.VISIBLE else View.GONE
        renderGestureDependents(repo.load())
    }

    private fun renderStatus(state: LockState) {
        val (title, subtitle, tone, iconRes) = when (state) {
            LockState.Unlocked -> Quad(R.string.state_unlocked, R.string.state_unlocked_sub, Tone.NEUTRAL, R.drawable.ic_lock_open)
            is LockState.Arming -> Quad(R.string.state_arming, R.string.state_arming_sub, Tone.PENDING, R.drawable.ic_lock)
            is LockState.Locked -> Quad(R.string.state_locked, R.string.state_locked_sub, Tone.ACTIVE, R.drawable.ic_lock)
        }
        val c = tone.color(this)
        heroTitle.text = getString(title)
        heroSubtitle.text = getString(subtitle)
        heroIcon.setImageResource(iconRes)
        heroIcon.imageTintList = android.content.res.ColorStateList.valueOf(c)
        (heroDisc.background as android.graphics.drawable.GradientDrawable).setColor((c and 0x00FFFFFF) or 0x24000000)
    }

    private data class Quad(val title: Int, val subtitle: Int, val tone: Tone, val icon: Int)

    private fun renderGestureDependents(s: LockSettings) {
        val isSequence = s.gesture == GestureType.VOLUME_SEQUENCE
        val isPin = s.gesture == GestureType.BADGE_PIN
        sequenceSection.visibility = if (isSequence) View.VISIBLE else View.GONE
        holdSection.visibility = if (isSequence) View.GONE else View.VISIBLE
        cornerPairSection.visibility = if (isPin || isSequence) View.GONE else View.VISIBLE
        pinSection.visibility = if (isPin) View.VISIBLE else View.GONE
        setChip(
            pinChip,
            if (s.hasPin) getString(R.string.pin_status_set, s.pinLength) else getString(R.string.pin_status_unset),
            if (s.hasPin) Tone.GOOD else Tone.ATTENTION,
        )
        lockHow.text = GestureText.lockHint(this, s)
        unlockHow.text = GestureText.unlockHint(this, s)
        guardWarning.visibility = if (s.gesture.needsGuard && !GuardAccessibilityService.isEnabled(this)) View.VISIBLE else View.GONE
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
    }
}

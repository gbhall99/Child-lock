package com.gbhall.childlock.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.billing.FeatureGate
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

    // Status
    private lateinit var heroDisc: FrameLayout
    private lateinit var heroIcon: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var armButton: Button

    // Needs attention
    private lateinit var attentionCard: View
    private lateinit var overlayRow: View
    private lateinit var helperRow: View
    private lateinit var restrictedHint: View
    private lateinit var shortcutHint: View
    private lateinit var notificationRow: View

    // How it works
    private lateinit var lockHow: TextView
    private lateinit var unlockHow: TextView

    // Unlock
    private lateinit var gestureGroup: RadioGroup
    private lateinit var sequenceSection: View
    private lateinit var holdSection: View
    private lateinit var cornerPairSection: View
    private lateinit var pinSection: View
    private lateinit var pinChip: LinearLayout
    private lateinit var fallbackNote: TextView

    // Auto-lock
    private lateinit var autoLockList: LinearLayout
    private lateinit var autoLockNote: View
    private lateinit var proChipHolder: LinearLayout

    /** The guide is offered once per visit, not every time this screen resumes. */
    private var setupShown = false

    private val stateListener: (LockState) -> Unit = { renderStatus(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        setContentView(buildContent(repo.load()))
        LockController.addListener(stateListener)
    }

    override fun onResume() {
        super.onResume()
        if (!setupShown && !repo.setupDismissed && SetupActivity.isNeeded(this)) {
            setupShown = true
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        refreshPermissions()
        renderStatus(LockController.state)
        renderAutoLock(repo.load())
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
            setPadding(dp(16), dp(12), dp(16), dp(24))
            addView(pageTitle(getString(R.string.app_name)))
            addView(statusCard(s))
            addView(attentionCard())
            addView(howCard())
            addView(gestureCard(s))
            addView(autoLockCard(s))
            addView(advancedCard(s))
            addView(aboutCard())
            addView(caption(getString(R.string.safety_note)).apply { setPadding(dp(6), dp(4), dp(6), 0) })
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pageBackground)
            addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val i = LockOverlayService.systemInsets(insets)
            page.setPadding(dp(16) + i[0], dp(12) + i[1], dp(16) + i[2], dp(24) + i[3])
            insets
        }
        return scroll
    }

    private fun statusCard(s: LockSettings) = card(null) {
        addView(
            horizontal {
                heroDisc = iconDisc(R.drawable.ic_lock_open, accent, discDp = 68, iconDp = 34, discAlpha = 0x14)
                heroIcon = heroDisc.getChildAt(0) as ImageView
                addView(heroDisc, LinearLayout.LayoutParams(dp(68), dp(68)).apply { marginEnd = dp(16) })
                addView(
                    vertical {
                        heroTitle = TextView(context).apply {
                            textSize = Type.DISPLAY
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(textPrimary)
                        }
                        addView(heroTitle)
                        heroSubtitle = body("", secondary = true)
                        addView(heroSubtitle)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
        )
        armButton = primaryButton(getString(R.string.arm_button, s.armDelaySec)) { arm() }
        addView(armButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(18) })
        addView(caption(getString(R.string.arm_hint)).apply { setPadding(dp(4), dp(8), dp(4), 0) })
        addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
        addView(
            seekRow(
                getString(R.string.session_title), 0, LockSettings.MAX_SESSION_MINUTES / 5, s.sessionMinutes / 5,
                format = { if (it == 0) getString(R.string.session_off) else getString(R.string.session_minutes, it * 5) },
            ) { steps -> repo.update { it.copy(sessionMinutes = steps * 5) } },
        )
        addView(caption(getString(R.string.session_desc)))
        addView(row(getString(R.string.rehearse_title), getString(R.string.rehearse_desc), actionButton(getString(R.string.rehearse_go)) { rehearse() }, null, R.drawable.ic_touch))
    }

    /**
     * Locks straight away without leaving the app, so the parent can practise
     * getting out. A parent who has unlocked once will not panic later.
     */
    private fun rehearse() {
        val s = repo.load()
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_no_overlay_permission)
            return
        }
        if (preflight(s) != null) return
        if (!LockController.requestLock(this, null, 0, rehearsal = true)) toast(R.string.toast_lock_failed)
    }

    private fun attentionCard(): View {
        attentionCard = card(getString(R.string.section_attention), R.drawable.ic_warning) {
            overlayRow = row(
                getString(R.string.perm_overlay), getString(R.string.perm_overlay_desc),
                actionButton(getString(R.string.turn_on)) {
                    repo.overlayAttempted = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                },
                null, R.drawable.ic_layers, Palette.ORANGE,
            )
            addView(overlayRow)
            helperRow = row(
                getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_desc),
                actionButton(getString(R.string.turn_on)) {
                    Disclosures.accessibility(this@MainActivity) {
                        repo.overlayAttempted = true
                        startActivity(GuardAccessibilityService.settingsIntent(this@MainActivity))
                    }
                },
                null, R.drawable.ic_shield, Palette.ORANGE,
            )
            addView(helperRow)
            restrictedHint = callout(getString(R.string.restricted_desc), actionButton(getString(R.string.app_info)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
            addView(restrictedHint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            notificationRow = row(
                getString(R.string.perm_notifications), getString(R.string.setup_notifications_desc),
                actionButton(getString(R.string.allow)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                    }
                },
                chip(getString(R.string.status_optional), Tone.NEUTRAL), R.drawable.ic_bell, Palette.GREY,
            )
            addView(notificationRow)
            shortcutHint = callout(getString(R.string.shortcut_button_desc), actionButton(getString(R.string.open)) {
                startActivity(GuardAccessibilityService.settingsIntent(this@MainActivity))
            })
            addView(shortcutHint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        return attentionCard
    }

    private fun howCard() = card(getString(R.string.section_how), R.drawable.ic_touch) {
        lockHow = body("")
        unlockHow = body("")
        addView(stepRow(R.drawable.ic_lock, getString(R.string.how_to_lock), lockHow))
        addView(stepRow(R.drawable.ic_lock_open, getString(R.string.how_to_unlock), unlockHow))
    }

    private fun stepRow(iconRes: Int, label: String, value: TextView) = horizontal {
        setPadding(0, dp(6), 0, dp(6))
        gravity = Gravity.TOP
        addView(icon(iconRes, accent, 20), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12); topMargin = dp(2) })
        addView(
            vertical {
                addView(caption(label).apply { isAllCaps = true; letterSpacing = 0.06f })
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

    private fun gestureCard(s: LockSettings) = card(getString(R.string.section_gesture), R.drawable.ic_volume) {
        val gestures = GestureType.entries
        gestureGroup = radioGroup(
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
        }
        addView(gestureGroup)
        // Only the recommended option shows until "Other ways" is opened, unless another is already chosen.
        val showAll = s.gesture != GestureType.VOLUME_SEQUENCE
        for (i in 1 until gestureGroup.childCount) gestureGroup.getChildAt(i).visibility = if (showAll) View.VISIBLE else View.GONE
        if (!showAll) {
            addView(actionButton(getString(R.string.section_other_unlock)) {
                for (i in 1 until gestureGroup.childCount) gestureGroup.getChildAt(i).visibility = View.VISIBLE
                (parent as? ViewGroup)?.removeView(this)
            }.also { it.tag = "otherWays" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4); marginStart = dp(10) })
        }

        sequenceSection = vertical {
            addView(divider())
            addView(label(getString(R.string.volume_pattern)).apply { setPadding(0, dp(6), 0, dp(10)) })
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
            fallbackNote = caption("")
            addView(fallbackNote)
        }
        addView(sequenceSection)

        holdSection = seekRow(
            getString(R.string.hold_duration),
            (LockSettings.MIN_HOLD_MS / 100).toInt(), (LockSettings.MAX_HOLD_MS / 100).toInt(), (s.holdMs / 100).toInt(),
            format = { String.format("%.1f s", it / 10f) },
        ) { tenths -> repo.update { it.copy(holdMs = tenths * 100L) } }
        addView(holdSection)

        cornerPairSection = vertical {
            addView(label(getString(R.string.corner_pair)).apply { setPadding(0, dp(8), 0, 0) })
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

    private fun autoLockCard(s: LockSettings): LinearLayout {
        proChipHolder = chipHolder()
        return card(getString(R.string.section_autolock), R.drawable.ic_layers, trailing = proChipHolder) {
            addView(body(getString(R.string.autolock_desc), secondary = true))
            autoLockList = vertical { setPadding(0, dp(6), 0, 0) }
            addView(autoLockList)
            addView(actionButton(getString(R.string.autolock_add)) {
                Paywall.require(this@MainActivity, FeatureGate.Feature.AUTO_LOCK) {
                    startActivity(Intent(this@MainActivity, AppPickerActivity::class.java))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(
                seekRow(
                    getString(R.string.autolock_delay), LockSettings.MIN_AUTO_LOCK_DELAY_SEC, LockSettings.MAX_AUTO_LOCK_DELAY_SEC, s.autoLockDelaySec,
                    format = { "$it s" },
                ) { sec -> repo.update { it.copy(autoLockDelaySec = sec) } },
            )
            addView(switchRow(getString(R.string.relock_title), getString(R.string.relock_desc), s.relockSameApp) { v ->
                Paywall.require(this@MainActivity, FeatureGate.Feature.RELOCK) { repo.update { it.copy(relockSameApp = v) } }
            })
            if (com.gbhall.childlock.guard.SkipAdFeature.AVAILABLE) {
                addView(switchRow(getString(R.string.skip_ads_title), getString(R.string.skip_ads_desc), s.skipAds) { v ->
                    Paywall.require(this@MainActivity, FeatureGate.Feature.SKIP_ADS) { repo.update { it.copy(skipAds = v) } }
                })
            }
            autoLockNote = callout(getString(R.string.autolock_note), actionButton(getString(R.string.turn_on)) {
                Disclosures.accessibility(this@MainActivity) { startActivity(GuardAccessibilityService.settingsIntent(this@MainActivity)) }
            })
            addView(autoLockNote, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }

    private fun renderAutoLock(s: LockSettings) {
        proChipHolder.removeAllViews()
        if (!FeatureGate.isPro(this)) proChipHolder.addView(chip(getString(R.string.pro_badge), Tone.ACTIVE))
        autoLockNote.visibility = if (GuardAccessibilityService.isEnabled(this)) View.GONE else View.VISIBLE
        val pm = packageManager
        autoLockList.removeAllViews()
        if (s.autoLockRules.isEmpty()) {
            autoLockList.addView(body(getString(R.string.autolock_none), secondary = true))
            return
        }
        s.autoLockRules.entries
            .map { (pkg, t) ->
                val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                Triple(pkg, info?.let { pm.getApplicationLabel(it).toString() } ?: pkg, info?.let { pm.getApplicationIcon(it) } to t)
            }
            .sortedBy { it.second.lowercase() }
            .forEach { (pkg, label, iconAndTrigger) ->
                val (icon, t) = iconAndTrigger
                autoLockList.addView(
                    horizontal {
                        setPadding(dp(4), dp(8), dp(4), dp(8))
                        isClickable = true
                        addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
                        addView(
                            vertical {
                                addView(label(label))
                                addView(body(getString(R.string.rule_summary, RuleEditor.label(this@MainActivity, t)), secondary = true))
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(actionButton(getString(R.string.rule_change)) {
                            RuleEditor.show(this@MainActivity, pkg, label) { renderAutoLock(repo.load()) }
                        })
                        setOnClickListener { RuleEditor.show(this@MainActivity, pkg, label) { renderAutoLock(repo.load()) } }
                    },
                )
            }
    }

    private fun advancedCard(s: LockSettings): LinearLayout {
        val fineTune = vertical {
            addView(switchRow(getString(R.string.block_gestures), getString(R.string.block_gestures_desc), s.blockGestures) { v -> repo.update { it.copy(blockGestures = v) } })
            addView(switchRow(getString(R.string.block_keys), getString(R.string.block_keys_desc), s.blockKeys) { v -> repo.update { it.copy(blockKeys = v) } })
            addView(switchRow(getString(R.string.block_shade), getString(R.string.block_shade_desc), s.blockShade) { v -> repo.update { it.copy(blockShade = v) } })
            addView(switchRow(getString(R.string.relaunch_app), getString(R.string.relaunch_app_desc), s.relaunchApp) { v -> repo.update { it.copy(relaunchApp = v) } })
        }
        val content = vertical {
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
            addView(switchRow(getString(R.string.keep_screen_on), getString(R.string.keep_screen_on_desc), s.keepScreenOn) { v -> repo.update { it.copy(keepScreenOn = v) } })
            addView(switchRow(getString(R.string.keep_orientation), getString(R.string.keep_orientation_desc), s.keepOrientation) { v -> repo.update { it.copy(keepOrientation = v) } })
            // Deliberately not blockGestures: that one costs the parent the corner-hold
            // unlock, so it stays an opt-in under Fine-tune rather than riding along here.
            val allInside = s.blockKeys && s.blockShade && s.relaunchApp
            addView(switchRow(getString(R.string.keep_inside_title), getString(R.string.keep_inside_desc), allInside) { v ->
                repo.update { it.copy(blockKeys = v, blockShade = v, relaunchApp = v) }
            })
            addView(expander(getString(R.string.keep_inside_more), null, fineTune).apply { setPadding(dp(10), 0, 0, 0) })
            addView(divider())
            addView(label(getString(R.string.badge_corner)).apply { setPadding(0, dp(8), 0, 0) })
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
            addView(row(getString(R.string.setup_assistant), getString(R.string.setup_assistant_desc), actionButton(getString(R.string.open)) {
                repo.setupDismissed = false
                startActivity(Intent(this@MainActivity, SetupActivity::class.java))
            }, null, R.drawable.ic_tune))
            if (FeatureGate.isDebuggable(this@MainActivity)) {
                addView(switchRow(getString(R.string.pro_preview_free), null, FeatureGate.isPreviewingFree(this@MainActivity)) { v ->
                    FeatureGate.setPreviewFree(this@MainActivity, v)
                    renderAutoLock(repo.load())
                })
            }
        }
        return card(null) { addView(expander(getString(R.string.section_advanced), R.drawable.ic_tune, content)) }
    }

    private fun aboutCard() = card(getString(R.string.section_about), R.drawable.ic_shield) {
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { null } ?: "?"
        addView(body(getString(R.string.about_privacy_summary)))
        addView(caption(getString(R.string.about_version, version)).apply { setPadding(0, dp(4), 0, dp(10)) })
        listOf(
            R.string.about_privacy to URL_PRIVACY,
            R.string.about_source to URL_SOURCE,
            R.string.about_licence to URL_LICENCE,
        ).forEach { (res, url) ->
            addView(actionButton(getString(res)) { open(url) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(R.string.toast_no_browser)
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
        if (preflight(s) != null) return
        if (!LockController.requestLock(this, ForegroundTracker.lastApp, s.armDelaySec * 1000L)) {
            toast(R.string.toast_lock_failed)
            return
        }
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
                    val salt = PinHasher.newSalt()
                    repo.update { it.copy(pinHash = PinHasher.hash(pin, salt), pinSalt = salt, pinLength = pin.length) }
                    renderGestureDependents(repo.load())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshPermissions() {
        val overlay = Settings.canDrawOverlays(this)
        val helper = GuardAccessibilityService.isEnabled(this)
        val shortcut = helper && GuardAccessibilityService.isShortcutButtonOn(this)
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && repo.overlayAttempted && (!overlay || !helper)
        val notify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        overlayRow.visibility = if (overlay) View.GONE else View.VISIBLE
        helperRow.visibility = if (helper) View.GONE else View.VISIBLE
        notificationRow.visibility = if (notify) View.GONE else View.VISIBLE
        restrictedHint.visibility = if (restricted) View.VISIBLE else View.GONE
        shortcutHint.visibility = if (shortcut) View.VISIBLE else View.GONE
        attentionCard.visibility = if (overlay && helper && notify && !shortcut) View.GONE else View.VISIBLE
        armButton.isEnabled = overlay
        renderGestureDependents(repo.load())
    }

    private fun renderStatus(state: LockState) {
        val (title, subtitle, tone, iconRes) = when (state) {
            LockState.Unlocked -> Quad(R.string.state_unlocked, R.string.state_unlocked_sub, Tone.NEUTRAL, R.drawable.ic_lock_open)
            is LockState.Arming -> Quad(R.string.state_arming, R.string.state_arming_sub, Tone.PENDING, R.drawable.ic_lock)
            is LockState.Locked -> Quad(R.string.state_locked, R.string.state_locked_sub, Tone.ACTIVE, R.drawable.ic_lock)
        }
        // Unlocked is a calm faint accent, not grey: grey reads as "broken".
        val iconColor = when (tone) {
            Tone.NEUTRAL -> if (isNight) 0xFFA9ACB5.toInt() else 0xFF5A5D66.toInt()
            else -> tone.color(this)
        }
        val discColor = when (tone) {
            Tone.NEUTRAL -> tint(accent, if (isNight) 0x1A else 0x14)
            else -> tint(tone.color(this), 0x2E)
        }
        heroTitle.text = getString(title)
        heroSubtitle.text = getString(subtitle)
        heroIcon.setImageResource(iconRes)
        heroIcon.imageTintList = ColorStateList.valueOf(iconColor)
        (heroDisc.background as GradientDrawable).setColor(discColor)
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
        unlockHow.text = GestureText.unlockHint(this, s) + " " + GestureText.fallbackHint(this, s)
        fallbackNote.text = GestureText.fallbackHint(this, s)
    }

    /** Returns the reason a lock would strand the parent, or null when it is safe. */
    private fun preflight(s: LockSettings): Int? {
        val reason = when (
            com.gbhall.childlock.lock.LockPreflight.check(
                s.gesture,
                GuardAccessibilityService.isConnected,
                screenReaderOn(),
                GuardAccessibilityService.keyToolActive,
            )
        ) {
            com.gbhall.childlock.lock.LockPreflight.Result.HelperNeeded -> R.string.toast_needs_helper
            com.gbhall.childlock.lock.LockPreflight.Result.ScreenReaderNeedsVolume -> R.string.toast_screen_reader
            com.gbhall.childlock.lock.LockPreflight.Result.SwitchAccessNeedsTouch -> R.string.toast_switch_access
            com.gbhall.childlock.lock.LockPreflight.Result.Ok -> null
        }
        if (reason != null) toast(reason)
        return reason
    }

    private fun screenReaderOn(): Boolean =
        try {
            getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        } catch (e: Exception) {
            false
        }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
        const val URL_SOURCE = "https://github.com/gbhall99/Child-lock"
        const val URL_PRIVACY = "https://github.com/gbhall99/Child-lock/blob/main/PRIVACY.md"
        const val URL_LICENCE = "https://github.com/gbhall99/Child-lock/blob/main/LICENSE"
    }
}

package com.gbhall.childlock.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.settings.GestureText
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

/** One page of settings, chosen by the tile tapped on the home screen. */
class SettingsActivity : Activity() {
    enum class Page(val titleRes: Int) {
        UNLOCK(R.string.section_gesture),
        AUTO_LOCK(R.string.section_autolock),
        INSIDE(R.string.section_inside),
        TIMER(R.string.session_title),
        MORE(R.string.section_advanced),
        ABOUT(R.string.section_about),
    }

    private lateinit var repo: SettingsRepository
    lateinit var page: Page
        private set

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        page = intent.getStringExtra(EXTRA_PAGE)?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.UNLOCK
        val s = repo.load()
        val content = vertical {
            addView(toolbar(getString(page.titleRes)) { finish() }.apply { setPadding(0, dp(4), 0, dp(12)) })
            when (page) {
                Page.UNLOCK -> unlockPage(s)
                Page.AUTO_LOCK -> autoLockPage(s)
                Page.INSIDE -> insidePage(s)
                Page.TIMER -> timerPage(s)
                Page.MORE -> morePage(s)
                Page.ABOUT -> aboutPage()
            }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pageBackground)
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.setPadding(dp(12), dp(8), dp(12), dp(24))
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val i = LockOverlayService.systemInsets(insets)
            content.setPadding(dp(12) + i[0], dp(8) + i[1], dp(12) + i[2], dp(24) + i[3])
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        when (page) {
            Page.UNLOCK -> renderGestureDependents(repo.load())
            Page.AUTO_LOCK -> renderAutoLock(repo.load())
            else -> Unit
        }
    }

    // ---- Unlock -------------------------------------------------------------

    private val gestureRows = LinkedHashMap<GestureType, LinearLayout>()

    private fun LinearLayout.unlockPage(s: LockSettings) {
        addView(
            card(null) {
                val titles = mapOf(
                    GestureType.VOLUME_SEQUENCE to (R.string.gesture_volume_sequence to R.string.gesture_volume_sequence_desc),
                    GestureType.CORNER_HOLD to (R.string.gesture_corner_hold to R.string.gesture_corner_hold_desc),
                    GestureType.BADGE_PIN to (R.string.gesture_badge_pin to R.string.gesture_badge_pin_desc),
                    GestureType.VOLUME_CHORD to (R.string.gesture_volume_chord to R.string.gesture_volume_chord_desc),
                )
                for (g in GestureType.entries) {
                    val (title, desc) = titles.getValue(g)
                    val r = switchRow(getString(title), getString(desc), g in s.gestures) { on -> setGesture(g, on) }
                    gestureRows[g] = r
                    addView(r)
                }
                // Only the recommended way shows until "More ways" is opened, unless another is already allowed.
                val showAll = s.gestures != setOf(GestureType.VOLUME_SEQUENCE)
                val others = gestureRows.filterKeys { it != GestureType.VOLUME_SEQUENCE }.values
                others.forEach { it.visibility = if (showAll) View.VISIBLE else View.GONE }
                if (!showAll) {
                    lateinit var moreWays: View
                    moreWays = actionButton(getString(R.string.section_other_unlock)) {
                        others.forEach { it.visibility = View.VISIBLE }
                        // The button, not the card it sits in: inside this lambda "this" is the card.
                        (moreWays.parent as? ViewGroup)?.removeView(moreWays)
                    }
                    addView(moreWays, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4); marginStart = dp(10) })
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
                    pinChip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    addView(row(getString(R.string.pin_title), getString(R.string.pin_desc), actionButton(getString(R.string.pin_set)) { showPinDialog() }, pinChip))
                }
                addView(pinSection)
                fallbackNote = caption("").apply { setPadding(0, dp(8), 0, 0) }
                addView(fallbackNote)
            },
        )
        addView(
            card(null) {
                addView(switchRow(getString(R.string.notif_unlock_setting), getString(R.string.notif_unlock_setting_desc), s.notificationUnlock, help = getString(R.string.help_notif_unlock)) { v ->
                    repo.update { it.copy(notificationUnlock = v) }
                })
            },
        )
        addView(
            card(null) {
                addView(row(getString(R.string.rehearse_title), getString(R.string.rehearse_desc), actionButton(getString(R.string.rehearse_go)) { rehearse() }, null, R.drawable.ic_touch))
            },
        )
    }

    /** Allows or disallows one way to unlock. The last one cannot be switched off. */
    private fun setGesture(g: GestureType, on: Boolean) {
        val current = repo.load()
        val set = if (on) current.gestures + g else current.gestures - g
        if (set.isEmpty()) {
            toast(R.string.toast_keep_one_unlock)
            gestureRows[g]?.switchView()?.isChecked = true
            return
        }
        repo.save(current.withGestures(set))
        renderGestureDependents(repo.load())
    }

    private fun renderGestureDependents(s: LockSettings) {
        val g = s.gestures
        sequenceSection.visibility = if (GestureType.VOLUME_SEQUENCE in g) View.VISIBLE else View.GONE
        holdSection.visibility = if (GestureType.CORNER_HOLD in g || GestureType.VOLUME_CHORD in g || GestureType.BADGE_PIN in g) View.VISIBLE else View.GONE
        cornerPairSection.visibility = if (GestureType.CORNER_HOLD in g) View.VISIBLE else View.GONE
        pinSection.visibility = if (GestureType.BADGE_PIN in g) View.VISIBLE else View.GONE
        pinChip.removeAllViews()
        pinChip.addView(
            chip(
                if (s.hasPin) getString(R.string.pin_status_set, s.pinLength) else getString(R.string.pin_status_unset),
                if (s.hasPin) Tone.GOOD else Tone.ATTENTION,
            ),
        )
        fallbackNote.text = GestureText.fallbackHint(this, s)
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
        if (s.gestures == setOf(GestureType.BADGE_PIN) && !s.hasPin) {
            toast(R.string.toast_need_pin)
            return
        }
        if (lockPreflight(s) != null) return
        if (!LockController.requestLock(this, null, 0, rehearsal = true)) toast(R.string.toast_lock_failed)
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

    // ---- Lock automatically ------------------------------------------------

    private fun LinearLayout.autoLockPage(s: LockSettings) {
        addView(
            card(null) {
                addView(row(getString(R.string.section_autolock), getString(R.string.autolock_desc), help = getString(R.string.help_autolock)))
                autoLockList = vertical { setPadding(0, dp(6), 0, 0) }
                addView(autoLockList)
                addView(actionButton(getString(R.string.autolock_add)) {
                    Paywall.require(this@SettingsActivity, FeatureGate.Feature.AUTO_LOCK) {
                        startActivity(Intent(this@SettingsActivity, AppPickerActivity::class.java))
                    }
                }.also { it.tag = "addApp" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
                autoLockNote = callout(getString(R.string.autolock_note), actionButton(getString(R.string.turn_on)) {
                    Disclosures.accessibility(this@SettingsActivity) { startActivity(GuardAccessibilityService.settingsIntent(this@SettingsActivity)) }
                })
                addView(autoLockNote, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            },
        )
        addView(
            card(null) {
                addView(
                    seekRow(
                        getString(R.string.autolock_delay), LockSettings.MIN_AUTO_LOCK_DELAY_SEC, LockSettings.MAX_AUTO_LOCK_DELAY_SEC, s.autoLockDelaySec,
                        format = { "$it s" },
                    ) { sec -> repo.update { it.copy(autoLockDelaySec = sec) } },
                )
                addView(switchRow(getString(R.string.relock_title), getString(R.string.relock_desc), s.relockSameApp, help = getString(R.string.help_relock)) { v ->
                    Paywall.require(this@SettingsActivity, FeatureGate.Feature.RELOCK) { repo.update { it.copy(relockSameApp = v) } }
                })
                if (com.gbhall.childlock.guard.SkipAdFeature.AVAILABLE) {
                    addView(switchRow(getString(R.string.skip_ads_title), getString(R.string.skip_ads_desc), s.skipAds, help = getString(R.string.help_skip_ads)) { v ->
                        Paywall.require(this@SettingsActivity, FeatureGate.Feature.SKIP_ADS) { repo.update { it.copy(skipAds = v) } }
                    })
                }
            },
        )
    }

    private fun renderAutoLock(s: LockSettings) {
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
                                addView(body(getString(R.string.rule_summary, RuleEditor.label(this@SettingsActivity, t)), secondary = true))
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(actionButton(getString(R.string.rule_change)) {
                            RuleEditor.show(this@SettingsActivity, pkg, label) { renderAutoLock(repo.load()) }
                        })
                        setOnClickListener { RuleEditor.show(this@SettingsActivity, pkg, label) { renderAutoLock(repo.load()) } }
                    },
                )
            }
    }

    // ---- Keep them inside --------------------------------------------------

    private fun LinearLayout.insidePage(s: LockSettings) {
        addView(
            card(null) {
                addView(body(getString(R.string.keep_inside_desc), secondary = true).apply { setPadding(0, 0, 0, dp(6)) })
                addView(switchRow(getString(R.string.block_gestures), getString(R.string.block_gestures_desc), s.blockGestures, help = getString(R.string.help_block_gestures)) { v -> repo.update { it.copy(blockGestures = v) } })
                addView(switchRow(getString(R.string.block_keys), getString(R.string.block_keys_desc), s.blockKeys) { v -> repo.update { it.copy(blockKeys = v) } })
                addView(switchRow(getString(R.string.block_shade), getString(R.string.block_shade_desc), s.blockShade) { v -> repo.update { it.copy(blockShade = v) } })
                addView(switchRow(getString(R.string.relaunch_app), getString(R.string.relaunch_app_desc), s.relaunchApp) { v -> repo.update { it.copy(relaunchApp = v) } })
            },
        )
        addView(caption(getString(R.string.safety_note)).apply { setPadding(dp(8), dp(4), dp(8), 0) })
    }

    // ---- Hand back after ---------------------------------------------------

    private fun LinearLayout.timerPage(s: LockSettings) {
        addView(
            card(null) {
                addView(
                    seekRow(
                        getString(R.string.session_title), 0, LockSettings.MAX_SESSION_MINUTES / 5, s.sessionMinutes / 5,
                        format = { if (it == 0) getString(R.string.session_off) else getString(R.string.session_minutes, it * 5) },
                    ) { steps -> repo.update { it.copy(sessionMinutes = steps * 5) } },
                )
                addView(body(getString(R.string.session_desc), secondary = true))
            },
        )
    }

    // ---- More settings -----------------------------------------------------

    private fun LinearLayout.morePage(s: LockSettings) {
        addView(
            card(null) {
                addView(
                    seekRow(
                        getString(R.string.arm_delay), LockSettings.MIN_ARM_DELAY_SEC, LockSettings.MAX_ARM_DELAY_SEC, s.armDelaySec,
                        format = { "$it s" },
                    ) { sec -> repo.update { it.copy(armDelaySec = sec) } },
                )
                addView(switchRow(getString(R.string.keep_screen_on), getString(R.string.keep_screen_on_desc), s.keepScreenOn) { v -> repo.update { it.copy(keepScreenOn = v) } })
                addView(switchRow(getString(R.string.keep_orientation), getString(R.string.keep_orientation_desc), s.keepOrientation) { v -> repo.update { it.copy(keepOrientation = v) } })
            },
        )
        addView(
            card(getString(R.string.badge_corner)) {
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
            },
        )
        addView(
            card(null) {
                addView(row(getString(R.string.setup_assistant), getString(R.string.setup_assistant_desc), actionButton(getString(R.string.open)) {
                    repo.setupDismissed = false
                    startActivity(Intent(this@SettingsActivity, SetupActivity::class.java))
                }, null, R.drawable.ic_tune))
                if (FeatureGate.isDebuggable(this@SettingsActivity)) {
                    addView(switchRow(getString(R.string.pro_preview_free), null, FeatureGate.isPreviewingFree(this@SettingsActivity)) { v ->
                        FeatureGate.setPreviewFree(this@SettingsActivity, v)
                    })
                }
            },
        )
    }

    // ---- About -------------------------------------------------------------

    private fun LinearLayout.aboutPage() {
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { null } ?: "?"
        addView(
            card(null) {
                addView(row(getString(R.string.about_version), version, chip = null, iconRes = R.drawable.ic_lock))
                addView(divider())
                addView(body(getString(R.string.about_privacy_summary), secondary = true).apply { setPadding(0, dp(8), 0, dp(4)) })
            },
        )
        addView(
            card(null) {
                listOf(
                    R.string.about_privacy to URL_PRIVACY,
                    R.string.about_source to URL_SOURCE,
                    R.string.about_licence to URL_LICENCE,
                ).forEachIndexed { i, (res, url) ->
                    if (i > 0) addView(divider())
                    addView(row(getString(res), null, actionButton(getString(R.string.open)) { open(url) }))
                }
            },
        )
        addView(
            card(getString(R.string.about_stuck), R.drawable.ic_warning) {
                addView(body(getString(R.string.about_stuck_body), secondary = true))
            },
        )
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(R.string.toast_no_browser)
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_PAGE = "page"
        const val URL_SOURCE = "https://github.com/gbhall99/Child-lock"
        const val URL_PRIVACY = "https://github.com/gbhall99/Child-lock/blob/main/PRIVACY.md"
        const val URL_LICENCE = "https://github.com/gbhall99/Child-lock/blob/main/LICENSE"

        fun intent(context: Context, page: Page): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page.name)
    }
}

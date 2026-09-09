package com.gbhall.childlock.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureText
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

class MainActivity : Activity() {
    private lateinit var repo: SettingsRepository

    // Status card
    private lateinit var stateChipHolder: LinearLayout
    private lateinit var lockHow: TextView
    private lateinit var unlockHow: TextView
    private lateinit var guardWarning: View
    private lateinit var armButton: Button
    private lateinit var armHint: TextView

    // Permissions card
    private lateinit var overlayChipHolder: LinearLayout
    private lateinit var notificationRow: View
    private lateinit var notificationChipHolder: LinearLayout
    private lateinit var accessibilityChipHolder: LinearLayout
    private lateinit var restrictedHint: View

    // Gesture card
    private lateinit var sequenceSection: View
    private lateinit var holdSection: View
    private lateinit var cornerPairSection: View
    private lateinit var pinSection: View
    private lateinit var pinChipHolder: LinearLayout

    private val stateListener: (LockState) -> Unit = { renderStatus(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        setContentView(buildContent(repo.load()))
        LockController.addListener(stateListener)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        renderStatus(LockController.state)
        renderGestureDependents(repo.load())
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
            setPadding(p, p, p, dp(32))
            addView(statusCard(s))
            addView(permissionsCard())
            addView(gestureCard(s))
            addView(optionsCard(s))
            addView(safetyCard())
        }
        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(if (isNight) 0xFF141519.toInt() else 0xFFF2F4F8.toInt())
            addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun chipHolder() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun setChip(holder: LinearLayout, text: String, tone: Tone, large: Boolean = false) {
        holder.removeAllViews()
        holder.addView(chip(text, tone, large))
    }

    private fun statusCard(s: LockSettings) = card(null) {
        stateChipHolder = chipHolder()
        addView(stateChipHolder)

        addView(body(getString(R.string.how_to_lock), secondary = true, size = 12f).apply {
            setPadding(0, dp(14), 0, 0)
            isAllCaps = true
            letterSpacing = 0.06f
        })
        lockHow = body("")
        addView(lockHow)
        addView(body(getString(R.string.how_to_unlock), secondary = true, size = 12f).apply {
            setPadding(0, dp(8), 0, 0)
            isAllCaps = true
            letterSpacing = 0.06f
        })
        unlockHow = body("")
        addView(unlockHow)

        guardWarning = chip(getString(R.string.guard_warning), Tone.ATTENTION)
        addView(guardWarning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        armButton = primaryButton(getString(R.string.arm_button)) { arm() }
        addView(armButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(16) })
        armHint = body(getString(R.string.arm_hint, s.armDelaySec), secondary = true, size = 13f)
        addView(armHint)
    }

    private fun permissionsCard() = card(getString(R.string.section_permissions)) {
        overlayChipHolder = chipHolder()
        addView(row(getString(R.string.perm_overlay), getString(R.string.perm_overlay_desc), actionButton(getString(R.string.grant)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, overlayChipHolder))
        restrictedHint = row(getString(R.string.restricted_title), getString(R.string.restricted_desc), actionButton(getString(R.string.app_info)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        addView(restrictedHint)
        addView(divider())

        notificationChipHolder = chipHolder()
        notificationRow = row(getString(R.string.perm_notifications), getString(R.string.perm_notifications_desc), actionButton(getString(R.string.allow)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }, notificationChipHolder)
        addView(notificationRow)
        addView(divider())

        accessibilityChipHolder = chipHolder()
        addView(row(getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_desc), actionButton(getString(R.string.open)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, accessibilityChipHolder))
    }

    private fun gestureCard(s: LockSettings) = card(getString(R.string.section_gesture)) {
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
        addView(divider())

        sequenceSection = vertical {
            addView(body(getString(R.string.volume_pattern)).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
            val patterns = VolumePattern.entries
            addView(
                radioGroup(
                    listOf(
                        getString(R.string.pattern_option_up_down) to null,
                        getString(R.string.pattern_option_down_up) to null,
                    ),
                    patterns.indexOf(s.volumePattern),
                ) { index ->
                    repo.update { it.copy(volumePattern = patterns[index]) }
                    renderGestureDependents(repo.load())
                },
            )
            addView(switchRow(getString(R.string.pattern_twice_title), getString(R.string.pattern_twice_desc), s.volumeRepeats >= 2) { v ->
                repo.update { it.copy(volumeRepeats = if (v) 2 else 1) }
                renderGestureDependents(repo.load())
            })
            addView(body(getString(R.string.fallback_note), secondary = true, size = 13f))
        }
        addView(sequenceSection)

        holdSection = seekRow(
            getString(R.string.hold_duration),
            (LockSettings.MIN_HOLD_MS / 100).toInt(), (LockSettings.MAX_HOLD_MS / 100).toInt(), (s.holdMs / 100).toInt(),
            format = { String.format("%.1f s", it / 10f) },
        ) { tenths -> repo.update { it.copy(holdMs = tenths * 100L) } }
        addView(holdSection)

        cornerPairSection = vertical {
            addView(body(getString(R.string.corner_pair)).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
            val pairs = CornerPair.entries
            addView(
                radioGroup(
                    listOf(
                        getString(R.string.pair_tl_br) to null,
                        getString(R.string.pair_tr_bl) to null,
                    ),
                    pairs.indexOf(s.cornerPair),
                ) { index -> repo.update { it.copy(cornerPair = pairs[index]) } },
            )
        }
        addView(cornerPairSection)

        pinSection = vertical {
            pinChipHolder = chipHolder()
            addView(row(getString(R.string.pin_title), null, actionButton(getString(R.string.pin_set)) { showPinDialog() }, pinChipHolder))
        }
        addView(pinSection)

        addView(divider())
        addView(body(getString(R.string.badge_corner)).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
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
    }

    private fun optionsCard(s: LockSettings) = card(getString(R.string.section_options)) {
        addView(
            seekRow(
                getString(R.string.arm_delay), LockSettings.MIN_ARM_DELAY_SEC, LockSettings.MAX_ARM_DELAY_SEC, s.armDelaySec,
                format = { "$it s" },
            ) { sec ->
                repo.update { it.copy(armDelaySec = sec) }
                armHint.text = getString(R.string.arm_hint, sec)
            },
        )
        addView(switchRow(getString(R.string.keep_screen_on), getString(R.string.keep_screen_on_desc), s.keepScreenOn) { v ->
            repo.update { it.copy(keepScreenOn = v) }
        })
        addView(divider())
        addView(body(getString(R.string.hardening_note), secondary = true, size = 13f))
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

    private fun safetyCard() = card(getString(R.string.section_safety)) {
        addView(body(getString(R.string.safety_note), secondary = true, size = 14f))
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
        setChip(overlayChipHolder, getString(if (overlay) R.string.status_granted else R.string.status_needed), if (overlay) Tone.GOOD else Tone.ATTENTION)
        armButton.isEnabled = overlay

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            setChip(notificationChipHolder, getString(if (granted) R.string.status_granted else R.string.status_recommended), if (granted) Tone.GOOD else Tone.NEUTRAL)
        } else {
            notificationRow.visibility = View.GONE
        }

        val a11y = GuardAccessibilityService.isEnabled(this)
        setChip(accessibilityChipHolder, getString(if (a11y) R.string.status_enabled else R.string.status_off), if (a11y) Tone.GOOD else Tone.NEUTRAL)
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!overlay || !a11y)
        restrictedHint.visibility = if (restricted) View.VISIBLE else View.GONE
        renderGestureDependents(repo.load())
    }

    private fun renderStatus(state: LockState) {
        when (state) {
            LockState.Unlocked -> setChip(stateChipHolder, getString(R.string.state_unlocked), Tone.NEUTRAL, large = true)
            is LockState.Arming -> setChip(stateChipHolder, getString(R.string.state_arming), Tone.PENDING, large = true)
            is LockState.Locked -> setChip(stateChipHolder, getString(R.string.state_locked), Tone.ACTIVE, large = true)
        }
    }

    private fun renderGestureDependents(s: LockSettings) {
        val isSequence = s.gesture == GestureType.VOLUME_SEQUENCE
        val isPin = s.gesture == GestureType.BADGE_PIN
        sequenceSection.visibility = if (isSequence) View.VISIBLE else View.GONE
        holdSection.visibility = if (isSequence) View.GONE else View.VISIBLE
        cornerPairSection.visibility = if (isPin) View.GONE else View.VISIBLE
        pinSection.visibility = if (isPin) View.VISIBLE else View.GONE
        setChip(
            pinChipHolder,
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

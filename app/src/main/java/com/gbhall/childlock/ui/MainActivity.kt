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
import android.view.Gravity
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
import com.gbhall.childlock.guard.ForegroundTracker
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

class MainActivity : Activity() {
    private lateinit var repo: SettingsRepository
    private lateinit var statusText: TextView
    private lateinit var armButton: Button
    private lateinit var armHint: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var notificationRow: View
    private lateinit var notificationStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var restrictedHint: View
    private lateinit var pinStatus: TextView
    private lateinit var cornerPairSection: View
    private lateinit var pinSection: View
    private lateinit var unlockHelp: TextView

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
            addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun statusCard(s: LockSettings) = card(getString(R.string.section_status)) {
        statusText = body("")
        addView(statusText)
        armButton = Button(context).apply {
            text = getString(R.string.arm_button)
            textSize = 18f
            setOnClickListener { arm() }
        }
        addView(armButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(8) })
        armHint = body(getString(R.string.arm_hint, s.armDelaySec), secondary = true, size = 13f)
        addView(armHint)
    }

    private fun permissionsCard() = card(getString(R.string.section_permissions)) {
        overlayStatus = body("", secondary = true, size = 13f)
        addView(row(getString(R.string.perm_overlay), getString(R.string.perm_overlay_desc), actionButton(R.string.grant) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }))
        addView(overlayStatus)

        notificationStatus = body("", secondary = true, size = 13f)
        notificationRow = row(getString(R.string.perm_notifications), getString(R.string.perm_notifications_desc), actionButton(R.string.allow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        })
        addView(notificationRow)
        addView(notificationStatus)

        accessibilityStatus = body("", secondary = true, size = 13f)
        addView(row(getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_desc), actionButton(R.string.open) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }))
        addView(accessibilityStatus)
        restrictedHint = row(getString(R.string.restricted_title), getString(R.string.restricted_desc), actionButton(R.string.app_info) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        addView(restrictedHint)
    }

    private fun gestureCard(s: LockSettings) = card(getString(R.string.section_gesture)) {
        val gestures = GestureType.entries
        addView(
            radioGroup(
                listOf(
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
        addView(
            seekRow(
                getString(R.string.hold_duration),
                (LockSettings.MIN_HOLD_MS / 100).toInt(), (LockSettings.MAX_HOLD_MS / 100).toInt(), (s.holdMs / 100).toInt(),
                format = { String.format("%.1f s", it / 10f) },
            ) { tenths -> repo.update { it.copy(holdMs = tenths * 100L) } },
        )

        cornerPairSection = vertical {
            addView(body(getString(R.string.corner_pair), size = 15f))
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
            pinStatus = body("", secondary = true, size = 13f)
            addView(row(getString(R.string.pin_title), null, actionButton(R.string.pin_set) { showPinDialog() }))
            addView(pinStatus)
        }
        addView(pinSection)

        addView(body(getString(R.string.badge_corner), size = 15f))
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
                horizontal = true,
            ) { index -> repo.update { it.copy(badgeCorner = corners[index]) } },
        )
        renderGestureDependents(s)
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

    private fun safetyCard() = card(getString(R.string.section_unlock)) {
        unlockHelp = body("")
        addView(unlockHelp)
        addView(body(getString(R.string.safety_note), secondary = true, size = 13f))
    }

    private fun actionButton(label: Int, onClick: () -> Unit) = Button(this).apply {
        text = getString(label)
        setOnClickListener { onClick() }
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
        overlayStatus.text = getString(if (overlay) R.string.status_granted else R.string.status_required)
        armButton.isEnabled = overlay

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            notificationStatus.text = getString(if (granted) R.string.status_granted else R.string.status_recommended)
        } else {
            notificationRow.visibility = View.GONE
            notificationStatus.visibility = View.GONE
        }

        val a11y = GuardAccessibilityService.isEnabled(this)
        accessibilityStatus.text = getString(if (a11y) R.string.status_enabled else R.string.status_optional)
        restrictedHint.visibility = if (!a11y && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
    }

    private fun renderStatus(state: LockState) {
        statusText.text = when (state) {
            LockState.Unlocked -> getString(R.string.status_unlocked)
            is LockState.Arming -> getString(R.string.status_arming)
            is LockState.Locked -> getString(R.string.status_locked)
        }
    }

    private fun renderGestureDependents(s: LockSettings) {
        cornerPairSection.visibility = if (s.gesture == GestureType.BADGE_PIN) View.GONE else View.VISIBLE
        pinSection.visibility = if (s.gesture == GestureType.BADGE_PIN) View.VISIBLE else View.GONE
        pinStatus.text = if (s.hasPin) getString(R.string.pin_status_set, s.pinLength) else getString(R.string.pin_status_unset)
        unlockHelp.text = getString(
            when (s.gesture) {
                GestureType.CORNER_HOLD -> R.string.hint_corner_hold
                GestureType.BADGE_PIN -> R.string.hint_badge_pin
                GestureType.VOLUME_CHORD -> R.string.hint_volume_chord
            },
        )
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
    }
}

package com.gbhall.childlock.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.guard.ForegroundTracker
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.GestureText
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import com.gbhall.childlock.settings.SettingsRepository

/**
 * Home: the lock state and the Lock button, anything that needs fixing, how
 * to lock and unlock in one glance, then a tile for each group of settings.
 * Everything below the tiles lives in [SettingsActivity].
 */
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

    // Tiles whose one-line state changes
    private lateinit var unlockTile: View
    private lateinit var autoLockTile: View
    private lateinit var insideTile: View
    private lateinit var timerTile: View

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
        val s = repo.load()
        refreshPermissions()
        renderStatus(LockController.state)
        renderSettings(s)
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
            addView(tiles())
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
                getString(R.string.perm_notifications), getString(R.string.status_optional) + ". " + getString(R.string.setup_notifications_desc),
                actionButton(getString(R.string.allow)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                    }
                },
                null, R.drawable.ic_bell, Palette.GREY,
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

    private fun tiles(): View {
        unlockTile = tile(getString(R.string.section_gesture), "", R.drawable.ic_volume) { open(SettingsActivity.Page.UNLOCK) }
        autoLockTile = tile(getString(R.string.section_autolock), "", R.drawable.ic_layers) { open(SettingsActivity.Page.AUTO_LOCK) }
        insideTile = tile(getString(R.string.section_inside), "", R.drawable.ic_shield) { open(SettingsActivity.Page.INSIDE) }
        timerTile = tile(getString(R.string.session_title), "", R.drawable.ic_timer) { open(SettingsActivity.Page.TIMER) }
        val more = tile(getString(R.string.section_advanced), getString(R.string.home_more_sub), R.drawable.ic_tune) { open(SettingsActivity.Page.MORE) }
        val about = tile(getString(R.string.section_about), getString(R.string.home_about_sub), R.drawable.ic_info) { open(SettingsActivity.Page.ABOUT) }
        return tileGrid(listOf(unlockTile, autoLockTile, insideTile, timerTile, more, about))
    }

    private fun open(page: SettingsActivity.Page) = startActivity(SettingsActivity.intent(this, page))

    /** Everything on this screen that mirrors a setting changed on another page. */
    private fun renderSettings(s: LockSettings) {
        armButton.text = getString(R.string.arm_button, s.armDelaySec)
        lockHow.text = GestureText.lockHint(this, s)
        unlockHow.text = GestureText.unlockHint(this, s) + " " + GestureText.fallbackHint(this, s)
        unlockTile.setTileSubtitle(GestureText.gestureName(this, s))
        val apps = s.autoLockRules.size
        autoLockTile.setTileSubtitle(
            when {
                apps == 0 -> getString(R.string.home_off)
                !GuardAccessibilityService.isEnabled(this) -> getString(R.string.home_needs_helper)
                else -> resources.getQuantityString(R.plurals.home_autolock_apps, apps, apps)
            },
        )
        val inside = listOf(s.blockKeys, s.blockShade, s.relaunchApp)
        insideTile.setTileSubtitle(
            when {
                inside.all { it } -> getString(R.string.home_on)
                inside.none { it } -> getString(R.string.home_off)
                else -> getString(R.string.home_partly)
            },
        )
        timerTile.setTileSubtitle(
            if (s.sessionMinutes == 0) getString(R.string.session_off) else getString(R.string.session_minutes, s.sessionMinutes),
        )
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
        if (lockPreflight(s) != null) return
        if (!LockController.requestLock(this, ForegroundTracker.lastApp, s.armDelaySec * 1000L)) {
            toast(R.string.toast_lock_failed)
            return
        }
        moveTaskToBack(true)
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

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
    }
}

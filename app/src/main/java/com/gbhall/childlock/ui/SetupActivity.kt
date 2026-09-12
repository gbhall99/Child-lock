package com.gbhall.childlock.ui

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.guard.GuardAccessibilityService
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.settings.SettingsRepository
import com.gbhall.childlock.tile.LockTileService

/**
 * First-run guide: two required steps, one tap each, completion detected on
 * return; then two optional extras. Done is always Done; the progress line
 * carries the guidance.
 */
class SetupActivity : Activity() {
    private lateinit var repo: SettingsRepository
    private lateinit var progress: TextView
    private lateinit var required: LinearLayout
    private lateinit var optional: LinearLayout
    private lateinit var doneButton: Button
    private lateinit var skipButton: TextView

    private data class Step(
        val title: String,
        val description: String,
        val iconRes: Int,
        val required: Boolean,
        val done: Boolean,
        val actionLabel: String,
        val action: () -> Unit,
        val extra: View? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun buildContent(): View {
        val page = vertical {
            setPadding(dp(16), dp(12), dp(16), dp(24))
            addView(pageTitle(getString(R.string.setup_title)))
            progress = body("", secondary = true).apply { setPadding(dp(6), 0, dp(6), dp(16)) }
            addView(progress)
            required = vertical {}
            addView(required)
            doneButton = primaryButton(getString(R.string.done)) { finishSetup() }
            addView(doneButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(4); bottomMargin = dp(20) })
            addView(heading(getString(R.string.setup_nice)).apply { setPadding(dp(6), 0, dp(6), dp(10)) })
            optional = vertical {}
            addView(optional)
            skipButton = TextView(context).apply {
                text = getString(R.string.setup_skip)
                textSize = Type.BODY
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(textSecondary)
                setPadding(0, dp(14), 0, dp(8))
                setOnClickListener {
                    repo.setupDismissed = true
                    finish()
                }
            }
            addView(skipButton)
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

    // ---- steps ------------------------------------------------------------

    private fun currentSteps(): List<Step> {
        val overlay = Settings.canDrawOverlays(this)
        val helper = GuardAccessibilityService.isEnabled(this)
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && repo.overlayAttempted && (!overlay || !helper)
        val restrictedHint = if (restricted) {
            callout(getString(R.string.restricted_desc), actionButton(getString(R.string.app_info)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        } else {
            null
        }
        val list = ArrayList<Step>()
        list += Step(
            getString(R.string.perm_overlay), getString(R.string.setup_overlay_desc), R.drawable.ic_layers,
            required = true, done = overlay, actionLabel = getString(R.string.setup_open),
            action = {
                repo.overlayAttempted = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            },
            extra = if (!overlay) restrictedHint else null,
        )
        list += Step(
            getString(R.string.perm_accessibility), getString(R.string.setup_guard_desc), R.drawable.ic_shield,
            required = true, done = helper, actionLabel = getString(R.string.setup_open),
            action = {
                Disclosures.accessibility(this) {
                    repo.overlayAttempted = true
                    startActivity(GuardAccessibilityService.settingsIntent(this))
                }
            },
            extra = if (overlay && !helper) restrictedHint else if (helper && GuardAccessibilityService.isShortcutButtonOn(this)) shortcutCallout() else null,
        )
        list += Step(
            getString(R.string.setup_escape_title), escapeText(), R.drawable.ic_lock_open,
            required = true, done = repo.escapeAcknowledged, actionLabel = getString(R.string.setup_escape_ack),
            action = {
                repo.escapeAcknowledged = true
                render()
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            list += Step(
                getString(R.string.perm_notifications), getString(R.string.setup_notifications_desc), R.drawable.ic_bell,
                required = false, done = granted, actionLabel = getString(R.string.allow),
                action = { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS) },
            )
        }
        list += Step(
            getString(R.string.setup_tile), getString(R.string.setup_tile_desc), R.drawable.ic_tune,
            required = false, done = repo.tileAdded, actionLabel = getString(R.string.setup_add),
            action = { requestTile() },
        )
        return list
    }

    /** How to unlock with the current settings, the touch backup if there is one, and the forced restart. */
    private fun escapeText(): String {
        val s = repo.load()
        val unlock = (com.gbhall.childlock.settings.GestureText.unlockHint(this, s) + " " +
            com.gbhall.childlock.settings.GestureText.fallbackHint(this, s)).trim()
        return getString(R.string.setup_escape_unlock, unlock) + "\n\n" +
            getString(R.string.setup_escape_restart, com.gbhall.childlock.settings.GestureText.forceRestart(this))
    }

    private fun shortcutCallout() = callout(getString(R.string.shortcut_button_desc), actionButton(getString(R.string.setup_open)) {
        startActivity(GuardAccessibilityService.settingsIntent(this))
    })

    private fun requestTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sbm = getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(this, LockTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_lock),
                mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) {
                    repo.tileAdded = true
                    render()
                }
            }
        } else {
            Toast.makeText(this, R.string.setup_tile_manual, Toast.LENGTH_LONG).show()
        }
    }

    private fun render() {
        val list = currentSteps()
        val requiredSteps = list.filter { it.required }
        val requiredLeft = requiredSteps.count { !it.done }
        progress.text = if (requiredLeft == 0) getString(R.string.setup_all_done) else getString(R.string.setup_progress, requiredSteps.count { it.done }, requiredSteps.size)
        required.removeAllViews()
        optional.removeAllViews()
        var firstOpen = true
        list.forEachIndexed { index, step ->
            val active = step.required && !step.done && firstOpen
            if (active) firstOpen = false
            (if (step.required) required else optional).addView(stepCard(index + 1, step, active))
        }
        doneButton.isEnabled = requiredLeft == 0
        skipButton.visibility = if (requiredLeft == 0) View.GONE else View.VISIBLE
    }

    private fun stepCard(number: Int, step: Step, active: Boolean): View = card(null) {
        if (active) (background as GradientDrawable).setStroke(dp(2), accent)
        val pending = !step.done && !active
        addView(
            horizontal {
                gravity = Gravity.TOP
                val disc = if (step.done) {
                    iconDisc(R.drawable.ic_check, Tone.GOOD.color(context), 44, 24)
                } else {
                    FrameLayout(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (active) Palette.BLUE else tint(Palette.GREY, 0x33))
                        }
                        addView(TextView(context).apply {
                            text = number.toString()
                            textSize = Type.SECTION
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (active) android.graphics.Color.WHITE else Palette.GREY)
                            gravity = Gravity.CENTER
                        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                }
                addView(disc, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
                addView(
                    vertical {
                        addView(
                            horizontal {
                                addView(label(step.title).apply { if (pending) setTextColor(textSecondary) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                                if (!step.required) addView(chip(getString(R.string.status_optional), Tone.NEUTRAL))
                            },
                        )
                        addView(body(step.description, secondary = true).apply { setPadding(0, dp(4), 0, 0) })
                        if (!step.done) {
                            val button: View = if (active) primaryButton(step.actionLabel) { step.action() } else actionButton(step.actionLabel) { step.action() }
                            addView(button, LinearLayout.LayoutParams(if (active) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT, if (active) dp(46) else ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
                        }
                        step.extra?.let { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }) }
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
        )
    }

    private fun finishSetup() {
        repo.setupDismissed = true
        finish()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1

        /** A required step is still open, so the guide should run. */
        fun isNeeded(activity: Activity): Boolean =
            !Settings.canDrawOverlays(activity) || !GuardAccessibilityService.isEnabled(activity) ||
                !SettingsRepository.get(activity).escapeAcknowledged
    }
}

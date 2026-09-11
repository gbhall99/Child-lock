package com.gbhall.childlock.ui

import android.app.Activity
import android.app.AlertDialog
import com.gbhall.childlock.R
import com.gbhall.childlock.settings.AppCatalog
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.SettingsRepository

/** The one dialog for changing or removing an auto-lock rule. */
object RuleEditor {
    fun label(activity: Activity, t: AutoLockTrigger): String = activity.getString(
        when (t) {
            AutoLockTrigger.OPEN -> R.string.trigger_open
            AutoLockTrigger.CALL -> R.string.trigger_call
            AutoLockTrigger.VIDEO_CALL -> R.string.trigger_video_call
            AutoLockTrigger.VOICE_CALL -> R.string.trigger_voice_call
            AutoLockTrigger.FULLSCREEN_PLAYBACK -> R.string.trigger_fullscreen
            AutoLockTrigger.PLAYBACK -> R.string.trigger_playback
        },
    )

    fun show(activity: Activity, packageName: String, appLabel: String, onDone: () -> Unit = {}) {
        val repo = SettingsRepository.get(activity)
        val current = repo.load().autoLockRules[packageName]
        val profile = AppCatalog.profile(activity, packageName)
        val options = AppCatalog.optionsFor(profile, current)
        val labels = options.map { label(activity, it) }.toTypedArray()
        var chosen = options.indexOf(current ?: profile.default).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.rule_title, appLabel))
            .setSingleChoiceItems(labels, chosen) { _, which -> chosen = which }
            .setPositiveButton(R.string.done) { _, _ ->
                repo.update { s -> s.copy(autoLockRules = s.autoLockRules + (packageName to options[chosen])) }
                onDone()
            }
            .setNeutralButton(R.string.rule_remove) { _, _ ->
                repo.update { s -> s.copy(autoLockRules = s.autoLockRules - packageName) }
                onDone()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDone() }
            .show()
    }
}

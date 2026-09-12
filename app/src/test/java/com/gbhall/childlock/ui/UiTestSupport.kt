package com.gbhall.childlock.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.gbhall.childlock.TestSupport
import org.robolectric.Shadows.shadowOf

/** Tree walks and fake apps for the screen tests. */
internal object UiTestSupport {
    fun <T : View> find(root: View, type: Class<T>, match: (T) -> Boolean): T? {
        if (type.isInstance(root) && match(type.cast(root)!!)) return type.cast(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), type, match)?.let { return it }
        return null
    }

    fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    fun button(activity: Activity, label: String): Button? =
        find(activity.window.decorView, Button::class.java) { it.text == label }

    fun switch(activity: Activity, title: String): Switch? =
        find(activity.window.decorView, Switch::class.java) { it.contentDescription == title }

    fun radio(activity: Activity, labelPrefix: String): RadioButton? =
        find(activity.window.decorView, RadioButton::class.java) { it.text.toString().startsWith(labelPrefix) }

    fun seek(activity: Activity, title: String): SeekBar? =
        find(activity.window.decorView, SeekBar::class.java) { it.contentDescription == title }

    /** Drags a slider the way a finger does, so the row's onChange fires. */
    fun drag(seek: SeekBar, to: Int) {
        seek.progress = to
        shadowOf(seek).onSeekBarChangeListener.onProgressChanged(seek, to, true)
    }

    fun tile(activity: Activity, title: String): View? =
        find(activity.window.decorView, View::class.java) { v ->
            v.tag != TILE_SUBTITLE_TAG && v is ViewGroup && v.isClickable &&
                find(v, TextView::class.java) { it.text == title } != null &&
                find(v, TextView::class.java) { it.tag == TILE_SUBTITLE_TAG } != null
        }

    fun tileSubtitle(tile: View): String = find(tile, TextView::class.java) { it.tag == TILE_SUBTITLE_TAG }!!.text.toString()

    /** A launchable app the picker and the rule list can show, with a label and an Android category. */
    fun installApp(pkg: String, label: String, category: Int = ApplicationInfo.CATEGORY_UNDEFINED) {
        val pm = shadowOf(TestSupport.app.packageManager)
        pm.installPackage(
            PackageInfo().apply {
                packageName = pkg
                applicationInfo = ApplicationInfo().apply {
                    packageName = pkg
                    nonLocalizedLabel = label
                    this.category = category
                }
            },
        )
        val main = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(main)
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }
}

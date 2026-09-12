package com.gbhall.childlock.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.gbhall.childlock.R
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.settings.AppCatalog
import com.gbhall.childlock.settings.SettingsRepository

/**
 * "Add an app" screen: a searchable list, one tap adds the app with the best
 * default moment for it and returns to the main screen, where the rule can be
 * edited or removed. Apps already added are marked and tap through to edit.
 */
class AppPickerActivity : Activity() {
    data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

    private lateinit var repo: SettingsRepository
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        adapter = Adapter(loadApps())

        val toolbar = toolbar(getString(R.string.picker_title)) { finish() }
        val search = EditText(this).apply {
            hint = getString(R.string.picker_search)
            textSize = 16f
            setSingleLine()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(cardBackground)
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) = adapter.filter(s.toString())
                override fun beforeTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
            })
        }
        val list = ListView(this).apply {
            adapter = this@AppPickerActivity.adapter
            divider = null
            setOnItemClickListener { _, _, position, _ -> choose(this@AppPickerActivity.adapter.getItem(position)) }
        }
        val page = vertical {
            setBackgroundColor(pageBackground)
            setPadding(dp(12), dp(8), dp(12), 0)
            addView(toolbar)
            addView(body(getString(R.string.picker_desc), secondary = true, size = 13.5f).apply { setPadding(dp(6), dp(2), dp(6), dp(12)) })
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4); marginEnd = dp(4); bottomMargin = dp(10)
            })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        page.setOnApplyWindowInsetsListener { _, insets ->
            val i = LockOverlayService.systemInsets(insets)
            page.setPadding(dp(12) + i[0], dp(8) + i[1], dp(12) + i[2], i[3])
            insets
        }
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private fun choose(entry: AppEntry) {
        val existing = repo.load().autoLockRules[entry.packageName]
        if (existing == null) {
            val profile = AppCatalog.profile(this, entry.packageName)
            repo.update { s -> s.copy(autoLockRules = s.autoLockRules + (entry.packageName to profile.default)) }
        }
        // Straight into the editor so the parent sees and can change the moment.
        RuleEditor.show(this, entry.packageName, entry.label) { finish() }
    }

    @Suppress("DEPRECATION")
    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != packageName }
            .mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    private inner class Adapter(private val source: List<AppEntry>) : BaseAdapter() {
        private var shown: List<AppEntry> = source

        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) source else source.filter { it.label.lowercase().contains(q) || it.packageName.contains(q) }
            notifyDataSetChanged()
        }

        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = getItem(position)
            val added = repo.load().autoLockRules.containsKey(entry.packageName)
            val row = (convertView as? LinearLayout) ?: horizontal {
                setPadding(dp(8), dp(10), dp(8), dp(10))
                addView(ImageView(context).apply { tag = "icon" }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
                addView(TextView(context).apply {
                    tag = "label"
                    textSize = 16f
                    setTextColor(themeColor(android.R.attr.textColorPrimary))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(LinearLayout(context).apply { tag = "chip"; orientation = LinearLayout.HORIZONTAL })
                gravity = Gravity.CENTER_VERTICAL
            }
            row.findViewWithTag<ImageView>("icon").setImageDrawable(entry.icon)
            row.findViewWithTag<TextView>("label").text = entry.label
            val chipHolder = row.findViewWithTag<LinearLayout>("chip")
            chipHolder.removeAllViews()
            chipHolder.addView(if (added) chip(getString(R.string.picker_added), Tone.GOOD) else actionButton(getString(R.string.picker_add)) { choose(entry) })
            return row
        }
    }
}

package com.gbhall.childlock.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.gbhall.childlock.R
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.settings.SettingsRepository

/** Multi-select list of launchable apps for the auto-lock feature. */
class AppPickerActivity : Activity() {
    data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

    private lateinit var repo: SettingsRepository
    private lateinit var adapter: Adapter
    private var all: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        all = loadApps()
        adapter = Adapter(all)

        val search = EditText(this).apply {
            hint = getString(R.string.picker_search)
            textSize = 16f
            setSingleLine()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
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
            setOnItemClickListener { _, view, position, _ ->
                val entry = this@AppPickerActivity.adapter.getItem(position)
                toggle(entry)
                (view.findViewWithTag<CheckBox>("check"))?.isChecked = entry.packageName in selected()
            }
        }
        val page = vertical {
            setBackgroundColor(pageBackground)
            setPadding(dp(16), dp(12), dp(16), 0)
            addView(
                horizontal {
                    addView(icon(R.drawable.ic_lock, accent, 24), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
                    addView(TextView(context).apply {
                        text = getString(R.string.picker_title)
                        textSize = 22f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(themeColor(android.R.attr.textColorPrimary))
                    })
                }.apply { setPadding(0, dp(8), 0, dp(6)) },
            )
            addView(body(getString(R.string.picker_desc), secondary = true, size = 13.5f).apply { setPadding(0, 0, 0, dp(12)) })
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        page.setOnApplyWindowInsetsListener { _, insets ->
            val i = LockOverlayService.systemInsets(insets)
            page.setPadding(dp(16) + i[0], dp(12) + i[1], dp(16) + i[2], i[3])
            insets
        }
        setContentView(page)
    }

    private fun selected(): Set<String> = repo.load().autoLockApps

    private fun toggle(entry: AppEntry) {
        repo.update { s ->
            val next = s.autoLockApps.toMutableSet()
            if (!next.remove(entry.packageName)) next.add(entry.packageName)
            s.copy(autoLockApps = next)
        }
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
            val row = (convertView as? LinearLayout) ?: horizontal {
                setPadding(dp(8), dp(10), dp(8), dp(10))
                addView(ImageView(context).apply { tag = "icon" }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
                addView(TextView(context).apply {
                    tag = "label"
                    textSize = 16f
                    setTextColor(themeColor(android.R.attr.textColorPrimary))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(CheckBox(context).apply { tag = "check"; isClickable = false; isFocusable = false })
                gravity = Gravity.CENTER_VERTICAL
            }
            row.findViewWithTag<ImageView>("icon").setImageDrawable(entry.icon)
            row.findViewWithTag<TextView>("label").text = entry.label
            row.findViewWithTag<CheckBox>("check").isChecked = entry.packageName in selected()
            return row
        }
    }
}

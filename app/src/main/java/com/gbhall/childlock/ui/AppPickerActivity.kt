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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.gbhall.childlock.R
import com.gbhall.childlock.guard.GuardPolicy
import com.gbhall.childlock.lock.LockOverlayService
import com.gbhall.childlock.settings.AutoLockTrigger
import com.gbhall.childlock.settings.SettingsRepository

/**
 * Multi-select list of launchable apps for auto-lock. A ticked app shows a
 * row of "lock when" choices underneath. Everything saves as you tap.
 */
class AppPickerActivity : Activity() {
    data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

    private lateinit var repo: SettingsRepository
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository.get(this)
        adapter = Adapter(loadApps())

        val toolbar = horizontal {
            setPadding(0, dp(4), 0, dp(4))
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_back)
                    imageTintList = android.content.res.ColorStateList.valueOf(themeColor(android.R.attr.textColorPrimary))
                    contentDescription = getString(R.string.back)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    isClickable = true
                    setOnClickListener { finish() }
                },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(6) },
            )
            addView(
                TextView(context).apply {
                    text = getString(R.string.picker_title)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(themeColor(android.R.attr.textColorPrimary))
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(actionButton(getString(R.string.done)) { finish() })
        }
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
            setOnItemClickListener { _, _, position, _ ->
                toggle(this@AppPickerActivity.adapter.getItem(position))
                this@AppPickerActivity.adapter.notifyDataSetChanged()
            }
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

    private fun rules(): Map<String, AutoLockTrigger> = repo.load().autoLockRules

    private fun toggle(entry: AppEntry) {
        repo.update { s ->
            val next = s.autoLockRules.toMutableMap()
            if (next.remove(entry.packageName) == null) next[entry.packageName] = GuardPolicy.smartTrigger(entry.packageName)
            s.copy(autoLockRules = next)
        }
    }

    private fun setTrigger(entry: AppEntry, trigger: AutoLockTrigger) {
        repo.update { s -> s.copy(autoLockRules = s.autoLockRules + (entry.packageName to trigger)) }
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
            val trigger = rules()[entry.packageName]
            val row = (convertView as? LinearLayout) ?: vertical {
                setPadding(dp(4), dp(6), dp(4), dp(6))
                addView(
                    horizontal {
                        tag = "top"
                        setPadding(dp(4), dp(6), dp(4), dp(6))
                        addView(ImageView(context).apply { tag = "icon" }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
                        addView(TextView(context).apply {
                            tag = "label"
                            textSize = 16f
                            setTextColor(themeColor(android.R.attr.textColorPrimary))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(CheckBox(context).apply { tag = "check"; isClickable = false; isFocusable = false })
                    },
                )
                addView(LinearLayout(context).apply { tag = "triggers"; orientation = LinearLayout.VERTICAL })
            }
            row.findViewWithTag<ImageView>("icon").setImageDrawable(entry.icon)
            row.findViewWithTag<TextView>("label").text = entry.label
            row.findViewWithTag<CheckBox>("check").isChecked = trigger != null
            val holder = row.findViewWithTag<LinearLayout>("triggers")
            holder.removeAllViews()
            if (trigger != null) {
                holder.addView(body(getString(R.string.picker_lock_when), secondary = true, size = 12.5f).apply {
                    setPadding(dp(58), 0, 0, dp(6))
                    isAllCaps = true
                    letterSpacing = 0.06f
                })
                val options = listOf(
                    AutoLockTrigger.OPEN to R.string.trigger_open,
                    AutoLockTrigger.CALL to R.string.trigger_call,
                    AutoLockTrigger.FULLSCREEN_PLAYBACK to R.string.trigger_fullscreen,
                    AutoLockTrigger.PLAYBACK to R.string.trigger_playback,
                )
                val group = android.widget.RadioGroup(this@AppPickerActivity).apply {
                    orientation = android.widget.RadioGroup.VERTICAL
                    setPadding(dp(46), 0, 0, dp(4))
                }
                options.forEach { (t, label) ->
                    group.addView(android.widget.RadioButton(this@AppPickerActivity).apply {
                        id = View.generateViewId()
                        text = getString(label)
                        textSize = 14.5f
                        isChecked = t == trigger
                        setOnClickListener { setTrigger(entry, t) }
                    })
                }
                holder.addView(group)
            }
            return row
        }
    }
}

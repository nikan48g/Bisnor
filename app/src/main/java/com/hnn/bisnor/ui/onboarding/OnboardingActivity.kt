package com.hnn.bisnor.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.hnn.bisnor.BuildConfig
import com.hnn.bisnor.MainActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.util.AppPrefs
import com.hnn.bisnor.util.ThemeHelper

/** First run / after an update: welcome, language, interface style, light or dark. */
class OnboardingActivity : AppCompatActivity() {

    private var step = 0
    private lateinit var content: LinearLayout
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        step = savedInstanceState?.getInt("step") ?: 0
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.background)); isFillViewport = true
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, FrameLayout.LayoutParams(
            minOf((resources.displayMetrics.widthPixels - 48 * dp).toInt(), (560 * dp).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        setContentView(scroll)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putInt("step", step)
    }

    private fun text(res: Int, style: Int, top: Int = 0) = TextView(this).apply {
        setText(res); setTextAppearance(style)
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (top * dp).toInt() }
    }

    private fun option(title: Int, desc: Int?, selected: Boolean, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_onb_option)
            isFocusable = true; isClickable = true; isSelected = selected
            val p = (18 * dp).toInt(); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * dp).toInt() }
            addView(TextView(context).apply { setText(title); setTextAppearance(R.style.TextAppearance_Bisnor_Title) })
            if (desc != null) addView(TextView(context).apply {
                setText(desc); setTextAppearance(R.style.TextAppearance_Bisnor_Caption)
                setTextColor(getColor(R.color.on_surface_variant))
            })
            setOnClickListener { onClick() }
        }

    private fun render() {
        content.removeAllViews()
        val pad = (28 * dp).toInt()
        content.setPadding(0, pad, 0, pad)
        val history = PlaybackHistoryManager(this)
        when (step) {
            0 -> {
                content.addView(text(R.string.app_name, R.style.TextAppearance_Bisnor_Display, 40))
                content.addView(text(R.string.onb_welcome_title, R.style.TextAppearance_Bisnor_Headline, 24))
                content.addView(text(R.string.onb_welcome_body, R.style.TextAppearance_Bisnor_Body, 12))
                content.addView(text(R.string.onb_whats_new, R.style.TextAppearance_Bisnor_Caption, 12))
            }
            1 -> {
                content.addView(text(R.string.onb_lang_title, R.style.TextAppearance_Bisnor_Headline, 16))
                content.addView(text(R.string.onb_lang_sub, R.style.TextAppearance_Bisnor_Body, 8))
                val cur = AppPrefs.lang(this)
                for ((tag, label) in listOf(AppPrefs.LANG_FA to R.string.lang_fa, AppPrefs.LANG_EN to R.string.lang_en))
                    content.addView(option(label, null, cur == tag) {
                        if (cur != tag) { AppPrefs.setLang(this, tag); recreate() }
                    })
            }
            2 -> {
                content.addView(text(R.string.onb_ui_title, R.style.TextAppearance_Bisnor_Headline, 16))
                content.addView(text(R.string.onb_ui_sub, R.style.TextAppearance_Bisnor_Body, 8))
                val glass = AppPrefs.glass(this)
                content.addView(option(R.string.ui_modern, R.string.ui_modern_desc, glass) { AppPrefs.setGlass(this, true); render() })
                content.addView(option(R.string.ui_classic, R.string.ui_classic_desc, !glass) { AppPrefs.setGlass(this, false); render() })
            }
            else -> {
                content.addView(text(R.string.onb_theme_title, R.style.TextAppearance_Bisnor_Headline, 16))
                content.addView(text(R.string.onb_theme_sub, R.style.TextAppearance_Bisnor_Body, 8))
                val cur = history.themeMode
                for ((mode, t, d) in listOf(
                    Triple("system", R.string.theme_system, R.string.theme_system_desc),
                    Triple("light", R.string.theme_light, R.string.theme_light_desc),
                    Triple("dark", R.string.theme_dark, R.string.theme_dark_desc)
                )) content.addView(option(t, d, cur == mode) {
                    if (cur != mode) { history.themeMode = mode; render() }
                })
            }
        }
        val last = step == 3
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (28 * dp).toInt() }
        }
        row.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            setText(R.string.onb_back); visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
            setOnClickListener { step--; render() }
        })
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(MaterialButton(this, null, 0).apply {
            setText(when { step == 0 -> R.string.onb_start; last -> R.string.onb_done; else -> R.string.onb_next })
            setOnClickListener { if (last) finishOnboarding() else { step++; render() } }
        }.also { it.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary)); it.setTextColor(getColor(R.color.on_primary)); it.cornerRadius = (100 * dp).toInt(); it.minWidth = (150 * dp).toInt(); it.minimumHeight = (52 * dp).toInt(); it.setPadding((28 * dp).toInt(), 0, (28 * dp).toInt(), 0); it.insetTop = 0; it.insetBottom = 0; it.gravity = Gravity.CENTER })
        content.addView(row)
        row.getChildAt(2).requestFocus()
    }

    private fun finishOnboarding() {
        AppPrefs.setOnboardedVersion(this, BuildConfig.VERSION_NAME)
        ThemeHelper.initAppTheme(this)
        // A fresh, explicit intent: re-sending the original MAIN/LAUNCHER intent would just
        // bring the (finished) old task to the front and close the app.
        val next = Intent(this, MainActivity::class.java).apply {
            intent.getStringExtra("next_data")?.let { data = android.net.Uri.parse(it); action = Intent.ACTION_VIEW }
        }
        startActivity(next)
        finish()
    }
}

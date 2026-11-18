package com.hnn.bisnor.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import java.util.Locale

object ThemeHelper {

    fun initAppTheme(context: Context) {
        val historyManager = PlaybackHistoryManager(context)
        val targetMode = when (historyManager.themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    fun applyTheme(activity: Activity) {
        val historyManager = PlaybackHistoryManager(activity)
        val targetMode = when (historyManager.themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }

        val lang = AppPrefs.lang(activity)
        activity.window.decorView.layoutDirection =
            if (AppPrefs.isRtl(activity)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        if (AppPrefs.font(activity) == "fancy") {
            activity.theme.applyStyle(com.hnn.bisnor.R.style.ThemeOverlay_Bisnor_FancyFont, true)
        }
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        // CRITICAL FIX: Ensure uiMode night mask matches the selected app theme mode!
        // Prevents system orientation changes (e.g. returning from landscape PlayerActivity)
        // from injecting the device system's light mode into app resources.
        val targetNightMask = when (targetMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            else -> activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or targetNightMask

        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
    }
}

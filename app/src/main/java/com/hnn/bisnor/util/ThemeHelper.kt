package com.hnn.bisnor.util

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import java.util.Locale

object ThemeHelper {

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

        // Force RTL layout direction and Persian locale
        activity.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val locale = Locale("fa")
        Locale.setDefault(locale)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
    }
}

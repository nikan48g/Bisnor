package com.hnn.bisnor.util

import android.content.Context
import android.content.SharedPreferences


/** Look-and-feel choices made in onboarding / settings. */
object AppPrefs {
    const val LANG_FA = "fa"
    const val LANG_EN = "en"

    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("bisnor_ui", Context.MODE_PRIVATE)

    fun lang(c: Context): String = p(c).getString("lang", null)
        ?: LANG_FA
    fun setLang(c: Context, v: String) = p(c).edit().putString("lang", v).apply()
    fun isRtl(c: Context) = lang(c) == LANG_FA

    /** true = liquid-glass UI, false = classic flat UI for weaker phones. */
    fun glass(c: Context): Boolean = p(c).getBoolean("glass", true)
    fun setGlass(c: Context, v: Boolean) = p(c).edit().putBoolean("glass", v).apply()

    /** "ajor" or "fancy". */
    fun font(c: Context): String = p(c).getString("font", "ajor") ?: "ajor"
    fun setFont(c: Context, v: String) = p(c).edit().putString("font", v).apply()

}

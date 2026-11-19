package com.hnn.bisnor.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import com.hnn.bisnor.R
import java.util.Locale

/** Strings and fonts for code that has no themed Activity at hand. */
object Tr {
    fun localized(c: Context): Context {
        val cfg = Configuration(c.resources.configuration)
        val l = Locale(AppPrefs.lang(c))
        cfg.setLocale(l)
        cfg.setLayoutDirection(l)
        return c.applicationContext.createConfigurationContext(cfg)
    }

    fun s(c: Context, @StringRes id: Int, vararg args: Any?): String = localized(c).getString(id, *args)

    private var cachedLang = ""
    private var cachedCtx: Context? = null

    /** String in the app language, from anywhere (no Context needed). */
    fun s(@StringRes id: Int, vararg args: Any?): String {
        val app = com.hnn.bisnor.BisnorApp.instance
        val lang = AppPrefs.lang(app)
        val ctx = cachedCtx?.takeIf { cachedLang == lang } ?: localized(app).also { cachedCtx = it; cachedLang = lang }
        return ctx.getString(id, *args)
    }

    /** weight: 0 regular, 1 medium, 2 bold */
    fun font(c: Context, weight: Int) = ResourcesCompat.getFont(
        c, if (AppPrefs.lang(c) == AppPrefs.LANG_EN) {
            intArrayOf(R.font.barlow_regular, R.font.barlow_medium, R.font.bebasneue_regular)[weight]
        } else if (AppPrefs.font(c) == "fancy") {
            intArrayOf(R.font.ajorfancy_regular, R.font.ajorfancy_medium, R.font.ajorfancy_bold)[weight]
        } else intArrayOf(R.font.ajor_regular, R.font.ajor_medium, R.font.ajor_bold)[weight]
    )
}

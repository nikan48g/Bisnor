package com.hnn.bisnor.data.repository

import com.hnn.bisnor.R
import com.hnn.bisnor.util.Tr

import android.content.Context
import android.content.SharedPreferences

/**
 * Which catalogue(s) the app should use. "both" is the default: one entry per title
 * that combines the links/seasons of both providers.
 */
object SourcePreferences {

    const val NEXT = "next"
    const val IRANFLIX = "iranflix"
    const val BOTH = "both"

    private const val PREFS = "bisnor_prefs"
    private const val KEY = "bisnor_source"
    private const val KEY_AUTO = "bisnor_source_auto"
    private var prefs: SharedPreferences? = null

    /** The user's manual pick; only used when [auto] is off. */
    @Volatile
    var manualMode: String = BOTH
        private set

    @Volatile
    var auto: Boolean = true
        private set

    /** Which servers the last health check found reachable (both until proven otherwise). */
    @Volatile
    var up: Set<String> = setOf(NEXT, IRANFLIX)

    /** What the app actually uses right now. */
    val mode: String
        get() = if (!auto) manualMode else when {
            NEXT in up && IRANFLIX in up -> BOTH
            IRANFLIX in up -> IRANFLIX
            NEXT in up -> NEXT
            else -> BOTH
        }

    fun setAuto(value: Boolean) {
        auto = value
        prefs?.edit()?.putBoolean(KEY_AUTO, value)?.apply()
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        manualMode = normalize(prefs?.getString(KEY, BOTH))
        auto = prefs?.getBoolean(KEY_AUTO, true) ?: true
    }

    fun setMode(value: String) {
        val normalized = normalize(value)
        if (normalized == manualMode) return
        manualMode = normalized
        prefs?.edit()?.putString(KEY, normalized)?.apply()
    }

    fun usesNext(): Boolean = mode == NEXT || mode == BOTH
    fun usesIranflix(): Boolean = mode == IRANFLIX || mode == BOTH

    fun label(value: String = mode): String = when (value) {
        IRANFLIX -> Tr.s(R.string.s_9a8fa14)
        BOTH -> Tr.s(R.string.s_c77a9c6)
        else -> Tr.s(R.string.s_1452e24)
    }

    private fun normalize(value: String?): String = when (value) {
        IRANFLIX -> IRANFLIX
        NEXT -> NEXT
        else -> BOTH
    }
}

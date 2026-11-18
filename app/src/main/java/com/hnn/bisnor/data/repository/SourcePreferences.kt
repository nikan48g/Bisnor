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
    private var prefs: SharedPreferences? = null

    @Volatile
    var mode: String = BOTH
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = normalize(prefs?.getString(KEY, BOTH))
    }

    fun setMode(value: String) {
        val normalized = normalize(value)
        if (normalized == mode) return
        mode = normalized
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

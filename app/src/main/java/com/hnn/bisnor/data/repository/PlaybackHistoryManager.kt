package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.data.model.RealMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackProgress(
    val url: String,
    val mediaId: Int,
    val mediaTitle: String = "",
    val mediaCover: String = "",
    val episodeTitle: String = "",
    val episodeIndex: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0

    val isFinished: Boolean
        get() = progressPercent >= 90
}

class PlaybackHistoryManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bisnor_playback_history_v6", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _historyFlow = MutableStateFlow<Map<String, PlaybackProgress>>(loadAll())
    val historyFlow: StateFlow<Map<String, PlaybackProgress>> = _historyFlow

    companion object {
        const val PREF_CONTENT_WARNING = "pref_content_warning"
        const val PREF_AUTO_NEXT_MINUTES = "pref_auto_next_minutes"
        const val PREF_AUTO_NEXT_ENABLED = "pref_auto_next_enabled"
        const val PREF_RECENT_SEARCHES = "pref_recent_searches"
        const val PREF_THEME_MODE = "pref_theme_mode" // system, light, dark
        const val PREF_EXTERNAL_PLAYER = "pref_external_player" // internal, vlc, mx, default
    }

    var isContentWarningEnabled: Boolean
        get() = prefs.getBoolean(PREF_CONTENT_WARNING, true)
        set(value) = prefs.edit().putBoolean(PREF_CONTENT_WARNING, value).apply()

    var isAutoNextEnabled: Boolean
        get() = prefs.getBoolean(PREF_AUTO_NEXT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PREF_AUTO_NEXT_ENABLED, value).apply()

    var autoNextMinutes: Int
        get() = prefs.getInt(PREF_AUTO_NEXT_MINUTES, 2)
        set(value) = prefs.edit().putInt(PREF_AUTO_NEXT_MINUTES, value).apply()

    var themeMode: String
        get() = prefs.getString(PREF_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(PREF_THEME_MODE, value).apply()

    var preferredPlayer: String
        get() = prefs.getString(PREF_EXTERNAL_PLAYER, "internal") ?: "internal"
        set(value) = prefs.edit().putString(PREF_EXTERNAL_PLAYER, value).apply()

    fun getRecentSearches(): List<String> {
        val json = prefs.getString(PREF_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val list = getRecentSearches().toMutableList()
        list.remove(clean)
        list.add(0, clean)
        val capped = list.take(10)
        prefs.edit().putString(PREF_RECENT_SEARCHES, gson.toJson(capped)).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(PREF_RECENT_SEARCHES).apply()
    }

    private fun loadAll(): Map<String, PlaybackProgress> {
        val json = prefs.getString("history_map", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, PlaybackProgress>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveProgress(
        url: String,
        mediaId: Int,
        mediaTitle: String,
        mediaCover: String,
        episodeTitle: String,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0 || positionMs <= 1000 || url.isEmpty()) return
        val current = _historyFlow.value.toMutableMap()
        val item = PlaybackProgress(
            url = url,
            mediaId = mediaId,
            mediaTitle = mediaTitle,
            mediaCover = mediaCover,
            episodeTitle = episodeTitle,
            episodeIndex = episodeIndex,
            positionMs = positionMs,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis()
        )

        current[url] = item
        current["last_media_$mediaId"] = item

        val json = gson.toJson(current)
        prefs.edit().putString("history_map", json).apply()
        _historyFlow.value = current
    }

    fun getProgressByUrl(url: String): PlaybackProgress? {
        if (url.isEmpty()) return null
        return _historyFlow.value[url]
    }

    fun isMediaWatched(mediaId: Int): Boolean {
        return _historyFlow.value.values.any { it.mediaId == mediaId && it.isFinished }
    }

    fun getLastWatchedEpisode(mediaId: Int): PlaybackProgress? {
        val direct = _historyFlow.value["last_media_$mediaId"]
        if (direct != null && !direct.isFinished) return direct

        return _historyFlow.value.values
            .filter { it.mediaId == mediaId && !it.isFinished && it.url.isNotEmpty() }
            .maxByOrNull { it.timestamp }
    }

    fun getAllContinueWatching(): List<PlaybackProgress> {
        return _historyFlow.value.keys
            .filter { it.startsWith("last_media_") }
            .mapNotNull { _historyFlow.value[it] }
            .filter { !it.isFinished && it.positionMs > 1000 }
            .sortedByDescending { it.timestamp }
    }
}

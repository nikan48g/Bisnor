package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.data.model.RealMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MediaReview(
    val mediaId: Int,
    val userRating: Float = 0f, // 1.0 to 10.0
    val reviewText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomPlaylist(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val items: MutableList<RealMedia> = mutableListOf(),
    val isPublic: Boolean = false,
    val creatorUsername: String = "",
    val reviews: MutableMap<String, MediaReview> = mutableMapOf() // key: mediaId.toString()
)

class PlaylistsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bisnor_custom_playlists_v2", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _playlistsFlow = MutableStateFlow<List<CustomPlaylist>>(loadPlaylists())
    val playlistsFlow: StateFlow<List<CustomPlaylist>> = _playlistsFlow

    private fun loadPlaylists(): List<CustomPlaylist> {
        val json = prefs.getString("playlists_json", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CustomPlaylist>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<CustomPlaylist>) {
        val json = gson.toJson(list)
        prefs.edit().putString("playlists_json", json).apply()
        _playlistsFlow.value = list
    }

    fun getPlaylistsRawJson(): String {
        return prefs.getString("playlists_json", "[]") ?: "[]"
    }

    fun setPlaylistsFromRawJson(rawJson: String) {
        try {
            val type = object : TypeToken<List<CustomPlaylist>>() {}.type
            val list: List<CustomPlaylist> = gson.fromJson(rawJson, type) ?: emptyList()
            save(list)
        } catch (e: Exception) {
            // Ignore parse error
        }
    }

    fun createPlaylist(name: String, isPublic: Boolean = false, creator: String = ""): String {
        val current = _playlistsFlow.value.toMutableList()
        val uniqueId = "pl_${System.currentTimeMillis()}"
        current.add(CustomPlaylist(id = uniqueId, name = name, isPublic = isPublic, creatorUsername = creator))
        save(current)
        return uniqueId
    }

    fun setPlaylistPublic(playlistId: String, isPublic: Boolean) {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(isPublic = isPublic)
            save(current)
        }
    }

    fun addReview(playlistId: String, mediaId: Int, rating: Float, review: String) {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = current[index]
            val reviewObj = MediaReview(mediaId, rating, review)
            pl.reviews[mediaId.toString()] = reviewObj
            save(current)
        }
    }

    fun getReview(playlistId: String, mediaId: Int): MediaReview? {
        val pl = _playlistsFlow.value.find { it.id == playlistId }
        return pl?.reviews?.get(mediaId.toString())
    }

    fun addToPlaylist(playlistId: String, media: RealMedia): Boolean {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val list = current[index].items
            if (list.none { it.id == media.id }) {
                list.add(0, media)
                save(current)
                return true
            }
        }
        return false
    }

    fun removeFromPlaylist(playlistId: String, mediaId: Int): Boolean {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val list = current[index].items
            val removed = list.removeAll { it.id == mediaId }
            if (removed) {
                save(current)
                return true
            }
        }
        return false
    }

    fun deletePlaylist(playlistId: String) {
        val current = _playlistsFlow.value.toMutableList()
        current.removeAll { it.id == playlistId }
        save(current)
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(name = newName)
            save(current)
        }
    }
}

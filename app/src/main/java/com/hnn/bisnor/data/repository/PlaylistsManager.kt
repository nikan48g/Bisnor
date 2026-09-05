package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.data.model.RealMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CustomPlaylist(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val items: MutableList<RealMedia> = mutableListOf()
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

    fun createPlaylist(name: String) {
        val current = _playlistsFlow.value.toMutableList()
        val uniqueId = "pl_${System.currentTimeMillis()}"
        current.add(CustomPlaylist(id = uniqueId, name = name))
        save(current)
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

    fun removeFromPlaylist(playlistId: String, mediaId: Int) {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index].items.removeAll { it.id == mediaId }
            save(current)
        }
    }

    fun deletePlaylist(playlistId: String) {
        val current = _playlistsFlow.value.toMutableList()
        current.removeAll { it.id == playlistId }
        save(current)
    }
}

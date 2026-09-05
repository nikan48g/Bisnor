package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.data.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bisnor_favorites", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _favoritesFlow = MutableStateFlow<List<MediaItem>>(loadFavorites())
    val favoritesFlow: StateFlow<List<MediaItem>> = _favoritesFlow

    private fun loadFavorites(): List<MediaItem> {
        val json = prefs.getString("favorites_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MediaItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<MediaItem>) {
        val json = gson.toJson(list)
        prefs.edit().putString("favorites_list", json).apply()
        _favoritesFlow.value = list
    }

    fun isFavorite(mediaId: String): Boolean {
        return _favoritesFlow.value.any { it.id == mediaId }
    }

    fun toggleFavorite(item: MediaItem): Boolean {
        val current = _favoritesFlow.value.toMutableList()
        val exists = current.any { it.id == item.id }
        if (exists) {
            current.removeAll { it.id == item.id }
            saveFavorites(current)
            return false
        } else {
            current.add(0, item)
            saveFavorites(current)
            return true
        }
    }
}

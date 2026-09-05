package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.data.model.RealMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bisnor_real_favorites", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _favoritesFlow = MutableStateFlow<List<RealMedia>>(loadFavorites())
    val favoritesFlow: StateFlow<List<RealMedia>> = _favoritesFlow

    private fun loadFavorites(): List<RealMedia> {
        val json = prefs.getString("favs", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RealMedia>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<RealMedia>) {
        val json = gson.toJson(list)
        prefs.edit().putString("favs", json).apply()
        _favoritesFlow.value = list
    }

    fun isFavorite(id: Int): Boolean = _favoritesFlow.value.any { it.id == id }

    fun toggleFavorite(item: RealMedia): Boolean {
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

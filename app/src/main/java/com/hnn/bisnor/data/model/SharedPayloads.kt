package com.hnn.bisnor.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

data class SharedPlaylistPayload(
    val name: String,
    val items: List<RealMedia>
) : Serializable {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): SharedPlaylistPayload? {
            return try {
                Gson().fromJson(json, SharedPlaylistPayload::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

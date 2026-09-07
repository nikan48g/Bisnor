package com.hnn.bisnor.data.model

import com.google.gson.Gson
import java.io.Serializable

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val sender: String,
    val receiver: String,
    val messageText: String = "",
    val sharedMediaJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {

    val sharedMedia: RealMedia?
        get() {
            if (sharedMediaJson.isNullOrEmpty()) return null
            return try {
                Gson().fromJson(sharedMediaJson, RealMedia::class.java)
            } catch (e: Exception) {
                null
            }
        }
}

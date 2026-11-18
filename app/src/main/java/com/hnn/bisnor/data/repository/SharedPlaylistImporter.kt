package com.hnn.bisnor.data.repository

import android.content.Context
import com.hnn.bisnor.data.model.SharedPlaylistPayload
import com.hnn.bisnor.data.remote.SupabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Shared watchlists travel as short links (`https://…/s/Ab3x9KqZ`, stored by the worker)
 * or, as a fallback and for older links, the full payload (`/import?data=…`,
 * `bisnor://app/playlist?data=…` or the bare code).
 */
object SharedPlaylistImporter {

    const val WORKER = "https://bisnor-pwa.drive-storage-hnn.workers.dev"

    private val DATA_PARAM = Regex("[?&]data=([^&\\s]+)")
    private val SHORT_LINK = Regex("/s/([A-Za-z0-9]{8})\\b")
    private val BARE_SHORT_CODE = Regex("^[A-Za-z0-9]{8}$")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Offline parse of a full-payload link or code. Short links need [resolve]. */
    fun parse(text: String?): SharedPlaylistPayload? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        val code = DATA_PARAM.find(value)?.groupValues?.get(1)
            ?: value.takeUnless { it.startsWith("http", ignoreCase = true) }
            ?: return null
        val decoded = runCatching { URLDecoder.decode(code, "UTF-8") }.getOrDefault(code)
        val json = SupabaseManager.decompressString(decoded)
        if (json.isBlank()) return null
        val payload = SharedPlaylistPayload.fromJson(json) ?: return null
        return payload.takeIf { it.items.isNotEmpty() }
    }

    /** Accepts short links / short codes (fetched from the worker) and every older format. */
    suspend fun resolve(text: String?): SharedPlaylistPayload? = withContext(Dispatchers.IO) {
        val value = text?.trim().orEmpty()
        val shortCode = SHORT_LINK.find(value)?.groupValues?.get(1)
            ?: value.takeIf { BARE_SHORT_CODE.matches(it) }
        if (shortCode == null) return@withContext parse(value)
        val data = runCatching {
            client.newCall(Request.Builder().url("$WORKER/api/share/$shortCode").build()).execute().use { res ->
                if (!res.isSuccessful) null else JSONObject(res.body?.string().orEmpty()).optString("data")
            }
        }.getOrNull()
        if (data.isNullOrBlank()) null else parse(data)
    }

    /**
     * Link to share for [payload]: a short `/s/<code>` link when the worker can store it,
     * otherwise the self-contained `/import?data=` link (works offline / before deploy).
     */
    suspend fun shareLink(payload: SharedPlaylistPayload): String = withContext(Dispatchers.IO) {
        val compressed = SupabaseManager.compressString(payload.toJson())
        val short = runCatching {
            val body = JSONObject().put("data", compressed).toString()
                .toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url("$WORKER/api/share").post(body).build()).execute().use { res ->
                if (!res.isSuccessful) null else JSONObject(res.body?.string().orEmpty()).optString("url")
            }
        }.getOrNull()
        short?.takeIf { it.startsWith("https://") }
            ?: "$WORKER/import?data=${URLEncoder.encode(compressed, "UTF-8")}"
    }

    /**
     * Stores the payload in [playlistId], or in a new playlist when it is null.
     * Returns how many entries were added (duplicates already in the list are skipped).
     */
    fun save(context: Context, payload: SharedPlaylistPayload, playlistId: String? = null): Int {
        val manager = PlaylistsManager(context)
        val target = playlistId ?: manager.createPlaylist("${payload.name} (اشتراکی)")
        var saved = 0
        for (item in payload.items) {
            if (manager.addToPlaylist(target, item)) saved++
        }
        return saved
    }
}

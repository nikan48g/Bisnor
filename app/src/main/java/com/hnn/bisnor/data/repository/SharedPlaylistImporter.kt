package com.hnn.bisnor.data.repository

import android.content.Context
import com.hnn.bisnor.data.model.SharedPlaylistPayload
import com.hnn.bisnor.data.remote.SupabaseManager
import java.net.URLDecoder

/**
 * Reads a shared watchlist link (`https://…/import?data=…` or `bisnor://app/playlist?data=…`)
 * or the bare code, so the same payload can be used by deep links and by the
 * "import watchlist" dialog in settings.
 */
object SharedPlaylistImporter {

    private val DATA_PARAM = Regex("[?&]data=([^&\\s]+)")

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

    /** Creates a new playlist from the payload and returns how many entries were stored. */
    fun save(context: Context, payload: SharedPlaylistPayload): Int {
        val manager = PlaylistsManager(context)
        val playlistId = manager.createPlaylist("${payload.name} (اشتراکی)")
        var saved = 0
        for (item in payload.items) {
            if (manager.addToPlaylist(playlistId, item)) saved++
        }
        return saved
    }
}

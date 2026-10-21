package com.hnn.bisnor.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves a real portrait for a person through Wikidata's P18 claim and Wikimedia
 * Commons. Only public, verifiable data is used; results (including "not found")
 * are cached so repeated detail screens do not re-query.
 */
object PersonPhotoResolver {

    private const val PREFS = "bisnor_person_photos_v1"
    private const val NONE = "none"

    private val memory = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    suspend fun resolve(name: String): String = withContext(Dispatchers.IO) {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return@withContext ""

        memory[key]?.let { return@withContext it }
        prefs?.getString(key, null)?.let { cached ->
            val value = if (cached == NONE) "" else cached
            memory[key] = value
            return@withContext value
        }

        val photo = runCatching { lookup(name) }.getOrDefault("")
        memory[key] = photo
        prefs?.edit()?.putString(key, photo.ifEmpty { NONE })?.apply()
        photo
    }

    private fun lookup(name: String): String {
        val entityId = searchEntity(name, "fa") ?: searchEntity(name, "en") ?: return ""
        val entity = getJson(
            "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$entityId&props=claims&format=json"
        ) ?: return ""
        val fileName = entity.optJSONObject("entities")
            ?.optJSONObject(entityId)
            ?.optJSONObject("claims")
            ?.optJSONArray("P18")
            ?.optJSONObject(0)
            ?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optString("value")
            ?.takeIf { it.isNotBlank() }
            ?: return ""

        val encoded = java.net.URLEncoder.encode(fileName, "UTF-8")
        val commons = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&titles=File:$encoded" +
                "&prop=imageinfo&iiprop=url&iiurlwidth=480&format=json"
        ) ?: return ""
        val pages = commons.optJSONObject("query")?.optJSONObject("pages") ?: return ""
        val iterator = pages.keys()
        while (iterator.hasNext()) {
            val page = pages.optJSONObject(iterator.next()) ?: continue
            val url = page.optJSONArray("imageinfo")
                ?.optJSONObject(0)
                ?.optString("thumburl")
                ?.takeIf { it.startsWith("https://") }
            if (!url.isNullOrBlank()) return url
        }
        return ""
    }

    private fun searchEntity(name: String, language: String): String? {
        val query = java.net.URLEncoder.encode(name, "UTF-8")
        val json = getJson(
            "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$query" +
                "&language=$language&format=json"
        ) ?: return null
        return json.optJSONArray("search")
            ?.optJSONObject(0)
            ?.optString("id")
            ?.takeIf { it.isNotBlank() }
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Bisnor/1.0 (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }
}

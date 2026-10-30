package com.hnn.bisnor.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SocialLink(val platform: String, val label: String, val url: String)

/**
 * Official links are maintained in social.md inside the web assets; the worker
 * parses it once and every client reads the same list.
 */
object SocialLinksRepository {

    private const val ENDPOINT = "https://bisnor-pwa.drive-storage-hnn.workers.dev/api/social"
    private const val CACHE_MS = 30L * 60 * 1000

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var cached: List<SocialLink>? = null
    @Volatile private var cachedAt: Long = 0L

    suspend fun fetch(): List<SocialLink> = withContext(Dispatchers.IO) {
        val fresh = cached
        if (fresh != null && System.currentTimeMillis() - cachedAt < CACHE_MS) return@withContext fresh
        val links = try {
            val request = Request.Builder().url(ENDPOINT).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fresh ?: emptyList()
                } else {
                    val body = response.body?.string().orEmpty()
                    val array = JSONObject(body).optJSONArray("links")
                    val parsed = ArrayList<SocialLink>()
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val url = item.optString("url")
                            if (url.isBlank()) continue
                            parsed.add(
                                SocialLink(
                                    platform = item.optString("platform", "website"),
                                    label = item.optString("label", url),
                                    url = url
                                )
                            )
                        }
                    }
                    parsed
                }
            }
        } catch (_: Exception) {
            fresh ?: emptyList()
        }
        cached = links
        cachedAt = System.currentTimeMillis()
        links
    }
}

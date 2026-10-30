package com.hnn.bisnor.data.repository

import com.hnn.bisnor.data.model.RealEpisode
import com.hnn.bisnor.data.model.RealGenre
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealSeason
import com.hnn.bisnor.data.model.RealSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Next Movie catalogue client (second provider).
 *
 * Every call is authenticated with a short-lived guest token; a 401 triggers one
 * silent re-auth. Nothing is fabricated: an unreachable provider yields empty lists.
 */
object NextMovieRepository {

    private const val BASE = "https://w.mihan-cdn.com/api"
    private const val TOKEN_TTL_MS = 25L * 60 * 1000
    private const val MEMORY_TTL_MS = 5L * 60 * 1000

    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var token: String = ""
    @Volatile
    private var tokenAt: Long = 0L
    private val tokenMutex = Mutex()

    private data class MemoryEntry(val value: Any, val at: Long)
    private val memory = java.util.concurrent.ConcurrentHashMap<String, MemoryEntry>()

    // ---------------------------------------------------------------- transport

    private suspend fun guestToken(force: Boolean = false): String = tokenMutex.withLock {
        val current = token
        if (!force && current.isNotEmpty() && System.currentTimeMillis() - tokenAt < TOKEN_TTL_MS) return current
        val fresh = withContext(Dispatchers.IO) {
            // The upstream token endpoint answers 500 intermittently; a single
            // failure must not blank the whole second-source catalogue.
            repeat(3) { attempt ->
                if (attempt > 0) Thread.sleep(600L * attempt)
                try {
                    val request = Request.Builder()
                        .url("$BASE/auth/guest-token")
                        .post("{}".toRequestBody(JSON))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Platform", "android/1.0")
                        .build()
                    val result = client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@use if (response.code in 400..499 && response.code != 429) null else ""
                        }
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) "" else JSONObject(body).optJSONObject("data")?.optString("token").orEmpty()
                    }
                    if (result == null) return@withContext ""
                    if (result.isNotEmpty()) return@withContext result
                } catch (_: Exception) {
                }
            }
            ""
        }
        token = fresh
        tokenAt = if (fresh.isNotEmpty()) System.currentTimeMillis() else 0L
        fresh
    }

    private data class RawResponse(val json: JSONObject?, val unauthorized: Boolean)

    private suspend fun request(path: String, body: JSONObject? = null, retried: Boolean = false): JSONObject? {
        val auth = guestToken()
        if (auth.isEmpty()) return null
        val result = withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url("$BASE$path")
                    .addHeader("Authorization", "Bearer $auth")
                    .addHeader("Accept", "application/json")
                    .addHeader("Platform", "android/1.0")
                if (body != null) {
                    builder.post(body.toString().toRequestBody(JSON))
                        .addHeader("Content-Type", "application/json")
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (response.code == 401) return@use RawResponse(null, true)
                    if (!response.isSuccessful) return@use RawResponse(null, false)
                    val text = response.body?.string().orEmpty()
                    RawResponse(if (text.isBlank()) null else JSONObject(text), false)
                }
            } catch (_: Exception) {
                RawResponse(null, false)
            }
        }
        if (result.unauthorized && !retried) {
            guestToken(force = true)
            return request(path, body, retried = true)
        }
        val raw = result.json ?: return null
        return raw.optJSONObject("data") ?: raw
    }

    // ---------------------------------------------------------------- endpoints

    /** Slides payload of the Next Movie home feed, memoised briefly. */
    suspend fun home(): JSONObject {
        val now = System.currentTimeMillis()
        memory["home"]?.let { entry ->
            if (now - entry.at <= MEMORY_TTL_MS) return entry.value as JSONObject
        }
        val data = request("/home?page=1") ?: return JSONObject()
        val slides = data.optJSONObject("slides") ?: JSONObject()
        memory["home"] = MemoryEntry(slides, now)
        return slides
    }

    suspend fun details(id: Int): JSONObject? = cachedObject("details:$id") { request("/movie/$id/details") }

    suspend fun links(id: Int): JSONObject? = cachedObject("links:$id") { request("/movie/$id/links") }

    /** Id of the first home-slide genre whose title contains one of [words]. */
    suspend fun genreId(vararg words: String): Int {
        val genres = home().optJSONArray("genres") ?: return 0
        for (i in 0 until genres.length()) {
            val obj = genres.optJSONObject(i) ?: continue
            val title = obj.optString("title", "")
            if (words.any { title.contains(it) }) return obj.optInt("id", 0)
        }
        return 0
    }

    private suspend fun cachedObject(key: String, loader: suspend () -> JSONObject?): JSONObject? {
        val now = System.currentTimeMillis()
        memory[key]?.let { entry ->
            if (now - entry.at <= MEMORY_TTL_MS) return entry.value as JSONObject
        }
        val data = loader() ?: return null
        memory[key] = MemoryEntry(data, now)
        return data
    }

    suspend fun search(title: String): List<JSONObject> {
        val query = title.trim()
        if (query.isEmpty()) return emptyList()
        val data = request("/v3/search?page=1", JSONObject().put("title", query)) ?: return emptyList()
        return data.optJSONArray("items").toObjectList()
    }

    suspend fun browse(
        type: String? = null,
        genreIds: List<Int> = emptyList(),
        countryIds: List<Int> = emptyList(),
        sortKey: String = "created_at",
        page: Int = 1
    ): List<JSONObject> {
        val body = JSONObject().put("sort_key", sortKey).put("sort_order", "desc")
        if (!type.isNullOrBlank()) body.put("type", type)
        if (genreIds.isNotEmpty()) body.put("genre_ids", JSONArray(genreIds))
        if (countryIds.isNotEmpty()) body.put("country_ids", JSONArray(countryIds))
        val data = request("/v3/search?page=$page", body) ?: return emptyList()
        return data.optJSONArray("items").toObjectList()
    }

    // ---------------------------------------------------------------- mapping

    private val JUNK_TITLE = listOf(
        "نسخه جدید", "نسخه وب", "ارسال برنامه", "پشتیبانی برنامه", "دانلود اپ",
        "کانال تلگرام", "تبلیغ", "بروزرسانی", "اپلیکیشن", "فیلترشکن",
        "v2ray", "vpn", "telegram", "t.me", "apk"
    )

    private fun isJunk(title: String): Boolean {
        val value = title.lowercase()
        return value.isBlank() || JUNK_TITLE.any { value.contains(it) }
    }

    /**
     * List/search payload → media. Next Movie ids are stored as negative values so a
     * Next-only title can never collide with an IranFlix id in navigation/favourites.
     */
    fun toMedia(raw: JSONObject?): RealMedia? {
        raw ?: return null
        val nextId = raw.optInt("id", 0)
        if (nextId <= 0) return null
        val title = raw.optString("title", "").trim()
        if (isJunk(title)) return null
        val imdb = raw.optDouble("imdb_rating", 0.0)
        if (imdb >= 9.9 && raw.optString("description", "").isBlank()) return null

        return RealMedia(
            id = -nextId,
            type = if (raw.optString("type") == "series") "serie" else "movie",
            title = title,
            description = raw.optString("description", ""),
            year = raw.optInt("year", 0),
            imdb = imdb,
            duration = raw.optString("duration", "").takeIf { it.isNotBlank() && it != "null" },
            image = raw.optString("poster", ""),
            cover = raw.optString("poster", ""),
            provider = "next",
            nextId = nextId
        )
    }

    fun toMediaList(items: List<JSONObject>): List<RealMedia> = items.mapNotNull { toMedia(it) }

    /** Playback links of a movie (`movie_links` array of the links payload). */
    fun movieSources(links: JSONObject?): List<RealSource> {
        val array = links?.optJSONArray("movie_links") ?: return emptyList()
        return toSources(array)
    }

    fun toSources(array: JSONArray?): List<RealSource> {
        if (array == null) return emptyList()
        val list = mutableListOf<RealSource>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url", "").replace("\\/", "/").trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            if (isJunk(url)) continue
            list.add(
                RealSource(
                    id = obj.optInt("id", 0),
                    quality = obj.optString("quality", "کیفیت اصلی").trim().ifBlank { "کیفیت اصلی" },
                    type = obj.optString("type", "").lowercase(),
                    url = url,
                    origin = "next"
                )
            )
        }
        return list
    }

    /** Seasons/episodes of a series, with numeric season+episode indices parsed from titles. */
    fun toSeasonList(links: JSONObject?): List<RealSeason> {
        val seasonsArray = links?.optJSONArray("seasons") ?: return emptyList()
        val seasons = mutableListOf<RealSeason>()
        for (i in 0 until seasonsArray.length()) {
            val seasonObj = seasonsArray.optJSONObject(i) ?: continue
            val rawTitle = seasonObj.optString("title", "").trim()
            val number = MediaMatcher.parseNumber(rawTitle.ifBlank { "فصل ${i + 1}" }, i + 1)
            val episodesArray = seasonObj.optJSONArray("episodes") ?: JSONArray()
            val episodes = mutableListOf<RealEpisode>()
            for (j in 0 until episodesArray.length()) {
                val epObj = episodesArray.optJSONObject(j) ?: continue
                val epTitle = epObj.optString("title", "").trim().ifBlank { "قسمت ${j + 1}" }
                val sources = toSources(epObj.optJSONArray("links"))
                if (sources.isEmpty()) continue
                episodes.add(
                    RealEpisode(
                        id = epObj.optInt("id", 0),
                        title = epTitle,
                        description = epObj.optString("description", ""),
                        duration = epObj.optString("duration", "").takeIf { it.isNotBlank() && it != "null" },
                        sources = sources,
                        number = MediaMatcher.parseNumber(epTitle, j + 1)
                    )
                )
            }
            if (episodes.isNotEmpty()) {
                seasons.add(
                    RealSeason(
                        id = seasonObj.optInt("id", 0),
                        title = rawTitle.ifBlank { "فصل ${number}" },
                        episodes = episodes,
                        number = number
                    )
                )
            }
        }
        return seasons
    }

    /** Metadata-only fill for fields a list endpoint leaves blank. */
    fun applyDetails(media: RealMedia, details: JSONObject?): RealMedia {
        details ?: return media
        val genres = mutableListOf<RealGenre>()
        details.optJSONArray("genres")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                if (title.isNotEmpty()) genres.add(RealGenre(obj.optInt("id", 0), title))
            }
        }
        return media.copy(
            description = media.description.ifBlank { details.optString("description", "") },
            image = media.image.ifBlank { details.optString("poster", "") },
            cover = media.cover.ifBlank { details.optString("poster", "") },
            year = media.year.takeIf { it > 0 } ?: details.optInt("year", 0),
            imdb = media.imdb.takeIf { it > 0 } ?: details.optDouble("imdb_rating", 0.0),
            duration = media.duration ?: details.optString("duration", "").takeIf { it.isNotBlank() && it != "null" },
            genres = media.genres.ifEmpty { genres }
        )
    }

    private fun JSONArray?.toObjectList(): List<JSONObject> {
        if (this == null) return emptyList()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until length()) optJSONObject(i)?.let { list.add(it) }
        return list
    }
}

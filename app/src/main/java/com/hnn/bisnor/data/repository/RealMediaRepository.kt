package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.data.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * The one and only catalogue source.
 *
 * All titles, artwork, metadata, seasons, episodes and stream links come from the
 * live IranFlix endpoints used by the existing app. There is deliberately NO mock,
 * sample or fallback catalogue here: when the network is unavailable the UI shows an
 * honest offline/error state (optionally backed by a cache of previously fetched,
 * genuine responses).
 */
object RealMediaRepository {

    private const val API_KEY = "4F5A9C3D9A86FA54EACEDDD635185"
    private const val CACHE_PREFS = "bisnor_catalog_cache_v1"
    private const val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val SERVERS = arrayOf(
        "https://server-hi-speed-iran.info",
        "https://hostinnegar.com",
        "https://windowsdiba.info"
    )

    /** Genre ids verified against live `/api/movie/by/filtres/{id}/created/...` responses. */
    object Genres {
        const val ACTION = 2
        const val ANIMATION = 3
        const val COMEDY = 13
        const val DRAMA = 10
        const val KOREAN = 36
        const val TOP250 = 32
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class MemoryEntry(val data: List<RealMedia>, val at: Long)
    private data class SeasonEntry(val data: List<RealSeason>, val at: Long)

    private val memoryLists = java.util.concurrent.ConcurrentHashMap<String, MemoryEntry>()
    private val memorySeasons = java.util.concurrent.ConcurrentHashMap<Int, SeasonEntry>()

    private const val MEMORY_TTL_MS = 5L * 60 * 1000

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun cachePrefs(): SharedPreferences? =
        appContext?.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    private val AD_KEYWORDS = listOf(
        "تبلیغ", "ورژن جدید", "اپلیکیشن", "دانلود اپ", "کانال تلگرام", "فیلترشکن",
        "v2ray", "vpn", "proxy", "simba", "darknama", "نسخه جدید", "بروزرسانی",
        "promot", "update app", "apk", "t.me", "telegram"
    )

    private fun isAdvertisement(title: String, desc: String, url: String = ""): Boolean {
        val t = title.lowercase()
        val d = desc.lowercase()
        val u = url.lowercase()
        AD_KEYWORDS.forEach { keyword ->
            if (t.contains(keyword) || d.contains(keyword)) return true
        }
        return u.endsWith(".apk") || u.contains("download_app") || u.contains("telegram")
    }

    // ---------------------------------------------------------------- transport

    private class FetchResult(val body: String?, val failed: Boolean)

    /**
     * Fetches a path from the fastest mirror that answers. Mirrors are raced with a
     * short stagger so one slow/dead host no longer serialises every catalogue page.
     */
    private suspend fun fetch(path: String): FetchResult = coroutineScope {
        val winner = CompletableDeferred<FetchResult>()
        val jobs = SERVERS.mapIndexed { index, server ->
            launch(Dispatchers.IO) {
                if (index > 0) delay(index * 400L)
                val result = fetchFrom(server, path)
                if (result.body != null) winner.complete(result)
            }
        }
        val result = withTimeoutOrNull(25_000) { winner.await() } ?: FetchResult(null, true)
        jobs.forEach { it.cancel() }
        result
    }

    private fun fetchFrom(server: String, path: String): FetchResult {
        return try {
            val request = Request.Builder()
                .url("$server$path")
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) FetchResult(body, false)
                else FetchResult(null, true)
            }
        } catch (_: Exception) {
            FetchResult(null, true)
        }
    }

    private inline fun <reified T> readCache(key: String): T? {
        val prefs = cachePrefs() ?: return null
        val json = prefs.getString("data_$key", null) ?: return null
        val savedAt = prefs.getLong("time_$key", 0L)
        if (System.currentTimeMillis() - savedAt > MAX_CACHE_AGE_MS) return null
        return try {
            gson.fromJson(json, object : TypeToken<T>() {}.type)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(key: String, value: Any) {
        val prefs = cachePrefs() ?: return
        try {
            prefs.edit()
                .putString("data_$key", gson.toJson(value))
                .putLong("time_$key", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
            // cache is best-effort only
        }
    }

    /** Network fetch with an in-memory hot cache and a real, previously-fetched disk fallback. */
    private suspend fun cachedList(path: String, cacheKey: String): Pair<List<RealMedia>, Boolean> {
        val now = System.currentTimeMillis()
        memoryLists[cacheKey]?.let { entry ->
            if (now - entry.at <= MEMORY_TTL_MS) return entry.data to false
        }

        val result = fetch(path)
        val fresh = result.body?.let { runCatching { parseMediaList(it) }.getOrNull() }
        if (!fresh.isNullOrEmpty()) {
            memoryLists[cacheKey] = MemoryEntry(fresh, now)
            writeCache(cacheKey, fresh)
            return fresh to false
        }
        val cached = readCache<List<RealMedia>>(cacheKey)
        if (!cached.isNullOrEmpty()) {
            memoryLists[cacheKey] = MemoryEntry(cached, now)
            return cached to true
        }
        return emptyList<RealMedia>() to result.failed
    }

    private suspend fun fetchList(path: String, cacheKey: String): List<RealMedia> =
        cachedList(path, cacheKey).first

    // ---------------------------------------------------------------- catalogue

    suspend fun getLatestMovies(page: Int = 0): List<RealMedia> =
        fetchList("/api/movie/by/filtres/0/created/$page/$API_KEY/", "movies_created_$page")

    suspend fun getPopularSeries(page: Int = 0): List<RealMedia> =
        fetchList("/api/serie/by/filtres/0/created/$page/$API_KEY/", "series_created_$page")

    suspend fun getTopImdbMovies(page: Int = 0): List<RealMedia> =
        fetchList("/api/movie/by/filtres/0/imdb/$page/$API_KEY/", "movies_imdb_$page")

    suspend fun getMoviesByGenre(genreId: Int, page: Int = 0): List<RealMedia> =
        fetchList("/api/movie/by/filtres/$genreId/created/$page/$API_KEY/", "movies_genre_${genreId}_$page")

    suspend fun getPostersByCountry(countryId: Int, page: Int = 0): List<RealMedia> =
        fetchList("/api/poster/by/filtres/0/$countryId/created/$page/$API_KEY/", "posters_country_${countryId}_$page")

    private val inMemoryCatalog = mutableListOf<RealMedia>()

    suspend fun getExploreCatalog(): List<RealMedia> = coroutineScope {
        if (inMemoryCatalog.size >= 50) return@coroutineScope inMemoryCatalog.toList()

        val jobs = listOf(
            async { getTopImdbMovies(0) },
            async { getTopImdbMovies(1) },
            async { getLatestMovies(0) },
            async { getLatestMovies(1) },
            async { getPopularSeries(0) },
            async { getPopularSeries(1) },
            async { getMoviesByGenre(Genres.ACTION, 0) },
            async { getMoviesByGenre(Genres.ANIMATION, 0) }
        )
        val merged = jobs.flatMap { it.await() }.distinctBy { it.id }
        if (merged.isNotEmpty()) {
            synchronized(inMemoryCatalog) {
                inMemoryCatalog.clear()
                inMemoryCatalog.addAll(merged)
            }
        }
        merged
    }

    // ---------------------------------------------------------------- home feed

    data class HomeRow(val title: String, val items: List<RealMedia>)

    data class HomeCatalog(
        val hero: List<RealMedia>,
        val rows: List<HomeRow>,
        val fromCache: Boolean,
        val failed: Boolean
    )

    /**
     * Builds the home experience exclusively from live endpoints.
     *
     * Selection logic (documented and deterministic):
     *  - Hero: highest-rated movies from the real IMDb-sorted endpoint (fallback: newest).
     *  - Rows: labelled after the endpoint that produced them.
     * No title is invented, reshuffled into a fake "personalised" list, or duplicated.
     */
    suspend fun getHomeCatalog(): HomeCatalog = coroutineScope {
        val latest0 = async { cachedList("/api/movie/by/filtres/0/created/0/$API_KEY/", "movies_created_0") }
        val latest1 = async { cachedList("/api/movie/by/filtres/0/created/1/$API_KEY/", "movies_created_1") }
        val series0 = async { cachedList("/api/serie/by/filtres/0/created/0/$API_KEY/", "series_created_0") }
        val series1 = async { cachedList("/api/serie/by/filtres/0/created/1/$API_KEY/", "series_created_1") }
        val top = async { cachedList("/api/movie/by/filtres/0/imdb/0/$API_KEY/", "movies_imdb_0") }
        val anime = async { cachedList("/api/movie/by/filtres/${Genres.ANIMATION}/created/0/$API_KEY/", "movies_genre_3_0") }
        val korean = async { cachedList("/api/movie/by/filtres/${Genres.KOREAN}/created/0/$API_KEY/", "movies_genre_36_0") }
        val action = async { cachedList("/api/movie/by/filtres/${Genres.ACTION}/created/0/$API_KEY/", "movies_genre_2_0") }

        val latest = (latest0.await().first + latest1.await().first).distinctBy { it.id }
        val series = (series0.await().first + series1.await().first).distinctBy { it.id }
        val fromCache = latest0.await().second || series0.await().second || top.await().second

        buildCatalog(
            latest = latest,
            series = series,
            topRated = top.await().first,
            anime = anime.await().first,
            korean = korean.await().first,
            action = action.await().first,
            fromCache = fromCache
        )
    }

    /**
     * Instant, cache-only snapshot (memory or previously fetched disk data) used to paint
     * the home screen before the network refresh completes. Never invents content.
     */
    fun cachedHomeCatalog(): HomeCatalog? {
        val latest = (offlineList("movies_created_0") + offlineList("movies_created_1")).distinctBy { it.id }
        val series = (offlineList("series_created_0") + offlineList("series_created_1")).distinctBy { it.id }
        val top = offlineList("movies_imdb_0")
        val anime = offlineList("movies_genre_3_0")
        val korean = offlineList("movies_genre_36_0")
        val action = offlineList("movies_genre_2_0")
        if (latest.isEmpty() && series.isEmpty() && top.isEmpty()) return null

        return buildCatalog(latest, series, top, anime, korean, action, fromCache = true)
    }

    private fun offlineList(cacheKey: String): List<RealMedia> {
        val entry = memoryLists[cacheKey]
        if (entry != null && System.currentTimeMillis() - entry.at <= MEMORY_TTL_MS) return entry.data
        return readCache<List<RealMedia>>(cacheKey) ?: emptyList()
    }

    private fun buildCatalog(
        latest: List<RealMedia>,
        series: List<RealMedia>,
        topRated: List<RealMedia>,
        anime: List<RealMedia>,
        korean: List<RealMedia>,
        action: List<RealMedia>,
        fromCache: Boolean
    ): HomeCatalog {
        val heroPool = (topRated + latest).filter { it.cover.isNotEmpty() || it.image.isNotEmpty() }
        val hero = heroPool.distinctBy { it.id }.take(6)

        val seen = hero.map { it.id }.toMutableSet()
        fun row(title: String, source: List<RealMedia>): HomeRow? {
            val items = source.filter { seen.add(it.id) && (it.image.isNotEmpty() || it.cover.isNotEmpty()) }
            return if (items.isEmpty()) null else HomeRow(title, items.take(20))
        }

        val rows = listOfNotNull(
            row("تازه اضافه‌شده‌ها", latest),
            row("سریال‌های تازه", series),
            row("برترین‌های IMDb", topRated),
            row("انیمیشن و انیمه", anime),
            row("سریال و فیلم کره‌ای", korean),
            row("اکشن و هیجان", action)
        )
        return HomeCatalog(hero, rows, fromCache, rows.isEmpty() && hero.isEmpty())
    }

    // ---------------------------------------------------------------- search

    suspend fun search(query: String): List<RealMedia> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext getExploreCatalog()

        val serverResults = try {
            val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val body = fetch("/api/search/$encoded/$API_KEY/").body
            if (body.isNullOrBlank()) emptyList() else {
                val jsonObject = JSONObject(body)
                val posters = jsonObject.optJSONArray("posters") ?: JSONArray()
                parseMediaList(posters.toString())
            }
        } catch (_: Exception) {
            emptyList()
        }

        val catalog = if (inMemoryCatalog.isNotEmpty()) inMemoryCatalog.toList() else getExploreCatalog()
        val qLower = q.lowercase()
        val localMatches = catalog.filter { item ->
            val meta = com.hnn.bisnor.util.MediaMetadataHelper.parse(item.description, item.imdb)
            item.title.lowercase().contains(qLower) ||
                item.description.lowercase().contains(qLower) ||
                (meta.director ?: "").lowercase().contains(qLower) ||
                (meta.actors ?: "").lowercase().contains(qLower) ||
                item.genres.any { it.title.contains(q, ignoreCase = true) }
        }
        (serverResults + localMatches).distinctBy { it.id }
    }

    // ---------------------------------------------------------------- series

    suspend fun getSeriesSeasons(seriesId: Int): List<RealSeason> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        memorySeasons[seriesId]?.let { entry ->
            if (now - entry.at <= MEMORY_TTL_MS) return@withContext entry.data
        }

        val result = fetch("/api/season/by/serie/$seriesId/$API_KEY/")
        val parsed = result.body?.let { body ->
            runCatching {
                val array = JSONArray(body)
                val seasons = mutableListOf<RealSeason>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawSeasonTitle = obj.optString("title", "فصل ${i + 1}").trim()
                    val seasonTitle = if (rawSeasonTitle.isEmpty() || rawSeasonTitle == "null") {
                        "فصل ${i + 1}"
                    } else rawSeasonTitle

                    val episodesArray = obj.optJSONArray("episodes") ?: JSONArray()
                    val episodes = mutableListOf<RealEpisode>()
                    for (j in 0 until episodesArray.length()) {
                        val epObj = episodesArray.getJSONObject(j)
                        val epTitle = epObj.optString("title", "قسمت ${j + 1}").trim()
                        val epDesc = epObj.optString("description", "")
                        if (isAdvertisement(epTitle, epDesc)) continue

                        val sources = parseSources(epObj.optJSONArray("sources"))
                        if (sources.isEmpty()) continue

                        episodes.add(
                            RealEpisode(
                                id = epObj.optInt("id", 0),
                                title = if (epTitle.isEmpty() || epTitle == "null") "قسمت ${j + 1}" else epTitle,
                                description = epDesc,
                                duration = epObj.optString("duration", null).takeIf { it != "null" && it != "N/A" },
                                image = epObj.optString("image", ""),
                                sources = sources
                            )
                        )
                    }
                    if (episodes.isNotEmpty()) {
                        seasons.add(RealSeason(id = obj.optInt("id", 0), title = seasonTitle, episodes = episodes))
                    }
                }
                seasons
            }.getOrNull()
        }

        if (!parsed.isNullOrEmpty()) {
            memorySeasons[seriesId] = SeasonEntry(parsed, now)
            writeCache("seasons_$seriesId", parsed)
            parsed
        } else {
            val cached = readCache<List<RealSeason>>("seasons_$seriesId") ?: emptyList()
            if (cached.isNotEmpty()) memorySeasons[seriesId] = SeasonEntry(cached, now)
            cached
        }
    }

    // ---------------------------------------------------------------- parsing

    private fun parseMediaList(json: String): List<RealMedia> {
        val list = mutableListOf<RealMedia>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val desc = obj.optString("description", "").trim()
                if (title.isEmpty() || isAdvertisement(title, desc)) continue

                val sources = parseSources(obj.optJSONArray("sources"))
                val genres = parseGenres(obj.optJSONArray("genres"))
                val isAnim = genres.any { it.title.contains("انیمیشن") || it.title.contains("کارتون") || it.title.contains("انیمه") } ||
                    title.contains("انیمه") || title.contains("انیمیشن")
                val parsedMeta = com.hnn.bisnor.util.MediaMetadataHelper.parse(desc, obj.optDouble("imdb", 0.0), isAnim)
                val finalImdb = parsedMeta.extractedImdb ?: obj.optDouble("imdb", 0.0)

                list.add(
                    RealMedia(
                        id = obj.optInt("id", 0),
                        type = obj.optString("type", "movie"),
                        title = title,
                        description = desc,
                        year = obj.optInt("year", 0),
                        imdb = finalImdb,
                        rating = obj.optDouble("rating", 0.0),
                        duration = obj.optString("duration", "").takeIf { it.isNotBlank() && it != "null" && it != "N/A" },
                        image = obj.optString("image", ""),
                        cover = obj.optString("cover", ""),
                        genres = genres,
                        sources = sources,
                        country = parseCountries(obj.optJSONArray("country")),
                        classification = obj.optString("classification", "").trim()
                            .takeIf { it.isNotBlank() && it != "null" && !it.equals("N/A", true) },
                        people = extractPeople(obj, desc, parsedMeta)
                    )
                )
            } catch (_: Exception) {
                continue
            }
        }
        return list
    }

    /**
     * Credits come from the provider when it sends structured fields, otherwise the
     * same description labels the web client reads. Nothing is invented here.
     */
    private fun extractPeople(
        obj: JSONObject,
        description: String,
        meta: com.hnn.bisnor.util.ParsedMediaInfo
    ): List<RealPerson> {
        val people = mutableListOf<RealPerson>()

        fun addName(rawName: String, role: String, photo: String) {
            val name = rawName.trim().take(80)
            if (name.isEmpty()) return
            if (people.any { it.role == role && it.name.equals(name, true) }) return
            people.add(
                RealPerson(
                    name = name,
                    role = role,
                    photo = photo.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
                )
            )
        }

        fun addValue(value: Any?, role: String) {
            when (value) {
                null, JSONObject.NULL -> Unit
                is JSONArray -> for (i in 0 until value.length()) addValue(value.opt(i), role)
                is JSONObject -> {
                    val name = value.optString("name", "")
                        .ifBlank { value.optString("title", "") }
                        .ifBlank { value.optString("full_name", "") }
                        .trim()
                    val photo = value.optString("photo", "")
                        .ifBlank { value.optString("image", "") }
                        .ifBlank { value.optString("avatar", "") }
                        .trim()
                    addName(name, role, photo)
                }
                else -> value.toString().split(Regex("[,،|]")).forEach { addName(it.trim(), role, "") }
            }
        }

        addValue(obj.opt("actors"), "actor")
        addValue(obj.opt("cast"), "actor")
        addValue(obj.opt("stars"), "actor")
        addValue(obj.opt("director"), "director")
        addValue(obj.opt("directors"), "director")
        addValue(obj.opt("writer"), "writer")
        addValue(obj.opt("writers"), "writer")
        addValue(obj.opt("screenwriter"), "writer")

        if (!meta.director.isNullOrBlank()) addName(meta.director, "director", "")
        if (!meta.actors.isNullOrBlank()) addValue(meta.actors, "actor")

        Regex("""(?i)(?:نویسنده|فیلمنامه|writer)\s*[:：\-]?\s*([^\n\r]+)""")
            .find(description)?.groupValues?.getOrNull(1)
            ?.let { addValue(it, "writer") }

        return people.take(12)
    }

    private fun parseGenres(array: JSONArray?): List<RealGenre> {
        if (array == null) return emptyList()
        val list = mutableListOf<RealGenre>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(RealGenre(obj.optInt("id"), obj.optString("title").trim()))
        }
        return list
    }

    private fun parseSources(array: JSONArray?): List<RealSource> {
        if (array == null) return emptyList()
        val list = mutableListOf<RealSource>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val quality = obj.optString("quality", "کیفیت اصلی").trim()
            val type = obj.optString("type", "mp4")
            val url = obj.optString("url", "").replace("\\/", "/")
            if (isAdvertisement(quality, "", url)) continue
            if (url.isNotEmpty()) {
                list.add(RealSource(obj.optInt("id"), quality, type, url))
            }
        }
        return list
    }

    private fun parseCountries(array: JSONArray?): List<RealCountry> {
        if (array == null) return emptyList()
        val list = mutableListOf<RealCountry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(RealCountry(obj.optInt("id"), obj.optString("title").trim(), obj.optString("image")))
        }
        return list
    }
}

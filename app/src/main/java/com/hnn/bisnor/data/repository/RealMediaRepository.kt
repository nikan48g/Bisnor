package com.hnn.bisnor.data.repository

import com.hnn.bisnor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object RealMediaRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val API_KEY = "4F5A9C3D9A86FA54EACEDDD635185"
    private val SERVERS = arrayOf(
        "https://server-hi-speed-iran.info",
        "https://hostinnegar.com",
        "https://windowsdiba.info"
    )

    private val AD_KEYWORDS = listOf(
        "تبلیغ", "ورژن جدید", "اپلیکیشن", "دانلود اپ", "کانال تلگرام", "فیلترشکن", 
        "v2ray", "vpn", "proxy", "simba", "darknama", "نسخه جدید", "بروزرسانی",
        "promot", "update app", "apk", "t.me", "telegram"
    )

    private fun isAdvertisement(title: String, desc: String, url: String = ""): Boolean {
        val t = title.lowercase()
        val d = desc.lowercase()
        val u = url.lowercase()
        if (AD_KEYWORDS.any { t.contains(it) || d.contains(it) }) return true
        if (u.endsWith(".apk") || u.contains("download_app") || u.contains("telegram")) return true
        return false
    }

    private suspend fun fetchJson(path: String): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (server in SERVERS) {
            try {
                val url = "$server$path"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) return@withContext body
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("Network error connecting to servers")
    }

    suspend fun getLatestMovies(page: Int = 0): List<RealMedia> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("/api/movie/by/filtres/0/created/$page/$API_KEY")
            parseMediaList(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularSeries(page: Int = 0): List<RealMedia> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("/api/serie/by/filtres/0/created/$page/$API_KEY")
            parseMediaList(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTopImdbMovies(page: Int = 0): List<RealMedia> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("/api/movie/by/filtres/0/imdb/$page/$API_KEY")
            parseMediaList(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMoviesByGenre(genreId: Int, page: Int = 0): List<RealMedia> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("/api/movie/by/filtres/$genreId/created/$page/$API_KEY")
            parseMediaList(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPostersByCountry(countryId: Int, page: Int = 0): List<RealMedia> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("/api/poster/by/filtres/0/$countryId/created/$page/$API_KEY")
            parseMediaList(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun search(query: String): List<RealMedia> = withContext(Dispatchers.IO) {
        if (query.trim().isEmpty()) {
            return@withContext getLatestMovies(0)
        }
        try {
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val json = fetchJson("/api/search/$encoded/$API_KEY/")
            val jsonObject = JSONObject(json)
            val postersArray = jsonObject.optJSONArray("posters") ?: JSONArray()
            parseMediaList(postersArray.toString())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSeriesSeasons(seriesId: Int): List<RealSeason> = withContext(Dispatchers.IO) {
        try {
            val json = fetchJson("/api/season/by/serie/$seriesId/$API_KEY/")
            val array = JSONArray(json)
            val seasons = mutableListOf<RealSeason>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawSeasonTitle = obj.optString("title", "فصل ${i + 1}").trim()
                val seasonTitle = if (rawSeasonTitle.isEmpty() || rawSeasonTitle == "null") "فصل ${i + 1}" else rawSeasonTitle

                val episodesArray = obj.optJSONArray("episodes") ?: JSONArray()
                val episodes = mutableListOf<RealEpisode>()
                for (j in 0 until episodesArray.length()) {
                    val epObj = episodesArray.getJSONObject(j)
                    val epTitle = epObj.optString("title", "قسمت ${j + 1}").trim()
                    val epDesc = epObj.optString("description", "")
                    if (isAdvertisement(epTitle, epDesc)) continue

                    val sources = parseSources(epObj.optJSONArray("sources"))
                    if (sources.isNotEmpty()) {
                        episodes.add(
                            RealEpisode(
                                id = epObj.optInt("id", 0),
                                title = if (epTitle.isEmpty() || epTitle == "null") "قسمت ${j + 1}" else epTitle,
                                description = epDesc,
                                duration = epObj.optString("duration", null).takeIf { it != "null" },
                                image = epObj.optString("image", ""),
                                sources = sources
                            )
                        )
                    }
                }
                if (episodes.isNotEmpty()) {
                    seasons.add(
                        RealSeason(
                            id = obj.optInt("id", 0),
                            title = seasonTitle,
                            episodes = episodes
                        )
                    )
                }
            }
            seasons
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseMediaList(json: String): List<RealMedia> {
        val list = mutableListOf<RealMedia>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val title = cleanText(obj.optString("title", ""))
                val desc = cleanText(obj.optString("description", ""))
                if (isAdvertisement(title, desc)) continue

                val sources = parseSources(obj.optJSONArray("sources"))

                list.add(
                    RealMedia(
                        id = obj.optInt("id", 0),
                        type = obj.optString("type", "movie"),
                        title = title,
                        description = desc,
                        year = obj.optInt("year", 0),
                        imdb = obj.optDouble("imdb", 0.0),
                        rating = obj.optDouble("rating", 0.0),
                        duration = obj.optString("duration", null).takeIf { it != "null" && it != "N/A" },
                        image = obj.optString("image", ""),
                        cover = obj.optString("cover", ""),
                        genres = parseGenres(obj.optJSONArray("genres")),
                        sources = sources,
                        country = parseCountries(obj.optJSONArray("country"))
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }
        return list
    }

    private fun parseGenres(array: JSONArray?): List<RealGenre> {
        if (array == null) return emptyList()
        val list = mutableListOf<RealGenre>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(RealGenre(obj.optInt("id"), cleanText(obj.optString("title"))))
        }
        return list
    }

    private fun parseSources(array: JSONArray?): List<RealSource> {
        if (array == null) return emptyList()
        val list = mutableListOf<RealSource>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val quality = cleanText(obj.optString("quality", "کیفیت اصلی"))
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
            list.add(RealCountry(obj.optInt("id"), cleanText(obj.optString("title")), obj.optString("image")))
        }
        return list
    }

    private fun cleanText(text: String): String {
        return text.trim()
    }
}

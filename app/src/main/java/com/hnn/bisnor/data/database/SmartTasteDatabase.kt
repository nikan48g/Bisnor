package com.hnn.bisnor.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class GenreStat(
    val genreName: String,
    val count: Int,
    val percentage: Int
)

data class MonthlyStats(
    val genreStats: List<GenreStat>,
    val movieDurationMs: Long,
    val seriesDurationMs: Long,
    val totalDataUsageBytes: Long
)

data class TasteRule(
    val id: Long,
    val ruleType: String, // EXCLUDE_GENRE, MIN_RATING, MIN_YEAR
    val ruleValue: String,
    val isEnabled: Boolean
)

class SmartTasteDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "bisnor_taste.db"
        const val DATABASE_VERSION = 2

        const val TABLE_FAVORITES = "user_favorite_genres"
        const val TABLE_WATCH_EVENTS = "watch_taste_events"
        const val TABLE_RULES = "taste_custom_rules"
        const val TABLE_PREFERENCES = "user_app_preferences"

        val DEFAULT_GENRES = listOf(
            "اکشن", "ماجراجویی", "کمدی", "درام", "علمی تخیلی",
            "انیمیشن", "انیمه", "ترسناک", "جنایی", "عاشقانه", "رازآلود",
            "فانتزی", "هیجان انگیز", "تاریخی", "مستند"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Table 1: User Explicit Favorite Genres
        db.execSQL(
            """
            CREATE TABLE $TABLE_FAVORITES (
                genre_name TEXT PRIMARY KEY,
                is_favorite INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Table 2: Watch Events for Machine Learning & Taste Analytics
        db.execSQL(
            """
            CREATE TABLE $TABLE_WATCH_EVENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                media_id INTEGER NOT NULL,
                media_title TEXT NOT NULL,
                media_type TEXT NOT NULL DEFAULT 'movie',
                genres TEXT NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                bytes_used INTEGER NOT NULL DEFAULT 0,
                is_completed INTEGER NOT NULL DEFAULT 0,
                rating INTEGER DEFAULT 0,
                watched_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Table 3: Custom Taste Filtering Rules
        db.execSQL(
            """
            CREATE TABLE $TABLE_RULES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_type TEXT NOT NULL,
                rule_value TEXT NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1
            );
            """.trimIndent()
        )

        // Table 4: Local offline user preferences and data usage
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PREFERENCES (
                pref_key TEXT PRIMARY KEY,
                pref_value TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_WATCH_EVENTS ADD COLUMN media_type TEXT NOT NULL DEFAULT 'movie'")
                db.execSQL("ALTER TABLE $TABLE_WATCH_EVENTS ADD COLUMN bytes_used INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_PREFERENCES (pref_key TEXT PRIMARY KEY, pref_value TEXT NOT NULL)")
            } catch (_: Exception) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_WATCH_EVENTS")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_RULES")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_PREFERENCES")
                onCreate(db)
            }
        }
    }

    // --- Watch Events & Learning ---

    fun recordWatchEvent(
        mediaId: Int,
        title: String,
        mediaType: String = "movie",
        genres: List<String>,
        durationMs: Long = 0L,
        bytesUsed: Long = 0L,
        isCompleted: Boolean = false
    ) {
        if (genres.isEmpty()) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put("media_id", mediaId)
            put("media_title", title)
            put("media_type", mediaType)
            put("genres", genres.joinToString(","))
            put("duration_ms", durationMs)
            put("bytes_used", bytesUsed)
            put("is_completed", if (isCompleted) 1 else 0)
            put("watched_at", System.currentTimeMillis())
        }
        db.insert(TABLE_WATCH_EVENTS, null, values)
    }

    fun recordDataUsage(bytes: Long) {
        if (bytes <= 0) return
        val current = getDataUsageBytes()
        val total = current + bytes
        val db = writableDatabase
        val values = ContentValues().apply {
            put("pref_key", "total_bytes_used")
            put("pref_value", total.toString())
        }
        db.insertWithOnConflict(TABLE_PREFERENCES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getDataUsageBytes(): Long {
        val db = readableDatabase
        val cursor = db.query(TABLE_PREFERENCES, arrayOf("pref_value"), "pref_key = 'total_bytes_used'", null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)?.toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    fun getContentTypePreference(): String {
        val db = readableDatabase
        val cursor = db.query(TABLE_PREFERENCES, arrayOf("pref_value"), "pref_key = 'content_preference'", null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) ?: "all"
            }
        }
        return "all"
    }

    fun setContentTypePreference(pref: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("pref_key", "content_preference")
            put("pref_value", pref)
        }
        db.insertWithOnConflict(TABLE_PREFERENCES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMonthlyStats(): MonthlyStats {
        val db = readableDatabase
        val sinceTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L) // Last 30 days
        val genreCounts = mutableMapOf<String, Int>()
        var totalWeight = 0
        var movieDur = 0L
        var seriesDur = 0L
        var totalBytes = 0L

        val cursor = db.query(
            TABLE_WATCH_EVENTS,
            arrayOf("genres", "media_type", "duration_ms", "bytes_used", "is_completed"),
            "watched_at >= ?",
            arrayOf(sinceTime.toString()),
            null, null, null
        )

        cursor.use {
            val genresCol = it.getColumnIndex("genres")
            val typeCol = it.getColumnIndex("media_type")
            val durCol = it.getColumnIndex("duration_ms")
            val bytesCol = it.getColumnIndex("bytes_used")
            val compCol = it.getColumnIndex("is_completed")

            while (it.moveToNext()) {
                val rawGenres = if (genresCol != -1) it.getString(genresCol) ?: "" else ""
                val mType = if (typeCol != -1) it.getString(typeCol) ?: "movie" else "movie"
                val dur = if (durCol != -1) it.getLong(durCol) else 0L
                val b = if (bytesCol != -1) it.getLong(bytesCol) else 0L
                val isComp = if (compCol != -1) it.getInt(compCol) == 1 else false
                val weight = if (isComp) 2 else 1

                if (mType == "series") {
                    seriesDur += dur
                } else {
                    movieDur += dur
                }
                totalBytes += b

                rawGenres.split(",").map { g -> g.trim() }.filter { g -> g.isNotEmpty() }.forEach { g ->
                    genreCounts[g] = (genreCounts[g] ?: 0) + weight
                    totalWeight += weight
                }
            }
        }

        val stats = if (totalWeight == 0) {
            getGenreStats()
        } else {
            genreCounts.map { (genre, count) ->
                val pct = ((count.toDouble() / totalWeight) * 100).toInt().coerceIn(1, 100)
                GenreStat(genreName = genre, count = count, percentage = pct)
            }.sortedByDescending { it.percentage }
        }

        val cumulativeAppBytes = getDataUsageBytes()
        val finalBytes = if (totalBytes > cumulativeAppBytes) totalBytes else cumulativeAppBytes

        return MonthlyStats(
            genreStats = stats,
            movieDurationMs = movieDur,
            seriesDurationMs = seriesDur,
            totalDataUsageBytes = finalBytes
        )
    }

    fun getGenreStats(): List<GenreStat> {
        val db = readableDatabase
        val genreCounts = mutableMapOf<String, Int>()

        val cursor = db.query(TABLE_WATCH_EVENTS, arrayOf("genres", "is_completed"), null, null, null, null, null)
        var totalWeight = 0

        cursor.use {
            val genresCol = it.getColumnIndex("genres")
            val compCol = it.getColumnIndex("is_completed")

            while (it.moveToNext()) {
                val rawGenres = if (genresCol != -1) it.getString(genresCol) ?: "" else ""
                val isComp = if (compCol != -1) it.getInt(compCol) == 1 else false
                val weight = if (isComp) 2 else 1

                rawGenres.split(",").map { g -> g.trim() }.filter { g -> g.isNotEmpty() }.forEach { g ->
                    genreCounts[g] = (genreCounts[g] ?: 0) + weight
                    totalWeight += weight
                }
            }
        }

        if (totalWeight == 0) return emptyList()

        return genreCounts.map { (genre, count) ->
            val pct = ((count.toDouble() / totalWeight) * 100).toInt().coerceIn(1, 100)
            GenreStat(genreName = genre, count = count, percentage = pct)
        }.sortedByDescending { it.percentage }
    }

    fun resetLearnedTaste() {
        val db = writableDatabase
        db.delete(TABLE_WATCH_EVENTS, null, null)
    }

    // --- User Explicit Favorite Genres ---

    fun setFavoriteGenre(genre: String, isFavorite: Boolean) {
        val db = writableDatabase
        if (isFavorite) {
            val values = ContentValues().apply {
                put("genre_name", genre)
                put("is_favorite", 1)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete(TABLE_FAVORITES, "genre_name = ?", arrayOf(genre))
        }
    }

    fun getFavoriteGenres(): Set<String> {
        val db = readableDatabase
        val set = mutableSetOf<String>()
        val cursor = db.query(TABLE_FAVORITES, arrayOf("genre_name"), "is_favorite = 1", null, null, null, null)
        cursor.use {
            val col = it.getColumnIndex("genre_name")
            while (it.moveToNext()) {
                if (col != -1) {
                    val g = it.getString(col)
                    if (!g.isNullOrBlank()) set.add(g)
                }
            }
        }
        return set
    }

    // --- Custom Rules ---

    fun getAllRules(): List<TasteRule> {
        val db = readableDatabase
        val list = mutableListOf<TasteRule>()
        val cursor = db.query(TABLE_RULES, null, null, null, null, null, "id DESC")
        cursor.use {
            val idCol = it.getColumnIndex("id")
            val typeCol = it.getColumnIndex("rule_type")
            val valCol = it.getColumnIndex("rule_value")
            val enCol = it.getColumnIndex("is_enabled")

            while (it.moveToNext()) {
                list.add(
                    TasteRule(
                        id = if (idCol != -1) it.getLong(idCol) else 0L,
                        ruleType = if (typeCol != -1) it.getString(typeCol) ?: "" else "",
                        ruleValue = if (valCol != -1) it.getString(valCol) ?: "" else "",
                        isEnabled = if (enCol != -1) it.getInt(enCol) == 1 else true
                    )
                )
            }
        }
        return list
    }

    fun addRule(ruleType: String, ruleValue: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("rule_type", ruleType)
            put("rule_value", ruleValue)
            put("is_enabled", 1)
        }
        db.insert(TABLE_RULES, null, values)
    }

    fun deleteRule(ruleId: Long) {
        val db = writableDatabase
        db.delete(TABLE_RULES, "id = ?", arrayOf(ruleId.toString()))
    }

    fun getTopRecommendedGenres(): List<String> {
        val favorites = getFavoriteGenres().toList()
        val stats = getGenreStats().map { it.genreName }
        val excluded = getAllRules()
            .filter { it.isEnabled && it.ruleType == "EXCLUDE_GENRE" }
            .map { it.ruleValue }
            .toSet()

        val combined = (favorites + stats).distinct().filterNot { excluded.contains(it) }
        return if (combined.isNotEmpty()) combined else DEFAULT_GENRES.take(4)
    }
}

package com.hnn.bisnor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealPerson
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * In-memory index of every credit the app has already seen.
 *
 * Providers only search titles and descriptions, so a person's name alone returns almost
 * nothing. Every list payload and every opened detail screen registers its cast/crew here,
 * which lets the search row and the person screen answer instantly – and lets a film that
 * is already on screen always stay in its actor's works.
 *
 * Nothing is invented: an entry only exists after a real credit was parsed from a payload.
 */
object PersonIndex {

    data class Entry(
        val name: String,
        val nameFa: String,
        val photo: String,
        val role: String,
        val character: String,
        val works: List<RealMedia>
    ) {
        val displayName: String get() = name.ifBlank { nameFa }
        val searchName: String get() = listOf(name, nameFa).filter { it.isNotBlank() }.joinToString(" ")
    }

    private class Bucket(var name: String) {
        var nameFa: String = ""
        var photo: String = ""
        var role: String = "actor"
        var character: String = ""
        val works = LinkedHashMap<Int, RealMedia>()
    }

    private val buckets = LinkedHashMap<String, Bucket>()

    // Credits (name, Persian name, portrait, role) survive restarts so a Persian actor
    // search still finds people on a fresh process. Works are re-attached as lists load.
    private const val PREFS = "bisnor_people_index_v1"
    private var prefs: SharedPreferences? = null
    private var savePending = false
    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadCredits()
    }

    private fun loadCredits() {
        val raw = prefs?.getString("credits", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val nameFa = obj.optString("nameFa", "").trim()
                if (!isPlausibleName(name.ifBlank { nameFa })) continue
                val key = normalize(name.ifBlank { nameFa })
                if (key.isEmpty()) continue
                val bucket = buckets.getOrPut(key) { Bucket(name.ifBlank { nameFa }) }
                if (bucket.name.isBlank()) bucket.name = name
                if (nameFa.isNotBlank()) bucket.nameFa = nameFa
                if (bucket.photo.isBlank()) bucket.photo = obj.optString("photo", "")
                if (bucket.role == "actor") bucket.role = obj.optString("role", "actor")
            }
        } catch (_: Exception) {
            // A broken index must never block search.
        }
    }

    private fun scheduleSave() {
        if (prefs == null || savePending) return
        savePending = true
        handler.postDelayed({ saveCredits() }, 2500)
    }

    @Synchronized
    private fun saveCredits() {
        savePending = false
        val store = prefs ?: return
        try {
            val array = JSONArray()
            buckets.values.forEach { bucket ->
                if (bucket.name.isBlank() && bucket.nameFa.isBlank()) return@forEach
                array.put(
                    JSONObject()
                        .put("name", bucket.name)
                        .put("nameFa", bucket.nameFa)
                        .put("photo", bucket.photo)
                        .put("role", bucket.role)
                )
            }
            store.edit().putString("credits", array.toString()).apply()
        } catch (_: Exception) {
            // best effort only
        }
    }

    /** Normalises Persian/Arabic letters, removes diacritics, ZWNJ and repeated spaces. */
    fun normalize(value: String): String {
        val lowered = value.lowercase()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('\u200c', ' ')
            .replace('\u200f', ' ')
            .replace('\u200e', ' ')
        val stripped = Normalizer.normalize(lowered, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun isPlausibleName(value: String): Boolean {
        val name = value.trim()
        if (name.length < 2 || name.length > 60) return false
        if (name.any { it.isDigit() }) return false
        val words = name.split(' ').filter { it.isNotBlank() }
        if (words.size > 6) return false
        // Description fragments ("... و هنرمندانی همچون ...") are not names.
        val lower = name.lowercase()
        if (lower.startsWith("به ") || lower.contains(" همچون ") || lower.contains(" هنرمندانی")) return false
        return true
    }

    @Synchronized
    fun remember(media: RealMedia) {
        if (media.title.isBlank()) return
        media.people.forEach { person ->
            if (!isPlausibleName(person.name.ifBlank { person.nameFa })) return@forEach
            val key = normalize(person.name.ifBlank { person.nameFa })
            if (key.isEmpty()) return@forEach
            val bucket = buckets.getOrPut(key) { Bucket(person.name.ifBlank { person.nameFa }) }
            if (person.name.isNotBlank() && bucket.name.isBlank()) bucket.name = person.name
            if (person.nameFa.isNotBlank()) bucket.nameFa = person.nameFa
            if (person.photo.isNotBlank()) bucket.photo = person.photo
            if (person.character.isNotBlank()) bucket.character = person.character
            // A director credit wins over an actor credit only when the person has no role yet.
            if (bucket.role.isBlank() || (bucket.role == "actor" && person.role.isNotBlank() && person.role != "actor")) {
                bucket.role = person.role.ifBlank { bucket.role }
            }
            bucket.works[media.id] = media
        }
        if (media.people.isNotEmpty()) scheduleSave()
    }

    @Synchronized
    fun rememberAll(items: List<RealMedia>) {
        items.forEach { remember(it) }
    }

    /** People whose English or Persian name contains every token of [query]. */
    @Synchronized
    fun search(query: String, limit: Int = 12): List<Entry> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val words = normalize(query)
        return buckets.values
            .asSequence()
            .mapNotNull { bucket ->
                val haystack = normalize(bucket.name + " " + bucket.nameFa)
                if (haystack.isEmpty()) return@mapNotNull null
                val matches = tokens.all { haystack.contains(it) }
                if (!matches) return@mapNotNull null
                // Rank exact/prefix matches first.
                val score = when {
                    haystack == words -> 0
                    haystack.startsWith(words) -> 1
                    else -> 2
                }
                score to bucket
            }
            .sortedWith(compareBy({ it.first }, { -it.second.works.size }))
            .take(limit)
            .map { (_, bucket) ->
                Entry(
                    name = bucket.name,
                    nameFa = bucket.nameFa,
                    photo = bucket.photo,
                    role = bucket.role,
                    character = bucket.character,
                    works = bucket.works.values.sortedWith(
                        compareByDescending<RealMedia> { it.year }.thenBy { it.title.lowercase() }
                    )
                )
            }
            .toList()
    }

    @Synchronized
    fun works(name: String): List<RealMedia> {
        val key = normalize(name)
        if (key.isEmpty()) return emptyList()
        return buckets[key]?.works?.values?.toList().orEmpty()
    }

    @Synchronized
    fun entry(name: String): Entry? {
        val key = normalize(name)
        if (key.isEmpty()) return null
        val bucket = buckets[key] ?: return null
        return Entry(bucket.name, bucket.nameFa, bucket.photo, bucket.role, bucket.character, bucket.works.values.toList())
    }

    /** Photo URL known for this person, if any payload carried one. */
    @Synchronized
    fun photo(name: String): String {
        val key = normalize(name)
        return buckets[key]?.photo.orEmpty()
    }
}

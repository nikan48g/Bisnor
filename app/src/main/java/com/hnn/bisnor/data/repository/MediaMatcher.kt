package com.hnn.bisnor.data.repository

import com.hnn.bisnor.data.model.RealEpisode
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealSeason
import com.hnn.bisnor.data.model.RealSource
import java.text.Normalizer

/**
 * Cross-provider title matching. IranFlix and Next Movie both publish the original
 * (English) title, so a normalised exact match covers almost everything; short
 * title variations and typos fall back to containment/trigram similarity.
 */
object MediaMatcher {

    private val COMBINING = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^0-9a-z\\u0600-\\u06FF ]+")
    private val SPACES = Regex("\\s+")
    private val PARENS = Regex("[（(\\[{].*?[)）\\]}]")
    private val DIACRITICS = Regex("[\\u064B-\\u0652\\u0670\\u200c\\u200e\\u200f]")

    fun normalizeTitle(raw: String?): String {
        var value = raw.orEmpty()
        value = PARENS.replace(value, " ")
        value = Normalizer.normalize(value, Normalizer.Form.NFKD)
        value = COMBINING.replace(value, "")
        value = value.replace('\u064A', '\u06CC').replace('\u0649', '\u06CC')
            .replace('\u0643', '\u06A9').replace('\u0629', '\u0647')
        value = DIACRITICS.replace(value, " ")
        value = value.lowercase()
        value = NON_WORD.replace(value, " ")
        return SPACES.replace(value, " ").trim()
    }

    private fun trigrams(value: String): Set<String> {
        if (value.length < 3) return emptySet()
        return (0..value.length - 3).mapTo(HashSet()) { value.substring(it, it + 3) }
    }

    private fun similarity(a: String, b: String): Double {
        val left = trigrams(a)
        val right = trigrams(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val shared = left.count { it in right }
        return shared.toDouble() / minOf(left.size, right.size)
    }

    private fun sameKind(a: RealMedia, b: RealMedia) = a.isSeries == b.isSeries

    private fun yearsCompatible(a: RealMedia, b: RealMedia): Boolean {
        if (a.year <= 0 || b.year <= 0) return true
        return kotlin.math.abs(a.year - b.year) <= 1
    }

    fun matchScore(a: RealMedia, b: RealMedia): Double {
        if (!sameKind(a, b)) return 0.0
        val left = normalizeTitle(a.title)
        val right = normalizeTitle(b.title)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return if (yearsCompatible(a, b)) 1.0 else 0.6
        val shorter = if (left.length <= right.length) left else right
        val longer = if (left.length <= right.length) right else left
        if (shorter.length >= 5 && longer.contains(shorter)) return if (yearsCompatible(a, b)) 0.92 else 0.5
        if (yearsCompatible(a, b) && similarity(left, right) >= 0.8) return 0.78
        return 0.0
    }

    fun findMatch(candidates: List<RealMedia>, value: RealMedia): RealMedia? {
        var best: RealMedia? = null
        var bestScore = 0.0
        candidates.forEach { candidate ->
            val score = matchScore(candidate, value)
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return if (bestScore >= 0.75) best else null
    }

    /** IranFlix metadata wins; both ids are kept so links/seasons can be merged. */
    fun merge(iranflix: RealMedia, next: RealMedia): RealMedia = iranflix.copy(
        provider = "both",
        nextId = next.nextId,
        iranflixId = iranflix.iranflixId.takeIf { it > 0 } ?: iranflix.id,
        title = iranflix.title.ifBlank { next.title },
        description = iranflix.description.ifBlank { next.description },
        image = iranflix.image.ifBlank { next.image },
        cover = iranflix.cover.ifBlank { next.cover },
        imdb = if (iranflix.imdb > 0) iranflix.imdb else next.imdb,
        year = if (iranflix.year > 0) iranflix.year else next.year,
        nextSources = next.sources
    )

    fun mergedSeasons(iranflix: List<RealSeason>, next: List<RealSeason>): List<RealSeason> {
        if (next.isEmpty()) return iranflix
        if (iranflix.isEmpty()) return next
        val byNumber = LinkedHashMap<Int, RealSeason>()
        iranflix.forEachIndexed { index, season ->
            val number = season.number.takeIf { it > 0 } ?: (index + 1)
            byNumber[number] = season.copy(number = number)
        }
        next.forEachIndexed { index, season ->
            val number = season.number.takeIf { it > 0 } ?: (index + 1)
            val existing = byNumber[number]
            if (existing == null) {
                byNumber[number] = season.copy(number = number)
            } else {
                byNumber[number] = existing.copy(
                    episodes = mergedEpisodes(existing.episodes, season.episodes)
                )
            }
        }
        return byNumber.entries.sortedBy { it.key }.map { it.value }
    }

    fun mergedEpisodes(iranflix: List<RealEpisode>, next: List<RealEpisode>): List<RealEpisode> {
        val byNumber = LinkedHashMap<Int, RealEpisode>()
        iranflix.forEachIndexed { index, episode ->
            val number = episode.number.takeIf { it > 0 } ?: (index + 1)
            byNumber[number] = episode.copy(number = number)
        }
        next.forEachIndexed { index, episode ->
            val number = episode.number.takeIf { it > 0 } ?: (index + 1)
            val existing = byNumber[number]
            if (existing == null) {
                byNumber[number] = episode.copy(number = number)
            } else {
                byNumber[number] = existing.copy(
                    sources = dedupeSources(existing.sources + episode.sources)
                )
            }
        }
        return byNumber.entries.sortedBy { it.key }.map { it.value }
    }

    fun dedupeSources(sources: List<RealSource>): List<RealSource> {
        val seen = HashSet<String>()
        return sources.filter { source ->
            val key = source.url.substringBefore('?')
            key.isNotBlank() && seen.add(key)
        }
    }

    /** Reads the first season/episode number from a title, ignoring quality markers. */
    fun parseNumber(title: String?, fallback: Int): Int {
        val normalized = normalizeDigits(title.orEmpty())
        val cleaned = normalized
            .replace(Regex("\\b(?:2160|1440|1080|720|480|360|4k|8k)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?:زیرنویس|چسبیده|دوبله|پارسی|کیفیت|صوت|زبان|فصل|قسمت|season|episode)", RegexOption.IGNORE_CASE), " ")
        Regex("\\d+").find(cleaned)?.let { return it.value.toIntOrNull() ?: fallback }
        val ordinals = mapOf(
            "اول" to 1, "یکم" to 1, "دوم" to 2, "سوم" to 3, "چهارم" to 4,
            "پنجم" to 5, "ششم" to 6, "هفتم" to 7, "هشتم" to 8, "نهم" to 9, "دهم" to 10
        )
        ordinals.forEach { (word, value) -> if (normalized.contains(word)) return value }
        return fallback
    }

    fun normalizeDigits(value: String): String {
        val builder = StringBuilder(value.length)
        value.forEach { char ->
            val persian = "۰۱۲۳۴۵۶۷۸۹".indexOf(char)
            val arabic = "٠١٢٣٤٥٦٧٨٩".indexOf(char)
            builder.append(
                when {
                    persian >= 0 -> ('0' + persian)
                    arabic >= 0 -> ('0' + arabic)
                    else -> char
                }
            )
        }
        return builder.toString()
    }
}

package com.hnn.bisnor.util

data class ParsedMediaInfo(
    val cleanStoryline: String,
    val extractedImdb: Double? = null,
    val ageRating: String? = null,
    val isAdultOr18: Boolean = false,
    val director: String? = null,
    val actors: String? = null
)

object MediaMetadataHelper {

    private val AGE_REGEX = Regex("""(?i)(?:رده\s*سنی|محدودیت\s*سنی|مناسب\s*برای|گروه\s*سنی|درجه\s*بندی)\s*[:：\-]?\s*(\+?\s*\d{1,2}\s*\+?|PG-13|TV-MA|R|G|NC-17|بزرگسال)""")
    private val DIRECT_AGE_REGEX = Regex("""(?i)(?:\b|\+|\s)(\d{1,2})\s*\+|(?:بالای|برای)\s*(\d{1,2})\s*سال""")
    private val IMDB_REGEX = Regex("""(?i)(?:IMDb|نمره|امتیاز)\s*[:：\-]?\s*([0-9](?:\.[0-9])?)(?:\s*(?:از|/)\s*10)?""")
    private val DIRECTOR_REGEX = Regex("""(?i)(?:کارگردان|کارگردانی|سازنده)\s*[:：\-]?\s*([^\n\r]+)""")
    private val ACTORS_REGEX = Regex("""(?i)(?:بازیگران|ستارگان|با\s*حضور)\s*[:：\-]?\s*([^\n\r]+)""")

    fun parse(rawDescription: String, existingImdb: Double = 0.0, isAnimationOrAnime: Boolean = false): ParsedMediaInfo {
        if (rawDescription.isBlank()) {
            return ParsedMediaInfo(
                cleanStoryline = "",
                extractedImdb = if (existingImdb > 0.0) existingImdb else null,
                ageRating = null,
                isAdultOr18 = false
            )
        }

        var detectedImdb: Double? = if (existingImdb > 0.0) existingImdb else null
        var detectedAgeRating: String? = null
        var isAdult = false
        var director: String? = null
        var actors: String? = null

        val imdbMatch = IMDB_REGEX.find(rawDescription)
        if (imdbMatch != null) {
            val scoreStr = imdbMatch.groupValues[1]
            val score = scoreStr.toDoubleOrNull()
            if (score != null && score in 1.0..10.0) {
                if (detectedImdb == null || detectedImdb == 0.0) {
                    detectedImdb = score
                }
            }
        }

        val directorMatch = DIRECTOR_REGEX.find(rawDescription)
        if (directorMatch != null) {
            director = directorMatch.groupValues[1].trim().take(80)
        }

        val actorsMatch = ACTORS_REGEX.find(rawDescription)
        if (actorsMatch != null) {
            actors = actorsMatch.groupValues[1].trim().take(120)
        }

        val ageMatch = AGE_REGEX.find(rawDescription)
        if (ageMatch != null) {
            val matchedVal = ageMatch.groupValues[1].trim()
            detectedAgeRating = formatAgeRating(matchedVal)
        } else {
            val directMatch = DIRECT_AGE_REGEX.find(rawDescription)
            if (directMatch != null) {
                val ageNum = directMatch.groupValues[1].ifEmpty { directMatch.groupValues[2] }
                if (ageNum.isNotEmpty()) {
                    detectedAgeRating = "+$ageNum"
                }
            }
        }

        if (detectedAgeRating != null) {
            val num = detectedAgeRating.filter { it.isDigit() }.toIntOrNull()
            if (num != null && num >= 18) {
                isAdult = !isAnimationOrAnime || rawDescription.contains("بزرگسال") || rawDescription.contains("محتوای خشن")
            } else if (rawDescription.contains("بزرگسال") && !isAnimationOrAnime) {
                isAdult = true
            }
        }

        val cleaned = cleanStorylineText(rawDescription)

        return ParsedMediaInfo(
            cleanStoryline = if (cleaned.isBlank()) rawDescription.trim() else cleaned,
            extractedImdb = detectedImdb,
            ageRating = detectedAgeRating,
            isAdultOr18 = isAdult,
            director = director,
            actors = actors
        )
    }

    private fun formatAgeRating(raw: String): String {
        val trimmed = raw.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return when {
            digitsOnly.isNotEmpty() -> "+$digitsOnly"
            trimmed.contains("PG-13", ignoreCase = true) -> "+13"
            trimmed.contains("TV-MA", ignoreCase = true) -> "+18"
            trimmed.contains("NC-17", ignoreCase = true) -> "+18"
            trimmed.contains("R", ignoreCase = true) -> "+17"
            trimmed.contains("بزرگسال", ignoreCase = true) -> "+18"
            else -> trimmed
        }
    }

    private fun cleanStorylineText(text: String): String {
        val lines = text.lines()
        val filteredLines = mutableListOf<String>()

        for (line in lines) {
            val l = line.trim()
            if (l.isEmpty()) continue

            if (l.startsWith("IMDb", ignoreCase = true) ||
                l.startsWith("رده سنی") ||
                l.startsWith("محدودیت سنی") ||
                l.startsWith("کارگردان") ||
                l.startsWith("بازیگران") ||
                l.startsWith("ستارگان") ||
                l.startsWith("کیفیت") ||
                l.startsWith("فرمت") ||
                l.startsWith("مدت زمان") ||
                l.startsWith("محصول") ||
                l.startsWith("زبان") ||
                l.startsWith("کشور") ||
                l.startsWith("ژانر")
            ) {
                continue
            }

            val cleanLine = l.replace(Regex("""^(?:خلاصه داستان|درباره فیلم|داستان|توضیحات)\s*[:：\-]?\s*"""), "").trim()
            if (cleanLine.isNotEmpty()) {
                filteredLines.add(cleanLine)
            }
        }

        val result = filteredLines.joinToString("\n\n").trim()
        return if (result.isEmpty()) text.trim() else result
    }
}

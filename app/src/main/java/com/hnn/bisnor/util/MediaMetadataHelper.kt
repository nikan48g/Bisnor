package com.hnn.bisnor.util

data class ParsedMediaInfo(
    val cleanStoryline: String,
    val extractedImdb: Double? = null,
    val ageRating: String? = null,
    val isAdultOr18: Boolean = false,
    val director: String? = null,
    val actors: String? = null,
    val averageEpisodeSize: String? = null
)

object MediaMetadataHelper {

    private val AGE_REGEX = Regex("""(?i)(?:رده\s*سنی|محدودیت\s*سنی|مناسب\s*برای|گروه\s*سنی|درجه\s*بندی)\s*[:：\-]?\s*(\+?\s*\d{1,2}\s*\+?|PG-13|TV-MA|R|G|NC-17|بزرگسال)""")
    private val DIRECT_AGE_REGEX = Regex("""(?i)(?:\b|\+|\s)(\d{1,2})\s*\+|(?:بالای|برای)\s*(\d{1,2})\s*سال""")
    private val IMDB_REGEX = Regex("""(?i)(?:IMDb|نمره|امتیاز)\s*[:：\-]?\s*([0-9](?:\.[0-9])?)(?:\s*(?:از|/)\s*10)?""")
    private val DIRECTOR_REGEX = Regex("""(?i)(?:کارگردان|کارگردانی|سازنده)\s*[:：\-]?\s*([^\n\r]+)""")
    private val ACTORS_REGEX = Regex("""(?i)(?:بازیگران|ستارگان|با\s*حضور)\s*[:：\-]?\s*([^\n\r]+)""")
    private val SIZE_REGEX = Regex("""(?i)(?:حجم(?:\s*کل|\s*تقریبی|\s*فایل|\s*هر\s*قسمت|\s*قسمت‌ها)?)\s*[:：\-]?\s*([0-9\.]+|[۰-۹\.]+)\s*(گیگابایت|مگابایت|GB|MB|گیگ|مگ)""")

    fun parse(
        rawDescription: String,
        existingImdb: Double = 0.0,
        isAnimationOrAnime: Boolean = false,
        isSeries: Boolean = false
    ): ParsedMediaInfo {
        if (rawDescription.isBlank()) {
            return ParsedMediaInfo(
                cleanStoryline = "",
                extractedImdb = if (existingImdb > 0.0) existingImdb else null,
                ageRating = null,
                isAdultOr18 = false,
                averageEpisodeSize = if (isSeries) "میانگین هر قسمت: ~۳۵۰ مگابایت" else null
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

        var detectedAverageSize: String? = if (isSeries) "میانگین هر قسمت: ~۳۵۰ تا ۵۰۰ مگابایت" else null

        val sizeMatch = SIZE_REGEX.find(rawDescription)
        if (sizeMatch != null) {
            val rawNumStr = sizeMatch.groupValues[1]
                .replace("۰", "0").replace("۱", "1").replace("۲", "2")
                .replace("۳", "3").replace("۴", "4").replace("۵", "5")
                .replace("۶", "6").replace("۷", "7").replace("۸", "8")
                .replace("۹", "9")
            val unit = sizeMatch.groupValues[2].lowercase()
            val num = rawNumStr.toDoubleOrNull() ?: 0.0

            if (isSeries) {
                if (unit.contains("گیگ") || unit.contains("gb")) {
                    detectedAverageSize = if (num >= 20.0) {
                        "میانگین هر قسمت: ~۴۵۰ تا ۵۵۰ مگابایت"
                    } else if (num >= 8.0) {
                        "میانگین هر قسمت: ~۳۵۰ تا ۴۵۰ مگابایت"
                    } else {
                        "میانگین هر قسمت: ~۲۵۰ تا ۳۵۰ مگابایت"
                    }
                } else {
                    if (num in 150.0..1200.0) {
                        detectedAverageSize = "میانگین هر قسمت: ~${num.toInt()} مگابایت"
                    } else if (num > 1200.0) {
                        detectedAverageSize = "میانگین هر قسمت: ~۴۵۰ مگابایت"
                    }
                }
            } else {
                detectedAverageSize = if (unit.contains("گیگ") || unit.contains("gb")) {
                    "حجم تقریبی: ~$num گیگابایت"
                } else {
                    "حجم تقریبی: ~${num.toInt()} مگابایت"
                }
            }
        }

        val cleaned = cleanStorylineText(rawDescription)

        return ParsedMediaInfo(
            cleanStoryline = if (cleaned.isBlank()) rawDescription.trim() else cleaned,
            extractedImdb = detectedImdb,
            ageRating = detectedAgeRating,
            isAdultOr18 = isAdult,
            director = director,
            actors = actors,
            averageEpisodeSize = detectedAverageSize
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
        val metaPatterns = listOf(
            Regex("""(?i)(?:IMDb|نمره|امتیاز)\s*[:：\-]?\s*[0-9]+(?:\.[0-9]+)?(?:\s*(?:از|/)\s*10)?"""),
            Regex("""(?i)(?:رده|محدودیت|گروه|درجه)\s*سنی\s*[:：\-]?\s*(\+?\s*\d{1,2}\s*\+?|PG-13|TV-MA|R|G|NC-17|بزرگسال)"""),
            Regex("""(?i)(?:مناسب\s*برای)\s*[:：\-]?\s*[^\n\r,،]+"""),
            Regex("""(?i)(?:کارگردان|کارگردانی|سازنده)\s*[:：\-]?\s*[^\n\r]+"""),
            Regex("""(?i)(?:بازیگران|ستارگان|با\s*حضور)\s*[:：\-]?\s*[^\n\r]+"""),
            Regex("""(?i)(?:کیفیت|فرمت|مدت\s*زمان|محصول|زبان|کشور|ژانر|نویسنده|تهیه‌کننده|شبکه)\s*[:：\-]?\s*[^\n\r]+"""),
            Regex("""(?i)(?:حجم(?:\s*کل|\s*تقریبی|\s*فایل|\s*هر\s*قسمت|\s*قسمت‌ها)?)\s*[:：\-]?\s*[^\n\r]+"""),
            Regex("""(?i)(?:جوایز|رتبه|افتخارات)\s*[:：\-]?\s*[^\n\r]+""")
        )

        var processed = text
        for (pattern in metaPatterns) {
            processed = pattern.replace(processed, "")
        }

        val lines = processed.lines()
        val filteredLines = mutableListOf<String>()

        for (line in lines) {
            var l = line.trim()
            if (l.isEmpty()) continue

            // Remove label prefixes
            l = l.replace(Regex("""^(?:خلاصه داستان|درباره فیلم|داستان|توضیحات|خلاصه)\s*[:：\-]?\s*"""), "").trim()
            
            // Remove leading bullets or dashes
            l = l.replace(Regex("""^[\-–—•*|]+\s*"""), "").trim()

            if (l.isNotEmpty() && l != ":" && l != "-" && l.length > 2) {
                filteredLines.add(l)
            }
        }

        val result = filteredLines.joinToString("\n\n").trim()
        return if (result.isEmpty()) text.trim() else result
    }
}

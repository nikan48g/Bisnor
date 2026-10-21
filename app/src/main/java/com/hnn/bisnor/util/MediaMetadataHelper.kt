package com.hnn.bisnor.util

data class ParsedMediaInfo(
    val cleanStoryline: String,
    val extractedImdb: Double? = null,
    val ageRating: String? = null,
    val isAdultOr18: Boolean = false,
    val director: String? = null,
    val actors: String? = null,
    val averageEpisodeSize: String? = null,
    val language: String? = null,
    val country: String? = null,
    val quality: String? = null,
    val format: String? = null,
    val declaredSize: String? = null,
    val hasPersianSubtitle: Boolean = false
)

object MediaMetadataHelper {

    private val AGE_REGEX = Regex("""(?i)(?:رده\s*سنی|محدودیت\s*سنی|مناسب\s*برای|گروه\s*سنی|درجه\s*بندی)\s*[:：\-]?\s*(\+?\s*\d{1,2}\s*\+?|PG-13|TV-MA|R|G|NC-17|بزرگسال)""")
    private val DIRECT_AGE_REGEX = Regex("""(?i)(?:\b|\+|\s)(\d{1,2})\s*\+|(?:بالای|برای)\s*(\d{1,2})\s*سال""")
    private val IMDB_REGEX = Regex("""(?i)(?:IMDb|نمره|امتیاز)\s*[:：\-]?\s*([0-9](?:\.[0-9])?)(?:\s*(?:از|/)\s*10)?""")
    private val DIRECTOR_REGEX = Regex("""(?i)(?:کارگردان|کارگردانی|سازنده|director)\s*[:：\-]?\s*([^\n\r]+)""")
    private val ACTORS_REGEX = Regex("""(?i)(?:بازیگران|ستارگان|با\s*حضور|هنرمندان|بازیگر|actors|cast|stars)\s*[:：\-]?\s*([^\n\r]+)""")
    private val SIZE_REGEX = Regex("""(?i)(?:حجم(?:\s*کل|\s*تقریبی|\s*فایل|\s*هر\s*قسمت|\s*قسمت‌ها)?)\s*[:：\-]?\s*([0-9\.]+|[۰-۹\.]+)\s*(گیگابایت|مگابایت|GB|MB|گیگ|مگ)""")
    private val SIZE_LINE_REGEX = Regex("""(?i)(?:حجم(?:\s*کل|\s*تقریبی|\s*فایل|\s*هر\s*قسمت|\s*قسمت‌ها)?)\s*[:：\-]?\s*([^\n\r]+)""")
    private val LANGUAGE_REGEX = Regex("""(?i)(?:زبان|language)\s*[:：\-]?\s*([^\n\r]+)""")
    private val COUNTRY_REGEX = Regex("""(?i)(?:محصول\s*کشور|کشور|country)\s*[:：\-]?\s*([^\n\r]+)""")
    private val QUALITY_REGEX = Regex("""(?i)(?:کیفیت|quality)\s*[:：\-]?\s*([^\n\r]+)""")
    private val FORMAT_REGEX = Regex("""(?i)(?:فرمت|format)\s*[:：\-]?\s*([^\n\r]+)""")

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

        val language = cleanValue(LANGUAGE_REGEX.find(rawDescription)?.groupValues?.getOrNull(1))
        val country = cleanValue(COUNTRY_REGEX.find(rawDescription)?.groupValues?.getOrNull(1))
        val quality = cleanValue(QUALITY_REGEX.find(rawDescription)?.groupValues?.getOrNull(1))
        val format = cleanValue(FORMAT_REGEX.find(rawDescription)?.groupValues?.getOrNull(1))
        val declaredSize = cleanValue(SIZE_LINE_REGEX.find(rawDescription)?.groupValues?.getOrNull(1))
        val hasSubtitle = rawDescription.contains("زیرنویس")

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

        // Only report sizes the source description actually declares; never estimate per-episode size.
        var detectedAverageSize: String? = null

        val sizeMatch = SIZE_REGEX.find(rawDescription)
        if (sizeMatch != null) {
            val rawNumStr = sizeMatch.groupValues[1]
                .replace("۰", "0").replace("۱", "1").replace("۲", "2")
                .replace("۳", "3").replace("۴", "4").replace("۵", "5")
                .replace("۶", "6").replace("۷", "7").replace("۸", "8")
                .replace("۹", "9")
            val unit = sizeMatch.groupValues[2].lowercase()
            val num = rawNumStr.toDoubleOrNull() ?: 0.0

            if (!isSeries) {
                detectedAverageSize = if (unit.contains("گیگ") || unit.contains("gb")) {
                    "حجم اعلام‌شده: $num گیگابایت"
                } else {
                    "حجم اعلام‌شده: ${num.toInt()} مگابایت"
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
            averageEpisodeSize = detectedAverageSize,
            language = language,
            country = country,
            quality = quality,
            format = format,
            declaredSize = declaredSize,
            hasPersianSubtitle = hasSubtitle
        )
    }

    private fun cleanValue(raw: String?): String? {
        val value = raw?.trim()?.trim('،', ',', '.', ' ', '|')
        if (value.isNullOrBlank()) return null
        if (value.equals("null", true) || value.equals("n/a", true) || value == "نامشخص") return null
        return value.take(48)
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
            Regex("""(?i)(?:نام\s*اصلی|نام\s*فیلم|عنوان\s*فارسی|original\s*title|original\s*name)\s*[:：\-]?\s*[^\n\r]+"""),
            Regex("""(?i)(?:سال\s*انتشار|سال\s*ساخت|release\s*year|year)\s*[:：\-]?\s*[^\n\r]+"""),
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

            // Standalone technical lines are rendered as badges, not as storyline.
            if (Regex("""^(?:زیرنویس|دوبله)[^\n]{0,60}$""").containsMatchIn(l)) continue
            if (Regex("""^(?:کیفیت|فرمت|حجم|زبان|رده\s*سنی|سال\s*انتشار)\b[^\n]{0,60}$""").containsMatchIn(l)) continue

            if (l.isNotEmpty() && l != ":" && l != "-" && l.length > 2) {
                filteredLines.add(l)
            }
        }

        val result = filteredLines.joinToString("\n\n").trim()
        return if (result.isEmpty()) text.trim() else result
    }
}

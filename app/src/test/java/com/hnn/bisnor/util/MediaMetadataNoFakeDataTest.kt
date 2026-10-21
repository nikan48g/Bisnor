package com.hnn.bisnor.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "no fake data" rule: metadata parsing must not invent per-episode sizes.
 */
class MediaMetadataNoFakeDataTest {

    @Test
    fun seriesWithoutDeclaredSize_hasNoFabricatedSize() {
        val description = """
            خلاصه داستان:
            یک سریال معمایی درباره یک شهر کوچک.
            ژانر: درام، معمایی
        """.trimIndent()

        val meta = MediaMetadataHelper.parse(description, 8.1, isSeries = true)

        assertNull(meta.averageEpisodeSize)
    }

    @Test
    fun movieWithDeclaredSize_reportsItAsDeclared() {
        val description = """
            نام اصلی: Example Movie
            حجم: 1.5 گیگابایت
            خلاصه داستان:
            یک فیلم نمونه برای بررسی استخراج حجم اعلام‌شده.
        """.trimIndent()

        val meta = MediaMetadataHelper.parse(description, 7.2, isSeries = false)

        assertNotNull(meta.averageEpisodeSize)
        assertTrue(meta.averageEpisodeSize!!.contains("حجم اعلام‌شده"))
    }
}

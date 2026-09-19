package com.hnn.bisnor.util

import org.junit.Assert.*
import org.junit.Test

class MediaMetadataHelperTest {

    @Test
    fun parse_extractsDirectorAndActorsCorrectly() {
        val description = """
            یک ماجراجویی جذاب و دیدنی.
            کارگردان: کریستوفر نولان
            بازیگران: کیلیان مورفی، امیلی بلانت، مت دیمون
            امتیاز: 8.9 از 10
            رده سنی: +18
        """.trimIndent()

        val meta = MediaMetadataHelper.parse(description, 0.0)

        assertEquals("کریستوفر نولان", meta.director)
        assertEquals("کیلیان مورفی، امیلی بلانت، مت دیمون", meta.actors)
        assertEquals(8.9, meta.extractedImdb ?: 0.0, 0.01)
        assertTrue(meta.isAdultOr18)
    }

    @Test
    fun parse_detectsAnimationNonAdultCorrectly() {
        val desc = """
            داستان یک پاندای بامزه که به کونگ‌فو علاقه‌مند است.
            رده سنی: مناسب برای همه سنین (PG)
            کارگردان: مارک آزبورن
        """.trimIndent()

        val meta = MediaMetadataHelper.parse(desc, 7.6, isAnimationOrAnime = true)

        assertFalse(meta.isAdultOr18)
        assertEquals("مارک آزبورن", meta.director)
    }

    @Test
    fun parse_cleansSynopsisWithoutMetadataTags() {
        val desc = """
            خلاصه داستان:
            این فیلم روایتگر زندگی اوپنهایمر و پروژه منهتن است.
            کارگردان: کریستوفر نولان
            بازیگران: رابرت داونی جونیور
        """.trimIndent()

        val meta = MediaMetadataHelper.parse(desc, 8.5)

        assertTrue(meta.cleanStoryline.isNotEmpty())
        assertFalse(meta.cleanStoryline.contains("کارگردان:"))
        assertFalse(meta.cleanStoryline.contains("بازیگران:"))
    }
}

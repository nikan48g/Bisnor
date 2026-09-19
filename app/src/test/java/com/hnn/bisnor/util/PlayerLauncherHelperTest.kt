package com.hnn.bisnor.util

import com.hnn.bisnor.ui.player.NextEpisodeManager
import org.junit.Assert.*
import org.junit.Test

class PlayerLauncherHelperTest {

    @Test
    fun urlEncoding_handlesSpacesCorrectly() {
        val rawUrl = "http://dl.bisnor.com/movies/Inception 2010 1080p BluRay.mkv"
        val cleanUrl = rawUrl.trim().replace(" ", "%20")

        assertEquals("http://dl.bisnor.com/movies/Inception%202010%201080p%20BluRay.mkv", cleanUrl)
    }

    @Test
    fun nextEpisodeManager_extractsEpisodeNumberFromPersianAndEnglishStrings() {
        assertEquals(5, NextEpisodeManager.extractEpisodeNumber("قسمت ۵ - کیفیت اصلی"))
        assertEquals(12, NextEpisodeManager.extractEpisodeNumber("Breaking Bad S01 Episode 12"))
        assertEquals(1169, NextEpisodeManager.extractEpisodeNumber("One Piece - Ep 1169 [1080p]"))
        assertEquals(3, NextEpisodeManager.extractEpisodeNumber("فصل ۱ قسمت ۳"))
    }
}

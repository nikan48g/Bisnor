package com.hnn.bisnor.data.remote

import org.junit.Assert.*
import org.junit.Test

class SupabaseCompressTest {

    @Test
    fun compressAndDecompress_preservesPersianTextAndJsonStructure() {
        val originalJson = """
            [
              {
                "id": 4821,
                "title": "اوپنهایمر (Oppenheimer)",
                "note": "فیلم عالی با بازی کیلیان مورفی",
                "is_watched": true
              }
            ]
        """.trimIndent()

        val compressed = SupabaseManager.compressString(originalJson)
        assertTrue("Compressed string should not be empty", compressed.isNotEmpty())
        assertNotEquals(originalJson, compressed)

        val decompressed = SupabaseManager.decompressString(compressed)
        assertEquals(originalJson, decompressed)
    }

    @Test
    fun compressAndDecompress_handlesEmptyOrBlankInputs() {
        assertEquals("", SupabaseManager.compressString(""))
        assertEquals("", SupabaseManager.compressString("   "))
        assertEquals("", SupabaseManager.compressString("[]"))

        assertEquals("", SupabaseManager.decompressString(""))
        assertEquals("", SupabaseManager.decompressString("   "))
    }
}

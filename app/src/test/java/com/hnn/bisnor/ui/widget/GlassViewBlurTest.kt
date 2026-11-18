package com.hnn.bisnor.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassViewBlurTest {
    @Test
    fun uniformImageStaysUniform() {
        val px = IntArray(12 * 5) { 0xFF336699.toInt() }
        GlassView.boxBlur(px, IntArray(px.size), 12, 5, 3)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test
    fun brightPixelSpreadsAndKeepsBounds() {
        val w = 9; val h = 9
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        px[4 * w + 4] = 0xFFFFFFFF.toInt()
        GlassView.boxBlur(px, IntArray(px.size), w, h, 1)
        val centre = px[4 * w + 4] and 0xFF
        val neighbour = px[4 * w + 5] and 0xFF
        val far = px[0] and 0xFF
        assertEquals(255 / 9, centre)          // 3x3 box: energy split nine ways
        assertEquals(centre, neighbour)
        assertEquals(0, far)
        assertTrue(px.all { it ushr 24 == 0xFF }) // alpha untouched
    }

    @Test
    fun luminanceSpansBlackToWhite() {
        assertEquals(0f, GlassView.averageLuminance(IntArray(8) { 0xFF000000.toInt() }), 0.001f)
        assertEquals(1f, GlassView.averageLuminance(IntArray(8) { 0xFFFFFFFF.toInt() }), 0.001f)
    }
}

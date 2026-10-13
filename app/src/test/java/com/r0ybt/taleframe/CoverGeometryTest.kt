package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.coverBounds
import org.junit.Assert.*
import org.junit.Test

class CoverGeometryTest {
    @Test fun fitKeepsLandscapeSquareAndPortraitImagesCompleteAndCentered() {
        for ((width, height) in listOf(800f to 400f, 400f to 400f, 400f to 900f)) {
            val bounds = coverBounds(width, height, 200f, 300f)
            assertTrue(bounds.left >= 0f && bounds.top >= 0f)
            assertTrue(bounds.width <= 200f && bounds.height <= 300f)
            assertEquals(width / height, bounds.width / bounds.height, .0001f)
            assertEquals(100f, bounds.left + bounds.width / 2, .0001f)
            assertEquals(150f, bounds.top + bounds.height / 2, .0001f)
        }
    }
    @Test fun adjustedFramingIsIdenticalAtDifferentPreviewAndLibrarySizes() {
        val small = coverBounds(800f, 400f, 120f, 180f, 2f, .2f, -.3f)
        val large = coverBounds(800f, 400f, 240f, 360f, 2f, .2f, -.3f)
        assertEquals(small.left * 2, large.left, .0001f)
        assertEquals(small.top * 2, large.top, .0001f)
        assertEquals(small.width * 2, large.width, .0001f)
        assertEquals(small.height * 2, large.height, .0001f)
        assertEquals(coverBounds(800f, 400f, 120f, 180f), coverBounds(800f, 400f, 120f, 180f, 1f, 0f, 0f))
    }
}

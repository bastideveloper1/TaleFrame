package com.r0ybt.taleframe

import androidx.compose.ui.geometry.Offset
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.*
import org.junit.Assert.*
import org.junit.Test

class MediaFramingTest {
    private fun anchor(b: ElementBounds, rotation: Float, corner: ResizeCorner): Offset =
        resizeCornerHandle(b, rotation, corner, 10000f, 10000f, -10000f)

    @Test fun fourCornersPreserveOppositeAnchorAndAspectIncludingRotation() {
        for (type in listOf("image", "gif", "video")) for (rotation in listOf(0f, 35f, -90f)) {
            val e = Element(1, 1, "image", "", image = "media", width = .4f, height = .3f,
                x = .4f, y = .4f, freePosition = true, rotation = rotation,
                media = MediaOptions(type), allowOverflow = true)
            val b = elementBounds(e, 400f, 800f, 0f, 0f)
            for (corner in ResizeCorner.entries) {
                val opposite = ResizeCorner.entries.single { it.sx == -corner.sx && it.sy == -corner.sy }
                val resized = resizeElementCorner(e, b, 400f, 800f, Offset(corner.sx * 80f, corner.sy * 90f), corner)
                val after = elementBounds(resized, 400f, 800f, 0f, 0f)
                assertEquals(b.width / b.height, after.width / after.height, .0001f)
                assertEquals(anchor(b, rotation, opposite).x, anchor(after, rotation, opposite).x, .001f)
                assertEquals(anchor(b, rotation, opposite).y, anchor(after, rotation, opposite).y, .001f)
            }
        }
    }
    @Test fun boundedVideoStopsAtStageWhileOverflowVideoCanGrowAndMoveBeyondIt() {
        val e = Element(1, 1, "image", "", image = "video", width = .4f, height = .4f, media = MediaOptions("video"))
        assertFalse(e.supportsFreePlacement)
        val b = elementBounds(e, 300f, 400f, 0f, 0f)
        val bounded = resizeElementCorner(e, b, 300f, 400f, Offset(2000f, 2000f), ResizeCorner.BottomRight)
        val inside = elementBounds(bounded, 300f, 400f, 0f, 0f)
        assertTrue(inside.left >= 0 && inside.top >= 0 && inside.left + inside.width <= 300.001f && inside.top + inside.height <= 400.001f)
        val enabled = setElementOverflow(e, true)
        val big = resizeElementCorner(enabled, b, 300f, 400f, Offset(2000f, 2000f), ResizeCorner.BottomRight)
        assertTrue(big.width > 1f && big.height > 1f)
        val moved = moveFreeElement(big, elementBounds(big, 300f, 400f, 0f, 0f), 300f, 400f, Offset(-1000f, -1000f))
        assertTrue(moved.x < 0f && moved.y < 0f)
        val recovered = setElementOverflow(moved, false)
        val visible = elementBounds(recovered, 300f, 400f, 0f, 0f)
        assertTrue(visible.left >= 0 && visible.top >= 0 && recovered.width <= 1 && recovered.height <= 1)
        assertEquals(big.width / big.height, recovered.width / recovered.height, .0001f)
    }
    @Test fun lockedElementsStayUnchangedAndHandlesRemainReachableForOversizedElements() {
        val e = Element(1, 1, "image", "", width = 2f, height = 2f, x = -.5f, y = -.5f, freePosition = true, locked = true)
        val b = elementBounds(e, 300f, 400f, 0f, 0f)
        for (corner in ResizeCorner.entries) {
            assertEquals(e, resizeElementCorner(e, b, 300f, 400f, Offset(100f, 200f), corner))
            val handle = resizeCornerHandle(b, 0f, corner, 300f, 400f, 12f)
            assertTrue(handle.x in 12f..288f && handle.y in 12f..388f)
        }
    }
    @Test fun gifUsesFullLogicalFrameWithoutContainerLetterboxingOrAlphaCropping() {
        for (aspect in listOf(2f, 1f, .75f, .25f)) {
            val size = mediaElementSize(aspect)
            assertEquals(aspect, size.first * 300f / (size.second * 400f), .0001f)
            assertTrue(size.first <= .4f && size.second <= .40001f)
            val b = ElementBounds(10f, 20f, 120f, 160f)
            val fitted = fittedMediaBounds(b, aspect)
            assertEquals(aspect, fitted.width / fitted.height, .0001f)
            assertEquals(b.left + b.width / 2, fitted.left + fitted.width / 2, .0001f)
            assertEquals(b.top + b.height / 2, fitted.top + fitted.height / 2, .0001f)
        }
        val b = ElementBounds(10f, 20f, 120f, 160f)
        assertEquals(b, fittedMediaBounds(b, null))
    }
    @Test fun movingLegacyLetterboxedMediaKeepsItsVisibleFrameUnderTheFinger() {
        val e = Element(1, 1, "image", "", width = .4f, height = .4f, x = .2f, y = .3f, media = MediaOptions("gif"))
        val stored = elementBounds(e, 300f, 400f, 0f, 0f)
        val visible = fittedMediaBounds(stored, 2f)
        val moved = moveFreeElement(e, stored, 300f, 400f, Offset(35f, -20f))
        val after = fittedMediaBounds(elementBounds(moved, 300f, 400f, 0f, 0f), 2f)
        assertEquals(visible.left + 35f, after.left, .001f)
        assertEquals(visible.top - 20f, after.top, .001f)
        assertEquals(e.width, moved.width, 0f); assertEquals(e.height, moved.height, 0f)
    }
    @Test fun legacyEligibilityAndPropertySizingKeepMediaProportional() {
        val e = Element(1, 1, "image", "", width = .4f, height = .4f)
        assertTrue(e.supportsFreePlacement)
        assertTrue(e.copy(media = MediaOptions("gif")).supportsFreePlacement)
        assertFalse(e.copy(media = MediaOptions("video")).supportsFreePlacement)
        assertFalse(e.copy(allowOverflow = false).supportsFreePlacement)
        val resized = resizeMediaElementTo(e, width = 2f, aspect = 2f)
        assertEquals(2f, resized.width * .75f / resized.height, .0001f)
    }
}

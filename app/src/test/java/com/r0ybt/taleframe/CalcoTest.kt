package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class CalcoTest {
    @Test fun quickTogglePreservesManualSourceAndOpacityWhileHidingReference() {
        val manual = CalcoConfig(CalcoMode.MANUAL, manualId = 70, opacity = .6f, configured = true)
        val hidden = toggleCalco(manual)
        assertFalse(hidden.enabled)
        assertNull(calcoReference(hidden, b, listOf(a, b)))
        assertEquals(manual, toggleCalco(hidden))
        assertEquals(a, calcoReference(toggleCalco(hidden), b, listOf(a, b)))
    }
    @Test fun firstQuickActivationUsesPreviousAndKeepsOpacity() {
        val active = toggleCalco(CalcoConfig(opacity = .4f))
        assertEquals(CalcoMode.PREVIOUS, active.mode)
        assertTrue(active.enabled && active.configured)
        assertEquals(.4f, active.opacity, 0f)
        assertEquals(a, calcoReference(active, b, listOf(a, b)))
    }
    private val automatic = CalcoConfig(CalcoMode.PREVIOUS, configured = true)
    private val a = Slide(70, 1, "A", order = 0, autoTargetId = 30)
    private val b = Slide(10, 1, "B", order = 1)
    private val c = Slide(30, 1, "C", order = 2)
    @Test fun previousFollowsAlbumOrderRatherThanIdsOrNarrativeConnections() {
        assertEquals(b, calcoReference(automatic, c, listOf(c, a, b)))
        assertEquals(a, calcoReference(automatic, b, listOf(c, a, b)))
        assertNull(calcoReference(automatic, a, listOf(c, a, b)))
        assertEquals(CalcoMode.PREVIOUS, automatic.mode)
    }
    @Test fun reorderedDuplicatedAndDeletedSlidesResolveWithoutChangingConfiguration() {
        assertEquals(a.copy(order = 1), calcoReference(automatic, c, listOf(b.copy(order = 0), a.copy(order = 1), c)))
        val duplicate = b.copy(id = 90, name = "B copia", order = 2)
        assertEquals(duplicate, calcoReference(automatic, c.copy(order = 3), listOf(a, b, duplicate, c.copy(order = 3))))
        assertEquals(a, calcoReference(automatic, c, listOf(a, c)))
        assertNull(calcoReference(automatic, c, listOf(c)))
    }
    @Test fun manualSelectionAndNoneRemainIndependentOfAlbumOrder() {
        val manual = automatic.copy(mode = CalcoMode.MANUAL, manualId = a.id)
        assertEquals(a, calcoReference(manual, c, listOf(a, b, c)))
        assertNull(calcoReference(manual, a, listOf(a, b, c)))
        assertNull(calcoReference(manual, c, listOf(b, c)))
        assertNull(calcoReference(manual.copy(mode = CalcoMode.NONE), c, listOf(a, b, c)))
    }
    @Test fun referencesNeverCrossProjectsOrUseAMissingCurrentSlide() {
        val other = Slide(100, 2, "Otro proyecto", order = 0)
        assertNull(calcoReference(automatic, a, listOf(other, a, b)))
        assertNull(calcoReference(automatic.copy(mode = CalcoMode.MANUAL, manualId = other.id), b, listOf(other, a, b)))
        assertNull(calcoReference(automatic, c, listOf(a, b)))
        assertNull(calcoReference(automatic, null, listOf(a, b)))
    }
}

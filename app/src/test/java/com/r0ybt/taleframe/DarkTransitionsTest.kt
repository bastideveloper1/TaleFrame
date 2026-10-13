package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class DarkTransitionsTest {
    @Test fun destinationIsHiddenUntilFullBlackAndBothEndpointsAreClear() {
        assertEquals(0f, darkCoverage(0f), 0f)
        assertEquals(1f, darkCoverage(.5f), 0f)
        assertEquals(0f, darkCoverage(1f), 0f)
        for (step in 0..1000) {
            val progress = step / 1000f
            assertEquals(progress >= .5f, darkIncomingVisible(progress))
            assertEquals(darkCoverage(progress), darkCoverage(1f - progress), .000001f)
            assertTrue(darkCoverage(progress) in 0f..1f)
        }
    }
    @Test fun darkEffectsUseExistingPriorityAndDurationRules() {
        for (type in listOf("black", "shadows")) {
            val effect = Transition(type, 701)
            assertTrue(effect.darkEffect())
            assertEquals(701, effect.duration)
            val project = ProjectPlayback(effect)
            val slide = Slide(1, 1, "A", useDefaultTransition = true)
            assertEquals(effect, effectiveTransition(project, slide))
            val exception = slide.copy(transition = Transition("none"), useDefaultTransition = false)
            assertEquals(0, effectiveTransition(project, exception).duration)
            val action = Element(1, 1, "button", "Ir", transition = effect)
            assertEquals(effect, effectiveTransition(project, exception, action))
            assertEquals(200, effect.copy(millis = -1).duration)
            assertEquals(1000, effect.copy(millis = 9999).duration)
        }
    }
    @Test fun oldEffectsRemainSupportedWithoutDarkOverlay() {
        for (type in listOf("none", "fade", "left", "right")) {
            assertTrue(type in transitionTypes)
            assertFalse(Transition(type).darkEffect())
        }
    }
}

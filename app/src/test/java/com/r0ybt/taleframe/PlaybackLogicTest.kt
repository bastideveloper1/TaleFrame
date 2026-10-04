package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackLogicTest {
    @Test fun buttonAndTimerCanConsumeOnlyOneExitAndOldTimersCannotExitNewVisits() {
        val gate = ExitGate()
        val oldTimer = gate.enter()
        assertTrue(gate.take(oldTimer)) // button wins
        assertFalse(gate.take(oldTimer)) // timer and second tap lose
        val newTimer = gate.enter()
        assertFalse(gate.take(oldTimer))
        assertTrue(gate.take(newTimer))
        assertFalse(gate.take(newTimer))
    }
    @Test fun slideshowLoopsOrHoldsFinalFrameIncludingSingleImage() {
        assertEquals(listOf(0,1,2,0,1,2), (0L..5).map { frameIndex(it,3,true) })
        assertEquals(listOf(0,1,2,2,2,2), (0L..5).map { frameIndex(it,3,false) })
        assertEquals(0,frameIndex(100,1,true)); assertEquals(0,frameIndex(100,0,false))
    }
    @Test fun transitionDurationIsBoundedAndNoneIsImmediate() {
        assertEquals(0,Transition("none",1000).duration)
        assertEquals(200,Transition("left",-50).duration)
        assertEquals(1000,Transition("right",9000).duration)
        assertEquals(700,Transition("fade",700).duration)
    }
}

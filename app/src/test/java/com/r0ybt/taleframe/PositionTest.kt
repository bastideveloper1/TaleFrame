package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.boundedPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionTest {
    @Test fun positionsStayInsideStageAndRejectNonFiniteValues() {
        assertEquals(0f,boundedPosition(-.2f),0f)
        assertEquals(1f,boundedPosition(1.2f),0f)
        assertEquals(.4f,boundedPosition(.4f),0f)
        assertEquals(0f,boundedPosition(Float.NaN),0f)
        assertEquals(0f,boundedPosition(Float.POSITIVE_INFINITY),0f)
    }
}

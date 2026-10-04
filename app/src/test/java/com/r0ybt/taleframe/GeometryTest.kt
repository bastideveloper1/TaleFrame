package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.Element
import com.r0ybt.taleframe.ui.*
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun legacyTravelCoordinatesAndExplicitSizesUseSameStage() {
        val old=Element(1,1,"text","Old",x=.5f,y=.25f)
        assertEquals(ElementBounds(150f,175f,100f,100f),elementBounds(old,400f,800f,100f,100f))
        val resized=old.copy(width=.5f,height=.25f)
        assertEquals(ElementBounds(100f,150f,200f,200f),elementBounds(resized,400f,800f,100f,100f))
    }
    @Test fun smallElementsHaveExpandedAndRotatedHitboxes() {
        val b=ElementBounds(100f,100f,10f,10f)
        assertTrue(b.contains(125f,105f,0f,48f))
        assertFalse(b.contains(130f,105f,0f,48f))
        val rotated=ElementBounds(100f,100f,100f,20f)
        assertTrue(rotated.contains(150f,150f,90f,20f))
        assertFalse(rotated.contains(190f,110f,90f,20f))
    }
}

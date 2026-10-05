package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.Element
import androidx.compose.ui.geometry.Offset
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
    @Test fun freeFramingCrossesFullSizeWithoutLosingOriginAndDragFollowsFinger() {
        val old=Element(1,1,"image","",width=.5f,height=.25f,x=.5f,y=.25f)
        val b=elementBounds(old,400f,800f,0f,0f)
        val resized=resizeFreeElement(old,b,400f,800f,Offset(800f,800f))
        assertTrue(resized.width>1f);assertTrue(resized.height>1f)
        val big=elementBounds(resized,400f,800f,0f,0f)
        assertEquals(b.left,big.left,0f);assertEquals(b.top,big.top,0f)
        assertEquals(b.width/b.height,big.width/big.height,.0001f)
        val moved=moveFreeElement(resized,big,400f,800f,Offset(-300f,-500f))
        assertTrue(moved.x<0f);assertTrue(moved.y<0f)
        val outside=elementBounds(moved,400f,800f,0f,0f)
        assertEquals(big.left-300f,outside.left,.001f);assertEquals(big.top-500f,outside.top,.001f)
        val exact=resizeFreeElementTo(moved,1f,1f)
        assertEquals(outside.left,elementBounds(exact,400f,800f,0f,0f).left,.001f)
        val returned=moveFreeElement(exact,elementBounds(exact,400f,800f,0f,0f),400f,800f,Offset(300f,500f))
        assertEquals(b.left,elementBounds(returned,400f,800f,0f,0f).left,.001f)
    }
    @Test fun oversizedResizeHandleRemainsOnStage() {
        assertEquals(Offset(388f,788f),resizeHandle(ElementBounds(-100f,-200f,1200f,1600f),400f,800f,12f))
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

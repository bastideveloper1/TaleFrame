package com.r0ybt.taleframe.ui

import com.r0ybt.taleframe.data.*
import androidx.compose.ui.geometry.Offset
import kotlin.math.*

data class ElementBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun intersectsStage(stageWidth: Float, stageHeight: Float): Boolean =
        left < stageWidth && top < stageHeight && left + width > 0f && top + height > 0f
    fun contains(px: Float, py: Float, rotation: Float, minimumTouchSize: Float): Boolean {
        val radians = -rotation * PI.toFloat() / 180f
        val dx = px - (left + width / 2); val dy = py - (top + height / 2)
        val localX = dx * cos(radians) - dy * sin(radians)
        val localY = dx * sin(radians) + dy * cos(radians)
        return abs(localX) <= max(width, minimumTouchSize) / 2 && abs(localY) <= max(height, minimumTouchSize) / 2
    }
}
fun elementBounds(e: Element, stageWidth: Float, stageHeight: Float, measuredWidth: Float, measuredHeight: Float): ElementBounds {
    val width = if (e.width > 0) stageWidth * e.width else measuredWidth
    val height = if (e.height > 0) stageHeight * e.height else measuredHeight
    return ElementBounds(e.x * (if (e.freePosition) stageWidth else (stageWidth - width).coerceAtLeast(0f)),
        e.y * (if (e.freePosition) stageHeight else (stageHeight - height).coerceAtLeast(0f)), width, height)
}

/** Convert legacy travel coordinates only when framing, keeping the visible origin unchanged. */
fun moveFreeElement(e: Element, b: ElementBounds, stageWidth: Float, stageHeight: Float, delta: Offset): Element =
    e.copy(freePosition=true, x=(b.left+delta.x)/stageWidth, y=(b.top+delta.y)/stageHeight)
fun resizeFreeElement(e: Element, b: ElementBounds, stageWidth: Float, stageHeight: Float, delta: Offset): Element {
    val factor=(1f+(delta.x*b.width+delta.y*b.height)/(b.width*b.width+b.height*b.height))
        .coerceAtLeast(max(.05f*stageWidth/b.width, .04f*stageHeight/b.height))
    return e.copy(freePosition=true, x=b.left/stageWidth, y=b.top/stageHeight,
        width=b.width*factor/stageWidth, height=b.height*factor/stageHeight)
}
fun resizeHandle(b: ElementBounds, stageWidth: Float, stageHeight: Float, inset: Float): Offset =
    Offset((b.left+b.width).coerceIn(inset, max(inset,stageWidth-inset)),
        (b.top+b.height).coerceIn(inset, max(inset,stageHeight-inset)))
/** Property resizing shares the same anchor as gestures, including crossing 100%. */
fun resizeFreeElementTo(e: Element, width: Float, height: Float): Element {
    val b=elementBounds(e,1f,1f,e.width,e.height)
    return e.copy(freePosition=true,x=b.left,y=b.top,width=width,height=height)
}

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

/** Corners refer to the element's local rectangle, before its rotation. */
enum class ResizeCorner(val sx: Float, val sy: Float) {
    TopLeft(-1f, -1f), TopRight(1f, -1f), BottomLeft(-1f, 1f), BottomRight(1f, 1f)
}
private fun Offset.rotated(degrees: Float): Offset {
    val angle = degrees * PI.toFloat() / 180f
    return Offset(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))
}
fun resizeCornerHandle(b: ElementBounds, rotation: Float, corner: ResizeCorner,
    stageWidth: Float, stageHeight: Float, inset: Float): Offset {
    val point = Offset(b.left + b.width / 2, b.top + b.height / 2) +
        Offset(corner.sx * b.width / 2, corner.sy * b.height / 2).rotated(rotation)
    return Offset(point.x.coerceIn(inset, max(inset, stageWidth - inset)),
        point.y.coerceIn(inset, max(inset, stageHeight - inset)))
}
fun resizeElementCorner(e: Element, b: ElementBounds, stageWidth: Float, stageHeight: Float,
    delta: Offset, corner: ResizeCorner): Element {
    if (e.locked || b.width <= 0f || b.height <= 0f || stageWidth <= 0f || stageHeight <= 0f) return e
    val localDelta = delta.rotated(-e.rotation)
    var factor = (1f + (corner.sx * localDelta.x * b.width + corner.sy * localDelta.y * b.height) /
        (b.width * b.width + b.height * b.height)).coerceAtLeast(max(.05f * stageWidth / b.width, .04f * stageHeight / b.height))
    val anchor = Offset(b.left + b.width / 2, b.top + b.height / 2) -
        Offset(corner.sx * b.width / 2, corner.sy * b.height / 2).rotated(e.rotation)
    if (!e.permitsOverflow) {
        // Every new corner is anchor + factor * vector. Bound the factor, keeping the anchor fixed.
        if (anchor.x in 0f..stageWidth && anchor.y in 0f..stageHeight) {
            var limit = Float.POSITIVE_INFINITY
            for (vector in listOf(Offset(corner.sx * b.width, 0f), Offset(0f, corner.sy * b.height),
                Offset(corner.sx * b.width, corner.sy * b.height)).map { it.rotated(e.rotation) }) {
                if (vector.x > 0f) limit = min(limit, (stageWidth - anchor.x) / vector.x)
                if (vector.x < 0f) limit = min(limit, -anchor.x / vector.x)
                if (vector.y > 0f) limit = min(limit, (stageHeight - anchor.y) / vector.y)
                if (vector.y < 0f) limit = min(limit, -anchor.y / vector.y)
            }
            factor = min(factor, limit.coerceAtLeast(.001f))
        }
    }
    val width = b.width * factor
    val height = b.height * factor
    val center = anchor + Offset(corner.sx * width / 2, corner.sy * height / 2).rotated(e.rotation)
    val changed = e.copy(freePosition = true, x = (center.x - width / 2) / stageWidth,
        y = (center.y - height / 2) / stageHeight, width = width / stageWidth, height = height / stageHeight)
    return if (e.permitsOverflow) changed else setElementOverflow(changed, false)
}

/** Keep the current origin when possible; bounded framing recovers oversized/offstage elements. */
fun setElementOverflow(e: Element, enabled: Boolean): Element {
    if (enabled) return e.copy(allowOverflow = true)
    val bounds = elementBounds(e, 1f, 1f, e.width, e.height)
    val factor = minOf(1f, 1f / bounds.width.coerceAtLeast(.001f), 1f / bounds.height.coerceAtLeast(.001f))
    val width = bounds.width * factor; val height = bounds.height * factor
    return e.copy(allowOverflow = false, freePosition = false, width = width, height = height,
        x = if (width < 1f) (bounds.left / (1f - width)).coerceIn(0f, 1f) else 0f,
        y = if (height < 1f) (bounds.top / (1f - height)).coerceIn(0f, 1f) else 0f)
}

/** Fit the entire logical frame. Transparent pixels remain part of the image. */
fun fittedMediaBounds(b: ElementBounds, aspect: Float?): ElementBounds {
    if (aspect == null || !aspect.isFinite() || aspect <= 0f) return b
    val width = min(b.width, b.height * aspect)
    val height = width / aspect
    return ElementBounds(b.left + (b.width - width) / 2, b.top + (b.height - height) / 2, width, height)
}

fun resizeMediaElementTo(e: Element, width: Float? = null, height: Float? = null, aspect: Float? = null): Element {
    val ratio = aspect ?: if (e.width > 0f && e.height > 0f) e.width * .75f / e.height else .75f
    val newWidth = width ?: requireNotNull(height) * ratio / .75f
    val newHeight = height ?: newWidth * .75f / ratio
    val resized = resizeFreeElementTo(e, newWidth, newHeight)
    return if (e.permitsOverflow) resized else setElementOverflow(resized, false)
}

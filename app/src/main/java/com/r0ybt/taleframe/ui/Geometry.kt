package com.r0ybt.taleframe.ui

import com.r0ybt.taleframe.data.Element
import kotlin.math.*

data class ElementBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
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
    return ElementBounds(e.x * (stageWidth - width).coerceAtLeast(0f), e.y * (stageHeight - height).coerceAtLeast(0f), width, height)
}

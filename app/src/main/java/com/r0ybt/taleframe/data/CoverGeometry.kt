package com.r0ybt.taleframe.data

/** Position is stored as a fraction of the frame, so preview and library use the same framing. */
data class CoverBounds(val left: Float, val top: Float, val width: Float, val height: Float)

fun coverBounds(imageWidth: Float, imageHeight: Float, frameWidth: Float, frameHeight: Float,
    scale: Float = 1f, x: Float = 0f, y: Float = 0f): CoverBounds {
    val fit = minOf(frameWidth / imageWidth, frameHeight / imageHeight) * scale
    val width = imageWidth * fit
    val height = imageHeight * fit
    return CoverBounds((frameWidth - width) / 2 + x * frameWidth,
        (frameHeight - height) / 2 + y * frameHeight, width, height)
}

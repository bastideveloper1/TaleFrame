package com.r0ybt.taleframe.data

data class Project(val id: Long, val name: String)
data class Slide(
    val id: Long, val projectId: Long, val name: String, val color: Int = -1,
    val image: String? = null, val order: Int = 0,
    val backgroundMode: String = "fill", val backgroundScale: Float = 1f,
    val backgroundX: Float = 0f, val backgroundY: Float = 0f,
    val backgroundLocked: Boolean = false,
    val media: MediaOptions = MediaOptions(), val audio: String? = null,
    val audioLoop: Boolean = true, val audioVolume: Float = 1f, val audioRevision: Long = 0,
    val autoEnabled: Boolean = false, val autoSeconds: Float = 5f,
    val autoTargetId: Long? = null, val transition: Transition = Transition()
)

// x/y remain normalized over available travel, preserving MVP compositions.
// A zero width/height retains legacy automatic text sizing until explicitly resized.
data class Element(
    val id: Long, val slideId: Long, val kind: String, val text: String,
    val x: Float = .1f, val y: Float = .1f, val textColor: Int = -16777216,
    val backgroundColor: Int = -1, val targetId: Long? = null,
    val image: String? = null, val width: Float = 0f, val height: Float = 0f,
    val rotation: Float = 0f, val flipped: Boolean = false,
    val opacity: Float = 1f, val locked: Boolean = false, val layer: Int = 0,
    val media: MediaOptions = MediaOptions(), val transition: Transition = Transition()
)
data class Story(val projects: List<Project> = emptyList(), val slides: List<Slide> = emptyList(), val elements: List<Element> = emptyList())
fun boundedPosition(value: Float): Float = boundedValue(value, 0f, 1f, 0f)
fun boundedValue(value: Float, min: Float, max: Float, fallback: Float): Float =
    if (value.isFinite()) value.coerceIn(min, max) else fallback

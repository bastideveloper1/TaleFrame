package com.r0ybt.taleframe.data

data class Project(val id: Long, val name: String)
data class Slide(val id: Long, val projectId: Long, val name: String, val color: Int = -1, val image: String? = null)
// Coordinates describe the available travel area, independent of display density.
data class Element(val id: Long, val slideId: Long, val kind: String, val text: String,
    val x: Float = .1f, val y: Float = .1f, val textColor: Int = -16777216,
    val backgroundColor: Int = -1, val targetId: Long? = null)
data class Story(val projects: List<Project> = emptyList(), val slides: List<Slide> = emptyList(), val elements: List<Element> = emptyList())
fun boundedPosition(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

package com.r0ybt.taleframe.data

enum class ScriptInsertionMode { END, START, AFTER, BETWEEN }
data class ScriptInsertionPoint(
    val mode: ScriptInsertionMode = ScriptInsertionMode.END,
    val afterId: Long? = null,
    val beforeId: Long? = null
)

/** Resolve stable IDs against the current Album order, never against navigation destinations. */
fun scriptInsertionIndex(orderedIds: List<Long>, point: ScriptInsertionPoint): Int = when (point.mode) {
    ScriptInsertionMode.END -> orderedIds.size
    ScriptInsertionMode.START -> 0
    ScriptInsertionMode.AFTER, ScriptInsertionMode.BETWEEN -> {
        val anchor = orderedIds.indexOf(point.afterId)
        require(anchor >= 0) { "La lámina elegida ya no está disponible en este proyecto. Elige otra ubicación." }
        if (point.mode == ScriptInsertionMode.BETWEEN) require(orderedIds.getOrNull(anchor + 1) == point.beforeId && point.beforeId != null) {
            "Las láminas elegidas ya no son consecutivas. Elige otra ubicación."
        }
        anchor + 1
    }
}

/** Shift only the suffix, preserving sparse orders and ties in older projects. */
fun scriptInsertionOrders(ordered: List<Pair<Long, Int>>, index: Int, newId: Long): List<Pair<Long, Int>> {
    require(index in 0..ordered.size)
    val suffix = ordered.drop(index)
    require(ordered.all { it.second < Int.MAX_VALUE }) { "El orden del proyecto no permite añadir otra lámina" }
    val order = suffix.firstOrNull()?.second ?: ((ordered.lastOrNull()?.second ?: -1) + 1)
    return suffix.map { it.first to it.second + 1 } + (newId to order)
}

fun scriptInsertionDescription(slides: List<Slide>, point: ScriptInsertionPoint): String {
    val index = runCatching { scriptInsertionIndex(slides.map { it.id }, point) }.getOrNull()
        ?: return "Ubicación no disponible; elige otra."
    if (point.mode == ScriptInsertionMode.END) return "Al final del proyecto"
    if (index == 0) return slides.firstOrNull()?.let { "Al principio, antes de «${it.name}»" } ?: "Al principio del proyecto"
    val previous = slides[index - 1]
    return slides.getOrNull(index)?.let { "Entre «${previous.name}» y «${it.name}»" } ?: "Después de «${previous.name}», al final"
}

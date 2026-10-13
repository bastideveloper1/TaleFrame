package com.r0ybt.taleframe.data

data class ProjectPlayback(val transition: Transition = Transition(), val sequenceEnabled: Boolean = false,
    val seconds: Float = 5f, val loop: Boolean = false)

/** An action exception wins, then the outgoing slide exception, then the project default. */
fun effectiveTransition(project: ProjectPlayback, slide: Slide, action: Element? = null): Transition =
    if (action != null && !action.useDefaultTransition) action.transition
    else if (!slide.useDefaultTransition) slide.transition else project.transition

fun slideSeconds(project: ProjectPlayback, slide: Slide): Float =
    boundedValue(if (slide.useDefaultSequenceTime) project.seconds else slide.autoSeconds, .2f, 3600f, 5f)

fun nextSequenceSlide(current: Slide, slides: List<Slide>, loop: Boolean, blocked: Set<Long> = emptySet()): Slide? {
    val album = slides.filter { it.projectId == current.projectId && it.id !in blocked }
        .sortedWith(compareBy<Slide> { it.order }.thenBy { it.id })
    val index = album.indexOfFirst { it.id == current.id }
    if (index < 0) return null
    return album.getOrNull(index + 1) ?: if (loop) album.firstOrNull() else null
}
fun frameSeconds(options: MediaOptions, index: Int): Float =
    boundedValue(options.frameSeconds.getOrNull(index) ?: options.seconds, .2f, 3600f, 3f)

fun reorderMediaFrames(options: MediaOptions, from: Int, to: Int): MediaOptions {
    val frames = options.frames.toMutableList().apply { add(to, removeAt(from)) }
    val seconds = options.frames.indices.map { frameSeconds(options, it) }.toMutableList().apply { add(to, removeAt(from)) }
    return options.copy(frames = frames, frameSeconds = seconds)
}
fun removeMediaFrame(options: MediaOptions, index: Int): MediaOptions = options.copy(
    frames = options.frames.filterIndexed { i, _ -> i != index },
    frameSeconds = options.frames.indices.filter { it != index }.map { frameSeconds(options, it) })
fun reorderExpressionFrames(e: Element, from: Int, to: Int): Element = e.copy(media = reorderMediaFrames(e.media, from, to),
    expressionFrames = if (e.expressionFrames.size == e.media.frames.size) e.expressionFrames.toMutableList().apply { add(to, removeAt(from)) } else e.expressionFrames)
fun removeExpressionFrame(e: Element, index: Int): Element = e.copy(media = removeMediaFrame(e.media, index),
    expressionFrames = if (e.expressionFrames.size == e.media.frames.size) e.expressionFrames.filterIndexed { i, _ -> i != index } else e.expressionFrames)

/** The caller's lifecycle coroutine owns this loop; cancellation stops the current wait. */
suspend fun runMediaSequence(options: MediaOptions, initialTick: Long = 0,
    wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }, publish: (Long) -> Unit) {
    if (options.frames.size <= 1) return
    var tick = initialTick
    while (options.loop || tick < options.frames.lastIndex) {
        wait((frameSeconds(options, frameIndex(tick, options.frames.size, options.loop)) * 1000).toLong())
        tick++; publish(tick)
    }
}

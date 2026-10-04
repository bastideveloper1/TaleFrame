package com.r0ybt.taleframe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.taleframe.data.*
import kotlin.math.roundToInt

/** Only the stage owns editor pointer events. Selection never competes with a tap dialog. */
@Composable
fun SlideCanvas(
    slide: Slide, elements: List<Element>, modifier: Modifier = Modifier, editing: Boolean = false,
    onSelect: (Element?) -> Unit = {}, onMove: (Element) -> Unit = {}, onNavigate: (Long) -> Unit = {},
    selectedId: Long? = null, onResize: (Element) -> Unit = {}, showActions: Boolean = false,
    destinations: List<Slide> = emptyList(), reference: Slide? = null, referenceElements: List<Element> = emptyList(),
    referenceOpacity: Float = .35f, movingBackground: Boolean = false, onBackgroundMove: (Slide) -> Unit = {},
    preview: Boolean = false, onAction: ((Long, Transition) -> Unit)? = null
) {
    BoxWithConstraints(modifier.background(Color(0xFF101014)), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * .75f)
        val height = width / .75f
        var stage by remember(slide.id) { mutableStateOf(IntSize.Zero) }
        var draft by remember(slide.id) { mutableStateOf<Element?>(null) }
        var backgroundDraft by remember(slide.id) { mutableStateOf<Slide?>(null) }
        val measured = remember(slide.id) { mutableStateMapOf<Long, IntSize>() }
        LaunchedEffect(elements) {
            val pending = draft
            if (pending != null) {
                val saved = elements.find { it.id == pending.id }
                if (saved == null || saved.locked || (saved.x == pending.x && saved.y == pending.y && saved.width == pending.width && saved.height == pending.height)) draft = null
            }
        }
        LaunchedEffect(slide) {
            val pending = backgroundDraft
            if (pending != null && (slide.backgroundLocked || (slide.backgroundX == pending.backgroundX && slide.backgroundY == pending.backgroundY))) backgroundDraft = null
        }
        val currentElements by rememberUpdatedState(elements)
        val currentSlide by rememberUpdatedState(slide)
        val select by rememberUpdatedState(onSelect)
        val move by rememberUpdatedState(onMove)
        val resize by rememberUpdatedState(onResize)
        val moveBackground by rememberUpdatedState(onBackgroundMove)
        val currentSelection by rememberUpdatedState(selectedId)
        val backgroundMode by rememberUpdatedState(movingBackground)
        val density = LocalDensity.current
        val touchSize = with(density) { 48.dp.toPx() }
        val handleRadius = with(density) { 24.dp.toPx() }
        val gesture = if (editing) Modifier.pointerInput(slide.id, stage, density.density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val previousDraft = draft
                val previousBackgroundDraft = backgroundDraft
                val ordered = currentElements.map { draft?.takeIf { d -> d.id == it.id } ?: it }.sortedWith(compareBy<Element> { it.layer }.thenBy { it.id })
                fun bounds(e: Element): ElementBounds {
                    val size = measured[e.id] ?: IntSize((stage.width * .3f).toInt(), (stage.height * .12f).toInt())
                    return elementBounds(e, stage.width.toFloat(), stage.height.toFloat(), size.width.toFloat(), size.height.toFloat())
                }
                val selected = ordered.find { it.id == currentSelection }
                val selectedBounds = selected?.let(::bounds)
                val handle = selected != null && !selected.locked && selectedBounds != null && selectedBounds.width >= touchSize && selectedBounds.height >= touchSize &&
                    (down.position - Offset(selectedBounds.left + selectedBounds.width, selectedBounds.top + selectedBounds.height)).getDistance() < handleRadius && selected.rotation == 0f
                val hit = if (handle) selected else ordered.asReversed().firstOrNull { bounds(it).contains(down.position.x, down.position.y, it.rotation, 0f) }
                    ?: ordered.asReversed().firstOrNull { bounds(it).contains(down.position.x, down.position.y, it.rotation, touchSize) }
                val startBounds = hit?.let(::bounds)
                val adjustBackground = backgroundMode && currentSlide.image != null && currentSlide.backgroundMode == "manual" && !currentSlide.backgroundLocked
                if (!adjustBackground) select(hit)
                down.consume()
                var accumulated = Offset.Zero
                var dragging = false
                var changed = hit
                val startBackground = backgroundDraft ?: currentSlide
                var changedBackground = startBackground
                var cancelled = false
                var completed = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null) { cancelled = true; break }
                        // Compose synthesizes ACTION_CANCEL as an already-consumed UP.
                        // Check consumption before release so cancellation never commits.
                        if (pointer.isConsumed) { cancelled = true; break }
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        val delta = pointer.position - pointer.previousPosition
                        accumulated += delta
                        if (!dragging && accumulated.getDistance() >= viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            if (adjustBackground) {
                                changedBackground = startBackground.copy(
                                    backgroundX = boundedValue(startBackground.backgroundX + accumulated.x / stage.width.coerceAtLeast(1), -1f, 1f, 0f),
                                    backgroundY = boundedValue(startBackground.backgroundY + accumulated.y / stage.height.coerceAtLeast(1), -1f, 1f, 0f))
                                backgroundDraft = changedBackground
                            } else if (hit != null && !hit.locked) {
                                val b = requireNotNull(startBounds)
                                changed = if (handle) {
                                    val newWidth = boundedValue((b.width + accumulated.x) / stage.width.coerceAtLeast(1), .05f, 1f, .3f)
                                    val newHeight = boundedValue((b.height + accumulated.y) / stage.height.coerceAtLeast(1), .04f, 1f, .15f)
                                    hit.copy(width = newWidth, height = newHeight,
                                        x = boundedPosition(b.left / (stage.width * (1f - newWidth)).coerceAtLeast(1f)),
                                        y = boundedPosition(b.top / (stage.height * (1f - newHeight)).coerceAtLeast(1f)))
                                }
                                else hit.copy(x = boundedPosition(hit.x + accumulated.x / (stage.width - b.width).coerceAtLeast(1f)),
                                    y = boundedPosition(hit.y + accumulated.y / (stage.height - b.height).coerceAtLeast(1f)))
                                draft = changed
                            }
                        }
                        pointer.consume()
                    }
                    if (dragging && !cancelled) {
                        if (adjustBackground) moveBackground(changedBackground)
                        else if (changed != null && hit?.locked == false) { if (handle) resize(changed) else move(changed) }
                    }
                    completed = !cancelled
                } finally {
                    if (!completed) { draft = previousDraft; backgroundDraft = previousBackgroundDraft }
                }
            }
        } else Modifier
        Box(Modifier.size(width, height).onSizeChanged { stage = it }.clip(RoundedCornerShape(if (editing) 8.dp else 0.dp)).then(gesture).testTag("stage")) {
            // Current composition is translucent only in editor calco mode. Reference is inert.
            if (editing && reference != null) {
                Composition(reference, referenceElements, stage, emptyMap(), null, false, null, false, emptyList(), true, {})
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (editing && reference != null) 1f - referenceOpacity.coerceIn(0f, .9f) else 1f }) {
                Composition(backgroundDraft ?: slide, elements, stage, measured, draft, editing, selectedId, showActions, destinations, preview, onNavigate, onAction) { id, size -> measured[id] = size }
            }
        }
    }
}

@Composable
private fun Composition(
    slide: Slide, elements: List<Element>, stage: IntSize, measured: Map<Long, IntSize>, draft: Element?,
    editing: Boolean, selectedId: Long?, showActions: Boolean, destinations: List<Slide>, preview: Boolean,
    navigate: (Long) -> Unit, action: ((Long, Transition) -> Unit)? = null, measure: (Long, IntSize) -> Unit = { _, _ -> }
) {
    Box(Modifier.fillMaxSize().background(Color(slide.color))) {
        if (slide.image != null) LocalMedia(slide.image, slide.media,
            Modifier.fillMaxSize().graphicsLayer {
                if (slide.backgroundMode == "manual") {
                    scaleX = slide.backgroundScale; scaleY = slide.backgroundScale
                    translationX = slide.backgroundX * stage.width; translationY = slide.backgroundY * stage.height
                }
            }, preview, slide.backgroundMode == "fill", editing)
        SlideAudio(slide, preview || editing)
        if (editing && slide.autoEnabled) Text("⏱ ${slide.autoSeconds} s → ${destinations.find { it.id == slide.autoTargetId }?.name ?: "Sin destino"}", color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).background(Color(0xAA000000)))
        elements.sortedWith(compareBy<Element> { it.layer }.thenBy { it.id }).forEach { original -> key(original.id) {
            val e = draft?.takeIf { it.id == original.id } ?: original
            ElementVisual(e, stage, measured[e.id] ?: IntSize.Zero, editing, selectedId == e.id, preview, { target -> if (action != null) action(target, e.transition) else navigate(target) }) { measure(e.id, it) }
            if (editing && showActions && e.kind == "button") {
                val size = measured[e.id] ?: IntSize.Zero
                val b = elementBounds(e, stage.width.toFloat(), stage.height.toFloat(), size.width.toFloat(), size.height.toFloat())
                Text("→ ${destinations.find { it.id == e.targetId }?.name ?: "Sin destino"}", color = Color.White,
                    fontSize = with(LocalDensity.current) { (stage.width * .032f).toSp() },
                    modifier = Modifier.offset { IntOffset(b.left.roundToInt(), (b.top + b.height).roundToInt()) }.background(Color(0xDD202026)))
            }
        } }
    }
}

@Composable
private fun ElementVisual(e: Element, stage: IntSize, measured: IntSize, editing: Boolean, selected: Boolean, preview: Boolean,
    navigate: (Long) -> Unit, onMeasure: (IntSize) -> Unit) {
    val density = LocalDensity.current
    val b = elementBounds(e, stage.width.toFloat(), stage.height.toFloat(), measured.width.toFloat(), measured.height.toFloat())
    val dimensions = if (e.width > 0 && e.height > 0) with(density) { Modifier.size((stage.width * e.width).toDp(), (stage.height * e.height).toDp()) }
        else with(density) { Modifier.widthIn(max = (stage.width * .8f).toDp()).heightIn(max = stage.height.toDp()) }
    val padding = with(density) { (stage.width * .025f).toDp() }
    val font = with(density) { (stage.width * .045f).toSp() }
    val click = if (!editing && !preview && e.kind == "button" && e.targetId != null) Modifier.clickable { navigate(e.targetId) } else Modifier
    Box(Modifier.offset { IntOffset(b.left.roundToInt(), b.top.roundToInt()) }.then(dimensions).onSizeChanged(onMeasure)
        .graphicsLayer { rotationZ = e.rotation; scaleX = if (e.flipped) -1f else 1f }
        .testTag("element-${e.id}").semantics { contentDescription = if (e.kind == "image") "Imagen ${e.id}" else e.text }
        .then(if (selected && editing) Modifier.border(2.dp, if (e.locked) Color(0xFFFFC107) else Color(0xFFC8B6FF)) else Modifier)
        .then(click)) {
        if (e.kind == "image") {
            LocalMedia(e.image, e.media, Modifier.fillMaxSize().graphicsLayer { alpha = e.opacity }, preview, editing = editing)
        } else {
            Box(Modifier.then(if (e.width > 0 && e.height > 0) Modifier.fillMaxSize() else Modifier)
                .graphicsLayer { alpha = e.opacity }.clip(RoundedCornerShape(if (e.kind == "button") 10.dp else 4.dp)).background(Color(e.backgroundColor)).padding(padding)) {
                Text(e.text, color = Color(e.textColor), fontSize = font, lineHeight = font * 1.25f, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected && editing && !e.locked && e.rotation == 0f && b.width >= with(density) { 48.dp.toPx() } && b.height >= with(density) { 48.dp.toPx() }) {
            Box(Modifier.align(Alignment.BottomEnd).size(12.dp).background(Color(0xFFC8B6FF)))
        }
    }
}

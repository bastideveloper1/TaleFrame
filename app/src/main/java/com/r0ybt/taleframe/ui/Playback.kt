package com.r0ybt.taleframe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

private class Exit(val from: Slide, val to: Long, val effect: Transition) {
    val progress=Animatable(0f)
    var ready by mutableStateOf(false)
    var outgoing:Map<String,androidx.compose.ui.graphics.ImageBitmap> = emptyMap()
    var incoming:Map<String,androidx.compose.ui.graphics.ImageBitmap> = emptyMap()
}
/** Decode only static thumbnails/posters in IO. Never start a destination MediaPlayer here. */
internal suspend fun prepareSlideVisuals(slide:Slide,elements:List<Element>):Map<String,androidx.compose.ui.graphics.ImageBitmap> {
    val assets=buildList {
        slide.image?.let {add(it to slide.media.type)}
        elements.filter {it.slideId==slide.id}.forEach {e->(e.media.frames.take(1).ifEmpty {listOfNotNull(e.image)}).forEach {add(it to e.media.type)}}
    }.distinct()
    if(assets.isEmpty()) return emptyMap()
    return withContext(Dispatchers.IO) {
    var bytes=0
    buildMap {
        assets.forEach {(path,type)->
            val bitmap=if(type=="video") PosterCache.load(path) else LocalImageCache.load(path,384)
            if(bitmap!=null && bytes+bitmap.width*bitmap.height*4<=12*1024*1024) {
                bytes+=bitmap.width*bitmap.height*4;put("${if(type=="video") "video" else "image"}:$path",bitmap)
            }
        }
    }
    }
}

@Composable
fun Player(slide: Slide?, elements: List<Element>, modifier: Modifier = Modifier, blockedTargets:Set<Long> = emptySet(), leave:()->Unit = {}, slides:List<Slide> = listOfNotNull(slide), navigate: (Long) -> Unit) {
    var blockedNotice by remember(slide?.id) {mutableStateOf(false)}
    val currentBlocked by rememberUpdatedState(blockedTargets)
    val gate = remember { ExitGate() }
    var retained by remember {mutableStateOf<Map<String,androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())}
    var entry by remember { mutableLongStateOf(0) }
    var exit by remember { mutableStateOf<Exit?>(null) }
    var showName by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    val active = foreground()
    val currentNavigate by rememberUpdatedState(navigate)
    val currentSlide by rememberUpdatedState(slide)
    val request: (Long, Transition) -> Unit = { target, effect ->
        val current = currentSlide
        if(target in currentBlocked && active && exit==null && gate.available(gate.token)) blockedNotice=true
        else if (current != null && exit == null && active && gate.take(gate.token)) exit = Exit(current, target, effect)
    }
    LaunchedEffect(slide?.id, entry, active) {
        val token = gate.enter()
        val current = slide
        if (active && exit == null && current?.autoEnabled == true && current.autoTargetId != null) {
            delay((current.autoSeconds.coerceIn(.2f, 3600f) * 1000).toLong())
            if(current.autoTargetId in currentBlocked) {if(gate.available(token) && exit==null) blockedNotice=true}
            else if (gate.take(token)) exit = Exit(current, current.autoTargetId, current.transition)
        }
    }
    LaunchedEffect(exit) {
        val action = exit ?: return@LaunchedEffect
        action.outgoing=prepareSlideVisuals(action.from,elements)
        action.incoming=slides.find {it.id==action.to}?.let {prepareSlideVisuals(it,elements)} ?: emptyMap()
        action.ready=true // progress already zero: there is no intermediate progress=1 frame.
        currentNavigate(action.to)
        // Wait until the destination has actually reached composition, before timing the transition.
        snapshotFlow {currentSlide?.id}.first {it==action.to}
        withFrameNanos { }
        action.progress.animateTo(1f, tween(action.effect.duration))
        retained=action.incoming
        exit = null; entry++ // Also starts a fresh visit for a self-link.
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        val action = exit?.takeIf {it.ready}
        val stageWidth=minOf(maxWidth,maxHeight*.75f)
        val travel=with(androidx.compose.ui.platform.LocalDensity.current) {stageWidth.toPx()}
        Box(Modifier.size(stageWidth,stageWidth/.75f).clipToBounds()) {
        CompositionLocalProvider(LocalVisuals provides (if(action!=null) action.outgoing+action.incoming else retained)) {
        if (action != null) {
            SlideCanvas(action.from, elements.filter { it.slideId == action.from.id }, Modifier.fillMaxSize().transitionLayer(action.effect, action.progress.value, false, travel), preview = true)
        }
        if (slide != null) key(slide.id, entry) {
            SlideCanvas(slide, elements.filter { it.slideId == slide.id }, Modifier.fillMaxSize().transitionLayer(action?.effect ?: Transition("none"), action?.progress?.value ?: 1f, true, travel),
                preview = action != null || !active, onAction = request)
        }
        }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xAA202026)),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=leave,modifier=Modifier.weight(.45f)) {Text("Salir de reproducción",color=Color.White,style=androidx.compose.material3.MaterialTheme.typography.labelMedium)}
            Column(Modifier.weight(.55f)) {
                TextButton(onClick={showName=!showName}) {Text("${if(showName) "✓ " else ""}Mostrar nombre de lámina",color=Color.White,style=androidx.compose.material3.MaterialTheme.typography.labelMedium)}
                if(showName && slide!=null) Text("Lámina ${slides.indexOfFirst {it.id==slide.id}+1} · ${slide.name}",Modifier.padding(4.dp),color=Color.White,style=androidx.compose.material3.MaterialTheme.typography.labelSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        if(blockedNotice) androidx.compose.material3.AlertDialog(onDismissRequest={blockedNotice=false},title={Text("Destino en borrador")},text={Text("Esta acción apunta a una lámina omitida. Permaneces en la escena actual; puedes usar otra acción o salir de reproducción. No se eligió una ruta alternativa.")},confirmButton={TextButton(onClick={blockedNotice=false}){Text("Permanecer aquí")}},dismissButton={TextButton(onClick=leave){Text("Salir de reproducción")}})
    }
}
private fun Modifier.transitionLayer(effect: Transition, progress: Float, incoming: Boolean, width: Float): Modifier = graphicsLayer {
    when (effect.type) {
        "fade" -> alpha = if (incoming) progress else 1f
        "left", "right" -> translationX = (if (effect.type == "left") -1 else 1) * width * (if (incoming) progress - 1 else progress)
    }
}
@Composable
fun TransitionPreview(effect: Transition) {
    var replay by remember { mutableIntStateOf(0) }
    val progress=remember {Animatable(1f)}
    LaunchedEffect(replay, effect) { progress.snapTo(0f); progress.animateTo(1f, tween(effect.duration)) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp).clipToBounds().background(Color.Black)) {
        Box(Modifier.fillMaxSize().transitionLayer(effect, progress.value, false, constraints.maxWidth.toFloat()).background(Color(0xFF263238)), contentAlignment = Alignment.Center) { Text("A", color = Color.White) }
        Box(Modifier.fillMaxSize().transitionLayer(effect, progress.value, true, constraints.maxWidth.toFloat()).background(Color(0xFF6750A4)), contentAlignment = Alignment.Center) { Text("B", color = Color.White) }
    }
    TextButton(onClick = { replay++ }) { Text("Probar transición") }
}

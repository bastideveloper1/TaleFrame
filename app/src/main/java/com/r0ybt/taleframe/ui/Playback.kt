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

private data class Exit(val from: Slide, val to: Long, val effect: Transition)
@Composable
fun Player(slide: Slide?, elements: List<Element>, modifier: Modifier = Modifier, blockedTargets:Set<Long> = emptySet(), leave:()->Unit = {}, navigate: (Long) -> Unit) {
    var blockedNotice by remember(slide?.id) {mutableStateOf(false)}
    val currentBlocked by rememberUpdatedState(blockedTargets)
    val gate = remember { ExitGate() }
    var entry by remember { mutableLongStateOf(0) }
    var exit by remember { mutableStateOf<Exit?>(null) }
    val progress = remember { Animatable(1f) }
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
        // Recomposition has removed all active outgoing multimedia before navigation.
        progress.snapTo(0f)
        currentNavigate(action.to)
        progress.animateTo(1f, tween(action.effect.duration))
        exit = null; entry++ // Also starts a fresh visit for a self-link.
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        val action = exit
        if (action != null) {
            SlideCanvas(action.from, elements.filter { it.slideId == action.from.id }, Modifier.fillMaxSize().transitionLayer(action.effect, progress.value, false, constraints.maxWidth.toFloat()), preview = true)
        }
        if (slide != null) key(slide.id, entry) {
            SlideCanvas(slide, elements.filter { it.slideId == slide.id }, Modifier.fillMaxSize().transitionLayer(action?.effect ?: Transition("none"), progress.value, true, constraints.maxWidth.toFloat()),
                preview = action != null || !active, onAction = request)
        }
        TextButton(onClick=leave,modifier=Modifier.align(Alignment.TopStart).background(Color(0xAA202026))) {Text("Salir de reproducción",color=Color.White)}
        if(blockedNotice) androidx.compose.material3.AlertDialog(onDismissRequest={blockedNotice=false},title={Text("Destino en borrador")},text={Text("Esta acción apunta a una lámina omitida. Permaneces en la escena actual; puedes usar otra acción o salir de reproducción. No se eligió una ruta alternativa.")},confirmButton={TextButton(onClick={blockedNotice=false}){Text("Permanecer aquí")}},dismissButton={TextButton(onClick=leave){Text("Salir de reproducción")}})
    }
}
private fun Modifier.transitionLayer(effect: Transition, progress: Float, incoming: Boolean, width: Float): Modifier = graphicsLayer {
    when (effect.type) {
        "fade" -> alpha = if (incoming) progress else 1f - progress
        "left", "right" -> translationX = (if (effect.type == "left") -1 else 1) * width * (if (incoming) progress - 1 else progress)
    }
}
@Composable
fun TransitionPreview(effect: Transition) {
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(replay, effect) { progress.snapTo(0f); progress.animateTo(1f, tween(effect.duration)) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp).clipToBounds().background(Color.Black)) {
        Box(Modifier.fillMaxSize().transitionLayer(effect, progress.value, false, constraints.maxWidth.toFloat()).background(Color(0xFF263238)), contentAlignment = Alignment.Center) { Text("A", color = Color.White) }
        Box(Modifier.fillMaxSize().transitionLayer(effect, progress.value, true, constraints.maxWidth.toFloat()).background(Color(0xFF6750A4)), contentAlignment = Alignment.Center) { Text("B", color = Color.White) }
    }
    TextButton(onClick = { replay++ }) { Text("Probar transición") }
}

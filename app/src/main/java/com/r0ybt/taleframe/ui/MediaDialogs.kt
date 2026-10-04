package com.r0ybt.taleframe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*
import java.io.File

@Composable
fun MediaControls(options: MediaOptions, change: (MediaOptions) -> Unit) {
    if (options.type == "image") return
    Text(when (options.type) { "gif" -> "GIF animado"; "video" -> "Video local"; else -> "Secuencia de imágenes" })
    Row { Switch(options.loop, { change(options.copy(loop = it)) }); Text("Repetir", Modifier.padding(12.dp)) }
    Text("Una vez: conserva el último frame; no cambia de lámina.", style = MaterialTheme.typography.bodySmall)
    if (options.type == "video") {
        Row { Switch(options.autoplay, { change(options.copy(autoplay = it)) }); Text("Reproducción automática", Modifier.padding(12.dp)) }
        Row { Switch(!options.muted, { change(options.copy(muted = !it)) }); Text("Audio del video", Modifier.padding(12.dp)) }
        Text("Volumen: ${(options.volume * 100).toInt()} %")
        Slider(options.volume, { change(options.copy(volume = it)) }, enabled = !options.muted)
        if (!options.autoplay) Text("En Play aparecerá ▶ / Pausa. En el editor se muestra el primer frame.")
    }
    if (options.type == "slideshow") {
        SecondsField("Duración por imagen (s)", options.seconds) { change(options.copy(seconds = it)) }
        Text("Todas las imágenes heredan la posición, tamaño, rotación y opacidad de este único elemento.")
        options.frames.forEachIndexed { i, path ->
            Row {
                Text("${i + 1}. ${File(path).name.take(8)}", Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = {
                    val frames = options.frames.toMutableList(); val previous = frames[i - 1]; frames[i - 1] = frames[i]; frames[i] = previous
                    change(options.copy(frames = frames))
                }, enabled = i > 0) { Text("↑") }
                TextButton(onClick = { change(options.copy(frames = options.frames.filterIndexed { index, _ -> index != i })) }, enabled = options.frames.size > 1) { Text("Quitar") }
            }
        }
    }
}
@Composable
fun SecondsField(label: String, value: Float, change: (Float) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    val valid = text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in .2f..3600f }
    OutlinedTextField(text, { text = it; it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v.isFinite() && v in .2f..3600f }?.let(change) }, label = { Text(label) }, singleLine = true,
        isError = valid == null, supportingText = { Text("0,2–3600 segundos; un valor inválido conserva el anterior.") })
}
@Composable
fun TransitionControls(effect: Transition, change: (Transition) -> Unit) {
    Text("Transición de esta acción")
    listOf(listOf("none" to "Ninguna", "fade" to "Fade"),listOf("left" to "Deslizar izquierda", "right" to "Deslizar derecha")).forEach {row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            row.forEach {(type,name)->
                Card(onClick={change(effect.copy(type=type))},modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=if(effect.type==type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(when(type) {"none"->"A │ B";"fade"->"A ░ B";"left"->"A ← B";else->"B → A"},Modifier.padding(12.dp),style=MaterialTheme.typography.titleLarge)
                    Text("${if(effect.type==type) "✓ " else ""}$name",Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (effect.type != "none") {
        Text("Duración: ${effect.millis} ms")
        Slider(effect.millis.toFloat(), { change(effect.copy(millis = it.toInt())) }, valueRange = 200f..1000f)
    }
    TransitionPreview(effect)
}
@Composable
fun SlideBehaviorDialog(slide: Slide, slides: List<Slide>, elements: List<Element>, dismiss: () -> Unit, save: (Slide) -> Unit, importAudio: () -> Unit, actionPresets: List<Preset> = emptyList()) {
    var draft by remember(slide.id) { mutableStateOf(slide) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Audio y avance automático") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = importAudio) { Text(if (draft.audio == null) "Elegir audio local" else "Reemplazar audio") }
            if (draft.audio != null) {
                Text("El audio inicia en Play y se libera al salir. El editor permanece en silencio.")
                Text("Volumen: ${(draft.audioVolume * 100).toInt()} %")
                Slider(draft.audioVolume, { draft = draft.copy(audioVolume = it) })
                Row { Switch(draft.audioLoop, { draft = draft.copy(audioLoop = it) }); Text("Repetir audio", Modifier.padding(12.dp)) }
                TextButton(onClick = { draft = draft.copy(audio = null) }) { Text("Eliminar audio") }
            }
            Row { Switch(draft.autoEnabled, { draft = draft.copy(autoEnabled = it) }); Text("Avance automático", Modifier.padding(12.dp)) }
            if (draft.autoEnabled) {
                SecondsField("Después de (s)", draft.autoSeconds) { draft = draft.copy(autoSeconds = it) }
                DestinationPicker(slides, elements, draft.autoTargetId, { draft = draft.copy(autoTargetId = it) })
                actionPresets.forEach { p -> TextButton(onClick = { draft = draft.copy(transition = p.transition) }) { Text("Usar acción: ${p.name}") } }
                TransitionControls(draft.transition) { draft = draft.copy(transition = it) }
            }
        }
    }, confirmButton = { TextButton(onClick = { save(draft) }, enabled = !draft.autoEnabled || draft.autoTargetId != null) { Text("Guardar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable
fun TransitionDialog(initial:Transition,dismiss:()->Unit,apply:(Transition)->Unit) {
    var effect by remember {mutableStateOf(initial)}
    AlertDialog(onDismissRequest=dismiss,title={Text("Transición de esta acción")},text={Column(Modifier.verticalScroll(rememberScrollState())) {TransitionControls(effect) {effect=it}}},confirmButton={TextButton(onClick={apply(effect)}){Text("Aplicar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

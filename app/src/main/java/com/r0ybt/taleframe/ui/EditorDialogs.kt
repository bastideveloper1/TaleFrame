package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

@Composable
fun NameDialog(title: String, initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=dismiss,title={ Text(title) },text={ OutlinedTextField(name,{ name=it },label={ Text("Nombre") },singleLine=true) },
        confirmButton={ TextButton(onClick={ save(name.trim()) },enabled=name.isNotBlank()) { Text("Guardar") } },dismissButton={ TextButton(onClick=dismiss) { Text("Cancelar") } })
}

@Composable
fun ColorPicker(label: String, selected: Int, pick: (Int) -> Unit) {
    val colors = listOf(0xFFFFFFFF,0xFF000000,0xFF263238,0xFF6750A4,0xFF1565C0,0xFF008577,0xFFFFC107,0xFFE57373,0xFFFFE0B2,0xFFE1BEE7)
    var hex by remember(selected) { mutableStateOf("%06X".format(selected and 0xFFFFFF)) }
    val custom = hex.takeIf { it.length == 6 && it.all { c -> c in "0123456789abcdefABCDEF" } }?.toLongOrNull(16)?.let { (it or 0xFF000000).toInt() }
    Text(label,style=MaterialTheme.typography.labelLarge)
    colors.chunked(5).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=4.dp)) {
        row.forEach { value -> val color=value.toInt(); Surface(onClick={pick(color)},color=Color(color),shape=MaterialTheme.shapes.small,modifier=Modifier.size(38.dp).semantics { contentDescription="Color #${"%06X".format(color and 0xFFFFFF)}" },border=androidx.compose.foundation.BorderStroke(if(color==selected) 3.dp else 1.dp,if(color==selected) MaterialTheme.colorScheme.primary else Color.Gray)) {} }
    } }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(hex,{hex=it.take(6)},label={Text("HEX (RRGGBB)")},singleLine=true,modifier=Modifier.weight(1f))
        TextButton(onClick={custom?.let(pick)},enabled=custom!=null) { Text("Aplicar") }
    }
}

@Composable
fun ElementDialog(element: Element, slides: List<Slide>, elements: List<Element>, dismiss: () -> Unit, save: (Element) -> Unit) {
    var draft by remember(element.id) { mutableStateOf(element) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(when (element.kind) { "image" -> "Imagen"; "button" -> "Botón"; else -> "Cuadro de texto" }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (element.kind != "image") OutlinedTextField(draft.text, { draft = draft.copy(text = it) }, label = { Text("Texto") }, minLines = 2, maxLines = 5)
            else Text("PNG/WebP conservan transparencia. Los cambios se guardan al confirmar.", style = MaterialTheme.typography.bodySmall)
            if (element.kind == "text") {
                ColorPicker("Color del texto", draft.textColor) { draft = draft.copy(textColor = it) }
                ColorPicker("Color del cuadro", draft.backgroundColor) { draft = draft.copy(backgroundColor = it) }
            }
            Row { Switch(draft.locked, { draft = draft.copy(locked = it) }); Text("Bloquear", Modifier.padding(12.dp)) }
            Text("Ancho: ${(draft.width * 100).toInt()} %${if (draft.width == 0f) " (automático)" else ""}")
            Slider(if (draft.width > 0f) draft.width else .6f, { draft = draft.copy(width = it, height = draft.height.takeIf { h -> h > 0 } ?: .18f) }, valueRange = .05f..1f, enabled = !draft.locked)
            Text("Alto: ${(draft.height * 100).toInt()} %${if (draft.height == 0f) " (automático)" else ""}")
            Slider(if (draft.height > 0f) draft.height else .18f, { draft = draft.copy(height = it, width = draft.width.takeIf { w -> w > 0 } ?: .6f) }, valueRange = .04f..1f, enabled = !draft.locked)
            if (element.kind == "image") {
                Text("Rotación: ${draft.rotation.toInt()}°")
                Slider(draft.rotation, { draft = draft.copy(rotation = it) }, valueRange = -180f..180f, enabled = !draft.locked)
                Row { Switch(draft.flipped, { draft = draft.copy(flipped = it) }, enabled = !draft.locked); Text("Voltear horizontalmente", Modifier.padding(12.dp)) }
                Text("Opacidad: ${(draft.opacity * 100).toInt()} %")
                Slider(draft.opacity, { draft = draft.copy(opacity = it) }, enabled = !draft.locked)
            }
            if (element.kind == "button") {
                Text("Lámina de destino")
                DestinationPicker(slides, elements, draft.targetId, { draft = draft.copy(targetId = it) })
            }
        }
    }, confirmButton = { TextButton(onClick = { save(draft.copy(text = draft.text.trim())) }, enabled = draft.kind == "image" || draft.text.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable
fun BackgroundDialog(slide: Slide, dismiss: () -> Unit, save: (Slide) -> Unit, import: () -> Unit) {
    var draft by remember(slide.id) { mutableStateOf(slide) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Fondo de la lámina") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPicker("Color sólido", draft.color) { save(draft.copy(color = it, image = null)) }
            Button(onClick = import) { Text("Elegir imagen local") }
            if (draft.image != null) {
                SlidePreview(draft, emptyList(), Modifier.fillMaxWidth().heightIn(max = 180.dp))
                listOf("fit" to "Encajar / Fit", "fill" to "Rellenar / Fill", "manual" to "Ajuste manual").forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().clickable { if (!draft.backgroundLocked) draft = draft.copy(backgroundMode = mode) }) {
                        RadioButton(draft.backgroundMode == mode, { draft = draft.copy(backgroundMode = mode) }, enabled = !draft.backgroundLocked)
                        Text(label, Modifier.padding(top = 12.dp))
                    }
                }
                if (draft.backgroundMode == "manual") {
                    Text("Escala: ${(draft.backgroundScale * 100).toInt()} %")
                    Slider(draft.backgroundScale, { draft = draft.copy(backgroundScale = it) }, valueRange = .25f..4f, enabled = !draft.backgroundLocked)
                    Text("Posición horizontal")
                    Slider(draft.backgroundX, { draft = draft.copy(backgroundX = it) }, valueRange = -1f..1f, enabled = !draft.backgroundLocked)
                    Text("Posición vertical")
                    Slider(draft.backgroundY, { draft = draft.copy(backgroundY = it) }, valueRange = -1f..1f, enabled = !draft.backgroundLocked)
                    Text("También puedes arrastrarlo con «Mover fondo» en el editor.", style = MaterialTheme.typography.bodySmall)
                }
                Row { Switch(draft.backgroundLocked, { draft = draft.copy(backgroundLocked = it) }); Text("Bloquear fondo", Modifier.padding(12.dp)) }
            }
            Text("Los archivos se copian al almacenamiento privado de TaleFrame.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { save(draft) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cerrar") } })
}

@Composable
fun CalcoDialog(slides: List<Slide>, elements: List<Element>, current: Long?, opacity: Float, dismiss: () -> Unit, save: (Long?, Float) -> Unit) {
    var target by remember { mutableStateOf(current) }
    var alpha by remember { mutableFloatStateOf(opacity) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Calco") }, text = {
        Column {
            Text("Referencia detrás de la lámina actual. No se edita ni aparece en Play.")
            DestinationPicker(slides, elements, target, { target = it }, allowNone = false)
            Text("Opacidad del calco: ${(alpha * 100).toInt()} %")
            Slider(alpha, { alpha = it }, valueRange = 0f.. .9f)
            TextButton(onClick = { target = null }) { Text("Desactivar calco") }
        }
    }, confirmButton = { TextButton(onClick = { save(target, alpha) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

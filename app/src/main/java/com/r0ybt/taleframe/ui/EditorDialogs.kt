package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    colors.chunked(5).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.padding(vertical=4.dp)) {
        row.forEach { value -> val color=value.toInt(); Surface(onClick={pick(color)},color=Color(color),shape=MaterialTheme.shapes.small,modifier=Modifier.size(48.dp).semantics { contentDescription="Color #${"%06X".format(color and 0xFFFFFF)}" },border=androidx.compose.foundation.BorderStroke(if(color==selected) 3.dp else 1.dp,if(color==selected) MaterialTheme.colorScheme.primary else Color.Gray)) {} }
    } }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(hex,{hex=it.take(6)},label={Text("HEX (RRGGBB)")},singleLine=true,modifier=Modifier.weight(1f))
        TextButton(onClick={custom?.let(pick)},enabled=custom!=null) { Text("Aplicar") }
    }
}

@Composable
fun ElementDialog(element: Element, slides: List<Slide>, elements: List<Element>, dismiss: () -> Unit, save: (Element) -> Unit, replace: () -> Unit = {}, addFrames: () -> Unit = {}, presets: List<Preset> = emptyList(), projectPlayback: ProjectPlayback = ProjectPlayback(), expressions: List<Expression> = emptyList(), assignLibrary:()->Unit = {}) {
    var draft by remember(element.id) { mutableStateOf(element) }
    val mediaAspect = if (element.supportsCornerResize) localMediaAspect(draft.image, draft.media.type) else null
    var showStyle by remember(element.id) { mutableStateOf(false) }
    var destinationOpen by remember(element.id) {mutableStateOf(false)}
    var presetPicker by remember(element.id) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(when (element.kind) { "image" -> if(element.panel!=null) "Panel" else "Imagen"; "button" -> "Botón"; else -> "Cuadro de texto" }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (element.kind == "button") {
                if (presets.any { it.kind == "button" }) TextButton(onClick = { presetPicker = "button" }) { Text("Elegir preset de botón") }
                if (presets.any { it.kind == "action" }) TextButton(onClick = { presetPicker = "action" }) { Text("Elegir preset de acción") }
            }
            if (element.kind != "image") OutlinedTextField(draft.text, { draft = draft.copy(text = it) }, label = { Text("Texto") }, minLines = 2, maxLines = 5)
            else {
                TextButton(onClick = {if(draft.panel!=null) save(draft);replace()}) { Text(if(draft.panel!=null) "Asignar imagen al panel" else "Reemplazar recurso") }
                if(draft.panel!=null) {TextButton(onClick={save(draft);assignLibrary()}){Text("Elegir imagen de Biblioteca")};TextButton(onClick={draft=draft.copy(image=null,resourceId=null,media=MediaOptions())}) {Text("Vaciar panel")}}
                if(draft.panel!=null) PanelControls(requireNotNull(draft.panel)) {draft=draft.copy(panel=it)}
                MediaControls(draft.media,
                    frameNames = draft.expressionFrames.map { id -> expressions.find { it.id == id }?.name ?: "Expresión" },
                    moveFrame = { from, to -> draft = reorderExpressionFrames(draft, from, to) },
                    removeFrame = { index -> draft = removeExpressionFrame(draft, index) }) { draft = draft.copy(media = it) }
                if (draft.panel==null && (draft.media.type == "image" || draft.media.type == "slideshow")) TextButton(onClick = { save(draft); addFrames() }) { Text("Añadir imágenes a la secuencia") }
            }
            if (element.kind == "text") {
                ColorPicker("Color del texto", draft.textColor) { draft = draft.copy(textColor = it) }
                ColorPicker("Color del cuadro", draft.backgroundColor) { draft = draft.copy(backgroundColor = it) }
            }
            Row { Switch(draft.locked, { draft = draft.copy(locked = it) }); Text("Bloquear", Modifier.padding(12.dp)) }
            if (draft.supportsCornerResize) {
                Row { Switch(draft.permitsOverflow, { draft = setElementOverflow(draft, it) }, enabled = !draft.locked)
                    Text("Permitir desbordamiento", Modifier.padding(12.dp)) }
                TextButton(onClick = {
                    val size = mediaElementSize(mediaAspect)
                    draft = draft.copy(x = .5f, y = .5f, width = size.first, height = size.second, freePosition = false)
                }, enabled = !draft.locked) { Text("Restablecer tamaño y posición") }
            }
            Text("Ancho: ${(draft.width * 100).toInt()} %${if (draft.width == 0f) " (automático)" else ""}")
            Slider(if (draft.width > 0f) draft.width else .6f, { draft = if (draft.supportsCornerResize) resizeMediaElementTo(draft, width = it, aspect = mediaAspect) else draft.copy(width = it, height = draft.height.takeIf { h -> h > 0 } ?: .18f) }, valueRange = .05f..(if(draft.supportsFreePlacement) maxOf(4f,draft.width) else 1f), enabled = !draft.locked)
            Text("Alto: ${(draft.height * 100).toInt()} %${if (draft.height == 0f) " (automático)" else ""}")
            Slider(if (draft.height > 0f) draft.height else .18f, { draft = if (draft.supportsCornerResize) resizeMediaElementTo(draft, height = it, aspect = mediaAspect) else draft.copy(height = it, width = draft.width.takeIf { w -> w > 0 } ?: .6f) }, valueRange = .04f..(if(draft.supportsFreePlacement) maxOf(4f,draft.height) else 1f), enabled = !draft.locked)
            if (element.kind == "image") {
                Text("Rotación: ${draft.rotation.toInt()}°")
                Slider(draft.rotation, { draft = draft.copy(rotation = it) }, valueRange = -180f..180f, enabled = !draft.locked)
                Row { Switch(draft.flipped, { draft = draft.copy(flipped = it) }, enabled = !draft.locked); Text("Voltear horizontalmente", Modifier.padding(12.dp)) }
                Text("Opacidad: ${(draft.opacity * 100).toInt()} %")
                Slider(draft.opacity, { draft = draft.copy(opacity = it) }, enabled = !draft.locked)
            }
            if (element.kind == "button") {
                Text("Lámina de destino")
                TextButton(onClick={destinationOpen=true}){Text("Elegir destino: ${slides.find {it.id==draft.targetId}?.name ?: "Sin destino"}")}
                if(destinationOpen) DestinationDialog(slides,elements,draft.targetId,element.slideId,{destinationOpen=false}) {id->draft=draft.copy(targetId=id);destinationOpen=false}
                Row { Switch(draft.useDefaultTransition, { draft = draft.copy(useDefaultTransition = it) }); Text("Usar transición predeterminada", Modifier.padding(12.dp)) }
                if (!draft.useDefaultTransition) TransitionControls(draft.transition) { draft = draft.copy(transition = it) }
                else TransitionPreview(effectiveTransition(projectPlayback, slides.find { it.id == draft.slideId } ?: Slide(0, 0, "")))
            }
            if (element.kind != "image") {
                Text("Editar modifica solo esta instancia.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showStyle = !showStyle }) { Text(if (showStyle) "Ocultar estilo" else "Estilo y presets") }
                if (showStyle) {
                    if (element.kind == "button") {
                        presets.filter { it.kind == "button" }.forEach { p -> TextButton(onClick = { draft = draft.copy(textColor = p.textColor, backgroundColor = p.backgroundColor, style = p.style, presetId = p.id) }) { Text("Usar estilo: ${p.name}") } }
                        presets.filter { it.kind == "action" }.forEach { p -> TextButton(onClick = { draft = draft.copy(transition = p.transition, useDefaultTransition = false) }) { Text("Usar acción: ${p.name}") } }
                        ColorPicker("Color del texto", draft.textColor) { draft = draft.copy(textColor = it) }
                        ColorPicker("Color del cuadro", draft.backgroundColor) { draft = draft.copy(backgroundColor = it) }
                    }
                    StyleControls(draft.style,button=draft.kind=="button") { draft = draft.copy(style = it, width = draft.width.takeIf { w -> w > 0 } ?: .6f, height = draft.height.takeIf { h -> h > 0 } ?: .3f) }
                    if (draft.style.showName) OutlinedTextField(draft.speakerName, { draft = draft.copy(speakerName = it) }, label = { Text("Nombre visible") }, singleLine = true)
                }
            }
            if (draft.sourceName.isNotBlank()) Text(draft.sourceName)
        }
    }, confirmButton = { TextButton(onClick = { save(draft.copy(text = draft.text.trim())) }, enabled = draft.kind == "image" || draft.text.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
    presetPicker?.let { kind -> PresetPicker(presets.filter { it.kind == kind }, if (kind == "button") "Estilo del botón" else "Acción del botón", { presetPicker = null }, { p ->
        draft = if (kind == "button") draft.copy(textColor = p.textColor, backgroundColor = p.backgroundColor, style = p.style, presetId = p.id) else draft.copy(transition = p.transition, useDefaultTransition = false)
        presetPicker = null
    }) }
}

@Composable
fun BackgroundDialog(slide: Slide, dismiss: () -> Unit, save: (Slide) -> Unit, import: () -> Unit, importGif: () -> Unit = {}, importVideo: () -> Unit = {}) {
    var draft by remember(slide.id) { mutableStateOf(slide) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Fondo de la lámina") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPicker("Color sólido", draft.color) { save(draft.copy(color = it, image = null, media = MediaOptions())) }
            Button(onClick = import) { Text("Elegir imagen local") }
            TextButton(onClick = importGif) { Text("Elegir GIF local") }
            TextButton(onClick = importVideo) { Text("Elegir video local") }
            if (draft.image != null) {
                MediaControls(draft.media) { draft = draft.copy(media = it) }
                TextButton(onClick = { draft = draft.copy(image = null, media = MediaOptions()) }) { Text("Eliminar recurso de fondo") }
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
fun CalcoDialog(slides: List<Slide>, elements: List<Element>, config: CalcoConfig, previous: Slide?,
    dismiss: () -> Unit, save: (CalcoConfig) -> Unit) {
    var mode by remember { mutableStateOf(if (config.configured) config.mode else CalcoMode.PREVIOUS) }
    var target by remember { mutableStateOf(config.manualId) }
    var alpha by remember { mutableFloatStateOf(config.opacity) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .6f).dp
    AlertDialog(onDismissRequest = dismiss, title = { Text("Calco") }, text = {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            Text("Referencia detrás de la lámina actual. No se edita ni aparece en Play.")
            listOf(CalcoMode.PREVIOUS to "Lámina anterior (automático)",
                CalcoMode.MANUAL to "Selección manual de lámina", CalcoMode.NONE to "Sin calco").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { mode = value }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(mode == value, onClick = { mode = value })
                    Text(label, Modifier.weight(1f))
                }
            }
            when (mode) {
                CalcoMode.PREVIOUS -> Text(previous?.let { "Referencia: ${it.name}" }
                    ?: "Esta es la primera lámina del álbum. No hay una lámina anterior.")
                CalcoMode.MANUAL -> DestinationPicker(slides, elements, target, { target = it }, allowNone = false)
                CalcoMode.NONE -> Unit
            }
            Text("Opacidad del calco: ${(alpha * 100).toInt()} %")
            Slider(alpha, { alpha = it }, valueRange = 0f.. .9f)
        }
    }, confirmButton = { TextButton(onClick = { save(CalcoConfig(mode, target, alpha, configured = true)) },
        enabled = mode != CalcoMode.MANUAL || slides.any { it.id == target }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable
fun QuickTextDialog(initial:String,dismiss:()->Unit,save:(String)->Unit) {
    var text by remember {mutableStateOf(initial)}
    val focus=remember {androidx.compose.ui.focus.FocusRequester()}
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    AlertDialog(onDismissRequest=dismiss,title={Text("Editar texto")},text={
        LaunchedEffect(Unit) {focus.requestFocus();keyboard?.show()}
        OutlinedTextField(text,{text=it},label={Text("Texto")},modifier=Modifier.fillMaxWidth().then(Modifier.focusRequester(focus)),minLines=2,maxLines=6)
    },confirmButton={TextButton(onClick={save(text)},enabled=text.isNotBlank()){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

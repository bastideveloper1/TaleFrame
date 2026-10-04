package com.r0ybt.taleframe.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.state.StoryViewModel

private data class NameRequest(val table: String, val id: Long?, val parent: Long?, val initial: String)
private data class DeleteRequest(val table: String, val id: Long, val name: String)

@Composable
fun TaleFrameApp(model: StoryViewModel) {
    val story by model.story.collectAsState()
    val ready by model.ready.collectAsState()
    val error by model.error.collectAsState()
    var projectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var slideId by rememberSaveable { mutableStateOf<Long?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var playSlideId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedId by rememberSaveable(slideId) { mutableStateOf<Long?>(null) }
    var actions by rememberSaveable { mutableStateOf(false) }
    var movingBackground by rememberSaveable(slideId) { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("editor", 0) }
    var referenceId by rememberSaveable(slideId) { mutableStateOf(preferences.getLong("calco_slide_$slideId", 0L).takeIf { it > 0 }) }
    var referenceOpacity by rememberSaveable(slideId) { mutableFloatStateOf(preferences.getFloat("calco_opacity_$slideId", .35f)) }
    var albumSize by rememberSaveable { mutableStateOf(preferences.getString("album_size", "Mediana") ?: "Mediana") }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    var elementRequest by remember { mutableStateOf<Element?>(null) }
    var showBackground by remember { mutableStateOf(false) }
    var showReference by remember { mutableStateOf(false) }
    var pickerSlide by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerKind by rememberSaveable { mutableStateOf("background") }
    val project = story.projects.find { it.id == projectId }
    val slides = story.slides.filter { it.projectId == projectId }
    val slide = slides.find { it.id == slideId }
    val selected = story.elements.find { it.slideId == slideId && it.id == selectedId }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pickerSlide
        val kind = pickerKind
        if (uri != null && id != null) model.edit {
            val current = read().slides.find { it.id == id }
            if (current != null) {
                val path = importImage(uri)
                if (kind == "background") background(id, current.color, path)
                else saveElement(Element(0, id, "image", "", image = path, width = .4f, height = .4f))
            }
        }
    }
    fun importImage(kind: String) { pickerSlide = slideId; pickerKind = kind; imagePicker.launch(arrayOf("image/png", "image/webp", "image/jpeg")) }
    fun back() {
        when { playing -> playing = false; slideId != null -> slideId = null; else -> projectId = null }
    }
    fun play() { playSlideId = slides.minByOrNull { it.id }?.id; playing = playSlideId != null }
    BackHandler(projectId != null || playing) { back() }
    Scaffold { padding ->
        if (playing) {
            Player(slides.find { it.id == playSlideId } ?: slides.minByOrNull { it.id }, story.elements, Modifier.padding(padding)) { target ->
                if (slides.any { it.id == target }) playSlideId = target
            }
        } else Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (projectId != null) TextButton(onClick = { back() }) { Text("‹ Volver") }
                Text(slide?.name ?: project?.name ?: "TaleFrame", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(12.dp), maxLines = 1)
                if (slides.isNotEmpty()) TextButton(onClick = { play() }) { Text("▶ Play") }
            }
            if (!ready) Text("Abriendo proyectos…", Modifier.padding(24.dp))
            else if (projectId == null) {
                Text("Tus historias · en este dispositivo", Modifier.padding(horizontal = 24.dp))
                Button(onClick = { nameRequest = NameRequest("projects", null, null, "") }, modifier = Modifier.padding(20.dp)) { Text("+ Crear proyecto") }
                if (story.projects.isEmpty()) Text("Crea tu primera historia para comenzar.", Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(story.projects, key = { it.id }) { p ->
                        Card(Modifier.fillMaxWidth().clickable { projectId = p.id }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("${story.slides.count { it.projectId == p.id }} láminas")
                                Row {
                                    TextButton(onClick = { nameRequest = NameRequest("projects", p.id, null, p.name) }) { Text("Renombrar") }
                                    TextButton(onClick = { deleteRequest = DeleteRequest("projects", p.id, p.name) }) { Text("Eliminar") }
                                }
                            }
                        }
                    }
                }
            } else if (slide == null) {
                Text("Álbum de láminas", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { nameRequest = NameRequest("slides", null, projectId, "Lámina ${slides.size + 1}") }) { Text("+ Crear lámina") }
                    listOf("Grande", "Mediana", "Pequeña").forEach { size ->
                        FilterChip(albumSize == size, onClick = { albumSize = size; preferences.edit { putString("album_size", size) } }, label = { Text(size) })
                    }
                }
                if (slides.isEmpty()) Text("Añade la primera lámina de tu historia.", Modifier.padding(24.dp))
                Album(slides, story.elements, albumSize, Modifier.weight(1f),
                    open = { slideId = it.id }, rename = { nameRequest = NameRequest("slides", it.id, null, it.name) },
                    delete = { deleteRequest = DeleteRequest("slides", it.id, it.name) },
                    duplicate = { model.edit { duplicateSlide(it.id) } }, reorder = { s, delta -> model.edit { reorderSlide(s.id, delta) } })
            } else {
                SlideCanvas(slide, story.elements.filter { it.slideId == slide.id }, Modifier.weight(1f).fillMaxWidth(), editing = true,
                    onSelect = { selectedId = it?.id }, onMove = { e -> model.edit { moveElement(e.id, e.x, e.y) } },
                    onResize = { e -> model.edit { resizeElement(e.id, e.width, e.height, e.x, e.y) } }, selectedId = selectedId,
                    showActions = actions, destinations = slides, reference = slides.find { it.id == referenceId },
                    referenceElements = story.elements.filter { it.slideId == referenceId }, referenceOpacity = referenceOpacity,
                    movingBackground = movingBackground, onBackgroundMove = { s -> model.edit {
                        val current = read().slides.find { it.id == s.id }
                        if (current != null && !current.backgroundLocked) saveSlide(current.copy(backgroundX = s.backgroundX, backgroundY = s.backgroundY))
                    } })
                // Fixed-height controls: selecting on DOWN cannot resize the stage and cancel a gesture.
                Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
                    if (selected == null) Text(if (movingBackground) "Arrastra el fondo manual" else "Toca para seleccionar · arrastra para mover", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                    else {
                        TextButton(onClick = { elementRequest = selected }) { Text("Editar") }
                        TextButton(onClick = { model.edit { toggleLock(selected.id) } }) { Text(if (selected.locked) "Desbloquear" else "Bloquear") }
                        TextButton(onClick = { model.edit { duplicateElement(selected.id) } }) { Text("Duplicar") }
                        TextButton(onClick = { model.edit { layer(selected.id, true) } }) { Text("Al frente") }
                        TextButton(onClick = { model.edit { layer(selected.id, false) } }) { Text("Atrás") }
                        TextButton(onClick = { deleteRequest = DeleteRequest("elements", selected.id, "elemento") }) { Text("Eliminar") }
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { showBackground = true }) { Text("Fondo") }
                    TextButton(onClick = { elementRequest = Element(0, slide.id, "text", "", y = .15f, width = .6f, height = .18f) }) { Text("+ Texto") }
                    TextButton(onClick = { elementRequest = Element(0, slide.id, "button", "", x = .1f + (story.elements.count { it.slideId == slide.id && it.kind == "button" } % 3) * .3f,
                        y = .7f, backgroundColor = 0xFF6750A4.toInt(), textColor = -1, width = .28f, height = .1f) }) { Text("+ Botón") }
                    TextButton(onClick = { importImage("element") }) { Text("+ Imagen") }
                    TextButton(onClick = { actions = !actions }) { Text(if (actions) "Ocultar acciones" else "Ver acciones") }
                    TextButton(onClick = { showReference = true }) { Text("Calco") }
                    TextButton(onClick = { movingBackground = !movingBackground }, enabled = slide.image != null && slide.backgroundMode == "manual" && !slide.backgroundLocked) { Text(if (movingBackground) "Terminar fondo" else "Mover fondo") }
                    TextButton(onClick = { selectedId = null; movingBackground = false }) { Text("Deseleccionar") }
                    TextButton(onClick = { nameRequest = NameRequest("layers", null, null, "") }) { Text("Elementos") }
                }
            }
        }
    }
    nameRequest?.let { request ->
        if (request.table == "layers") {
            AlertDialog(onDismissRequest = { nameRequest = null }, title = { Text("Elementos · arriba primero") }, text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(story.elements.filter { it.slideId == slideId }.sortedByDescending { it.layer }, key = { it.id }) { e ->
                        TextButton(onClick = { selectedId = e.id; nameRequest = null }) { Text("${if (e.locked) "🔒 " else ""}${if (e.kind == "image") "Imagen ${e.id}" else e.text}") }
                    }
                }
            }, confirmButton = { TextButton(onClick = { nameRequest = null }) { Text("Cerrar") } })
        } else NameDialog(if (request.id == null) "Crear" else "Renombrar", request.initial, { nameRequest = null }) { name ->
            model.edit { if (request.id != null) rename(request.table, request.id, name) else if (request.table == "projects") createProject(name) else createSlide(requireNotNull(request.parent), name) }
            nameRequest = null
        }
    }
    deleteRequest?.let { request ->
        AlertDialog(onDismissRequest = { deleteRequest = null }, title = { Text("Eliminar ${request.name}") },
            text = { Text(if (request.table == "projects") "Se eliminarán todas sus láminas y elementos." else if (request.table == "slides") "Se eliminarán sus elementos. Los botones que apuntan aquí quedarán sin destino." else "Se eliminará el elemento seleccionado.") },
            confirmButton = { TextButton(onClick = { model.edit { delete(request.table, request.id) }; deleteRequest = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteRequest = null }) { Text("Cancelar") } })
    }
    elementRequest?.let { e -> ElementDialog(e, slides, story.elements, { elementRequest = null }, { updated -> model.edit { editElement(e, updated) }; elementRequest = null }) }
    if (showBackground && slide != null) BackgroundDialog(slide, { showBackground = false },
        save = { s -> model.edit { editBackground(slide, s) }; showBackground = false }, import = { showBackground = false; importImage("background") })
    if (showReference && slide != null) CalcoDialog(slides.filter { it.id != slide.id }, story.elements, referenceId, referenceOpacity,
        { showReference = false }, { id, opacity ->
            referenceId = id; referenceOpacity = opacity; showReference = false
            preferences.edit { putLong("calco_slide_$slideId", id ?: 0); putFloat("calco_opacity_$slideId", opacity) }
        })
    error?.let { message -> AlertDialog(onDismissRequest = { model.clearError() }, title = { Text("No se pudo completar el cambio") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.clearError() }) { Text("Aceptar") } }) }
}

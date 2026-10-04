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
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var showCharacters by rememberSaveable { mutableStateOf(false) }
    var showDialogs by rememberSaveable { mutableStateOf(false) }
    var expressionCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var changingExpressionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resourceUseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resourceUseSlide by rememberSaveable { mutableStateOf<Long?>(null) }
    var resourceUseMode by rememberSaveable { mutableStateOf("element") }
    var pickerProject by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerCategory by rememberSaveable { mutableStateOf("image") }
    var pickerResource by rememberSaveable { mutableStateOf<Long?>(null) }
    var savePresetElement by remember { mutableStateOf<Element?>(null) }
    var savePresetKind by remember { mutableStateOf("dialog") }
    var showBackground by remember { mutableStateOf(false) }
    var showBehavior by remember { mutableStateOf(false) }
    var pickerElement by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerType by rememberSaveable { mutableStateOf("image") }
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
        val type = pickerType
        val elementId = pickerElement
        val importingProject = pickerProject
        val resourceId = pickerResource
        val category = pickerCategory
        if (uri != null && importingProject != null && kind in listOf("library", "libraryReplace")) model.edit {
            if (read().projects.any { it.id == importingProject }) {
                if (kind == "libraryReplace" && resourceId != null) library.replaceResource(resourceId, uri)
                else library.importResource(importingProject, uri, displayName(uri), type, category)
            }
        }
        else if (uri != null && id != null) model.edit {
            val current = read().slides.find { it.id == id }
            if (current != null) {
                val path = importMedia(uri, type)
                val resource = library.addResource(current.projectId, displayName(uri), type, if (kind == "background") "background" else type, path)
                when (kind) {
                    "background" -> saveSlide(current.copy(image = path, backgroundResourceId = resource, media = MediaOptions(type = type, revision = current.media.revision + 1)))
                    "audio" -> saveSlide(current.copy(audio = path, audioResourceId = resource, audioRevision = current.audioRevision + 1))
                    "replace" -> {
                        val element = read().elements.find { it.id == elementId }
                        if (element != null) saveElement(element.copy(image = path, resourceId = resource, characterId = null, expressionId = null, expressionFrames = emptyList(), sourceName = "", media = MediaOptions(type = type, revision = element.media.revision + 1))) else cleanImages()
                    }
                    else -> saveElement(Element(0, id, "image", "", image = path, width = .4f, height = .4f, resourceId = resource, media = MediaOptions(type = type)))
                }
            }
        }
    }
    fun importImage(kind: String, type: String = "image", element: Long? = null) {
        pickerSlide = slideId; pickerKind = kind; pickerType = type; pickerElement = element
        imagePicker.launch(when (type) { "video" -> arrayOf("video/*"); "audio" -> arrayOf("audio/*"); "gif" -> arrayOf("image/gif"); else -> arrayOf("image/png", "image/webp", "image/jpeg") })
    }
    fun importLibrary(type: String, category: String, resource: Resource? = null) {
        pickerProject = projectId; pickerCategory = category; pickerResource = resource?.id
        pickerType = type; pickerKind = if (resource == null) "library" else "libraryReplace"
        imagePicker.launch(when(type) { "video" -> arrayOf("video/*"); "audio" -> arrayOf("audio/*"); "gif" -> arrayOf("image/gif"); else -> arrayOf("image/png", "image/webp", "image/jpeg") })
    }
    val sequencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val id = pickerSlide; val elementId = pickerElement
        if (uris.isNotEmpty() && id != null) model.edit {
            val existing = read().elements.find { it.id == elementId && it.slideId == id }
            if (read().slides.any { it.id == id }) {
                try {
                    val project = read().slides.first { it.id == id }.projectId
                    val paths = uris.map { uri ->
                        val path = importImage(uri); library.addResource(project, displayName(uri), "image", "image", path); path
                    }
                    val frames = if (existing == null) paths else (existing.media.frames.ifEmpty { listOfNotNull(existing.image) } + paths)
                    val element = existing ?: Element(0, id, "image", "", width = .4f, height = .4f)
                    saveElement(element.copy(image = frames.first(), resourceId = read().resources.firstOrNull { it.projectId == project && it.path == frames.first() }?.id, media = element.media.copy(type = "slideshow", frames = frames)))
                } catch (e: Exception) { cleanImages(); throw e }
            }
        }
    }
    fun importSequence(element: Long? = null) {
        pickerSlide = slideId; pickerElement = element
        sequencePicker.launch(arrayOf("image/png", "image/webp", "image/jpeg"))
    }
    fun back() {
        when { playing -> playing = false; showLibrary -> showLibrary = false; slideId != null -> slideId = null; else -> projectId = null }
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
                Text(if (showLibrary) "Biblioteca · ${project?.name ?: ""}" else slide?.name ?: project?.name ?: "TaleFrame", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(12.dp), maxLines = 1)
                if (project != null && !showLibrary) TextButton(onClick = { showLibrary = true }) { Text("Biblioteca") }
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
            } else if (showLibrary) {
                LibraryScreen(story, requireNotNull(projectId), Modifier.weight(1f), edit = model::edit,
                    import = { type, category -> importLibrary(type, category) },
                    replace = { r -> importLibrary(r.type, r.category, r) }, use = { r ->
                        resourceUseId = r.id; resourceUseSlide = slideId
                        resourceUseMode = if (r.type == "audio") "audio" else if (r.category == "background") "background" else "element"
                    })
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
                        if (selected.kind == "image" && selected.characterId != null) TextButton(onClick = { changingExpressionId = selected.id; expressionCharacterId = selected.characterId }) { Text("Cambiar expresión") }
                        if (selected.kind in listOf("text", "button")) {
                            TextButton(onClick = { savePresetElement = selected; savePresetKind = if (selected.kind == "button") "button" else "dialog" }) { Text("Guardar como preset") }
                            if (selected.kind == "button") TextButton(onClick = { savePresetElement = selected; savePresetKind = "action" }) { Text("Guardar acción como preset") }
                        }
                        TextButton(onClick = { model.edit { toggleLock(selected.id) } }) { Text(if (selected.locked) "Desbloquear" else "Bloquear") }
                        TextButton(onClick = { model.edit { duplicateElement(selected.id) } }) { Text("Duplicar") }
                        TextButton(onClick = { model.edit { layer(selected.id, true) } }) { Text("Al frente") }
                        TextButton(onClick = { model.edit { layer(selected.id, false) } }) { Text("Atrás") }
                        TextButton(onClick = { deleteRequest = DeleteRequest("elements", selected.id, "elemento") }) { Text("Eliminar") }
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { showBackground = true }) { Text("Fondo") }
                    TextButton(onClick = { showCharacters = true }) { Text("+ Personaje") }
                    TextButton(onClick = { showDialogs = true }) { Text("+ Diálogo") }
                    TextButton(onClick = { elementRequest = Element(0, slide.id, "text", "", y = .15f, width = .6f, height = .18f) }) { Text("+ Texto") }
                    TextButton(onClick = { elementRequest = Element(0, slide.id, "button", "", x = .1f + (story.elements.count { it.slideId == slide.id && it.kind == "button" } % 3) * .3f,
                        y = .7f, backgroundColor = 0xFF6750A4.toInt(), textColor = -1, width = .28f, height = .1f) }) { Text("+ Botón") }
                    TextButton(onClick = { importImage("element") }) { Text("+ Imagen") }
                    TextButton(onClick = { importImage("element", "gif") }) { Text("+ GIF") }
                    TextButton(onClick = { importImage("element", "video") }) { Text("+ Video") }
                    TextButton(onClick = { importSequence() }) { Text("+ Secuencia") }
                    TextButton(onClick = { showBehavior = true }) { Text("Audio / Tiempo") }
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
                        TextButton(onClick = { selectedId = e.id; nameRequest = null }) { Text("${if (e.locked) "🔒 " else ""}${if (e.kind == "image") e.sourceName.ifBlank { "Imagen ${e.id}" } else e.text}") }
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
    elementRequest?.let { e -> ElementDialog(e, slides, story.elements, { elementRequest = null }, { updated -> model.edit { editElement(e, updated) }; elementRequest = null },
        replace = { elementRequest = null; importImage("replace", e.media.type.takeIf { it != "slideshow" } ?: "image", e.id) },
        addFrames = { importSequence(e.id) }, presets = story.presets.filter { it.projectId == projectId }) }
    if (showBackground && slide != null) BackgroundDialog(slide, { showBackground = false },
        save = { s -> model.edit { editBackground(slide, s) }; showBackground = false }, import = { showBackground = false; importImage("background") },
        importGif = { showBackground = false; importImage("background", "gif") },
        importVideo = { showBackground = false; importImage("background", "video") })
    if (showBehavior && slide != null) SlideBehaviorDialog(slide, slides, story.elements, { showBehavior = false },
        save = { updated -> model.edit { editBackground(slide, updated) }; showBehavior = false },
        importAudio = { showBehavior = false; importImage("audio", "audio") }, actionPresets = story.presets.filter { it.projectId == projectId && it.kind == "action" })
    if (showReference && slide != null) CalcoDialog(slides.filter { it.id != slide.id }, story.elements, referenceId, referenceOpacity,
        { showReference = false }, { id, opacity ->
            referenceId = id; referenceOpacity = opacity; showReference = false
            preferences.edit { putLong("calco_slide_$slideId", id ?: 0); putFloat("calco_opacity_$slideId", opacity) }
        })
    if (showCharacters && projectId != null) CharacterPicker(story, requireNotNull(projectId), { showCharacters = false }, { c ->
        showCharacters = false; expressionCharacterId = c.id; changingExpressionId = null
    })
    story.characters.find { it.id == expressionCharacterId }?.let { c ->
        ExpressionPicker(c, story.expressions.filter { it.characterId == c.id }, story.resources.filter { it.projectId == c.projectId },
            { expressionCharacterId = null; changingExpressionId = null }, { ids ->
                val changing = changingExpressionId; val currentSlide = slideId
                if (changing != null) model.edit { library.changeExpression(changing, ids.first()) }
                else if (currentSlide != null) model.edit { library.insertCharacter(currentSlide, ids) }
                expressionCharacterId = null; changingExpressionId = null
            }, allowSequence = changingExpressionId == null)
    }
    if (showDialogs && projectId != null) DialogPicker(story, requireNotNull(projectId), { showDialogs = false }, { character, preset ->
        val currentSlide = slideId
        if (currentSlide != null) model.edit { library.insertDialog(currentSlide, character, preset) }
        showDialogs = false
    })
    story.resources.find { it.id == resourceUseId }?.let { r ->
        AlertDialog(onDismissRequest = { resourceUseId = null }, title = { Text("Usar ${r.name}") }, text = {
            Column {
                if (r.type != "audio") Row {
                    FilterChip(resourceUseMode == "element", { resourceUseMode = "element" }, label = { Text("Elemento") })
                    FilterChip(resourceUseMode == "background", { resourceUseMode = "background" }, label = { Text("Fondo") })
                } else Text("Audio de la lámina")
                DestinationPicker(slides, story.elements, resourceUseSlide, { resourceUseSlide = it })
            }
        }, confirmButton = { TextButton(onClick = {
            val destination = resourceUseSlide; val mode = resourceUseMode
            if (destination != null) model.edit { library.insertResource(destination, r.id, mode) }
            resourceUseId = null; showLibrary = false
            if (destination != null) slideId = destination
        }, enabled = resourceUseSlide != null) { Text("Usar en lámina") } }, dismissButton = { TextButton(onClick = { resourceUseId = null }) { Text("Cancelar") } })
    }
    savePresetElement?.let { element -> NameDialog("Guardar preset", "", { savePresetElement = null }, { name ->
        val project = projectId; val kind = savePresetKind
        if (project != null) model.edit { library.savePreset(Preset(0, project, name, kind, element.textColor, element.backgroundColor, element.style, element.transition)) }
        savePresetElement = null
    }) }
    error?.let { message -> AlertDialog(onDismissRequest = { model.clearError() }, title = { Text("No se pudo completar el cambio") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.clearError() }) { Text("Aceptar") } }) }
}

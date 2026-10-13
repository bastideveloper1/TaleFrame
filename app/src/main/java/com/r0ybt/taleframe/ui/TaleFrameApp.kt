package com.r0ybt.taleframe.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.delay
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.R
import com.r0ybt.taleframe.state.StoryViewModel

private data class NameRequest(val table: String, val id: Long?, val parent: Long?, val initial: String)
private data class DeleteRequest(val table: String, val id: Long, val name: String)

@Composable
fun TaleFrameApp(model: StoryViewModel) {
    val story by model.story.collectAsState()
    val ready by model.ready.collectAsState()
    val undo by model.undoState.collectAsState()
    val error by model.error.collectAsState()
    val backup by model.backup.collectAsState()
    val quickScript by model.quickScript.collectAsState()
    val quickBusy by model.quickBusy.collectAsState()
    var showQuickScript by rememberSaveable { mutableStateOf(false) }
    var projectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var slideId by rememberSaveable { mutableStateOf<Long?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var playSlideId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedId by rememberSaveable(slideId) { mutableStateOf<Long?>(null) }
    var actions by rememberSaveable { mutableStateOf(false) }
    var movingBackground by rememberSaveable(slideId) { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("editor", 0) }
    val calcoPreferences = remember(preferences) { CalcoPreferences(preferences) }
    var calcoConfig by remember(projectId, slideId) { mutableStateOf(calcoPreferences.read(projectId, slideId)) }
    var albumSize by rememberSaveable { mutableStateOf(preferences.getString("album_size", "Mediana") ?: "Mediana") }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    var elementRequest by remember { mutableStateOf<Element?>(null) }
    var showSettings by remember {mutableStateOf(false)}
    var panelLibraryId by remember {mutableStateOf<Long?>(null)}
    var playbackSettingsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var coverAdjustmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var coverPicker by remember {mutableStateOf(false)}
    var librarySection by rememberSaveable {mutableStateOf("images")}
    var savingTemplate by remember {mutableStateOf(false)}
    var applyingTemplate by remember {mutableStateOf<SlideTemplate?>(null)}
    val albumState=rememberSaveable(projectId,saver=LazyGridState.Saver) {LazyGridState()}
    var quickMenu by remember {mutableStateOf<Element?>(null)}
    var quickText by remember {mutableStateOf<Element?>(null)}
    var quickDestination by remember {mutableStateOf<Element?>(null)}
    var quickTransition by remember {mutableStateOf<Element?>(null)}
    var baseOffer by remember {mutableStateOf(false)}
    var swipeIndicator by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(swipeIndicator) {if(swipeIndicator!=null) {delay(1100);swipeIndicator=null}}
    var playNotice by remember {mutableStateOf<String?>(null)}
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
    val calcoSource = calcoReference(calcoConfig, story.slides.find { it.id == slideId }, slides)
    val slide = slides.find { it.id == slideId }
    val editorSession = slide?.id?.takeUnless {showLibrary || playing}
    LaunchedEffect(projectId,editorSession) {model.setEditorSession(editorSession)}
    var exportProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ProjectBackup.MIME)) { uri ->
        exportProjectId?.let { id -> if (uri != null) model.exportProject(id, uri) }
        exportProjectId = null
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importProject(uri) }
    LaunchedEffect(backup.imported, story.projects) {
        backup.imported?.takeIf { id -> story.projects.any { it.id == id } }?.let { id ->
            projectId = id; slideId = null; showLibrary = false; playing = false; model.dismissBackup()
        }
    }
    backup.progress?.let { phase -> AlertDialog(onDismissRequest = {}, title = { Text("Respaldo del proyecto") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(phase) } }, confirmButton = {}) }
    backup.copyName?.let { copyName -> AlertDialog(onDismissRequest = model::dismissBackup, title = { Text("Ya existe un proyecto con este nombre") },
        text = { Text("Se importará como «${copyName}». Tus proyectos existentes se conservan.") },
        confirmButton = { TextButton(onClick = model::confirmImportCopy) { Text("Importar como copia") } },
        dismissButton = { TextButton(onClick = model::dismissBackup) { Text("Cancelar") } }) }
    backup.exported?.let { uri -> AlertDialog(onDismissRequest = model::dismissBackup, title = { Text("Proyecto exportado") },
        text = { Text("El archivo .taleframe permite restaurar tu proyecto en otra instalación de TaleFrame.") },
        confirmButton = { TextButton(onClick = {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = ProjectBackup.MIME; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("Proyecto TaleFrame", uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Compartir proyecto"))
            } catch (_: android.content.ActivityNotFoundException) { }
        }) { Text("Compartir") } }, dismissButton = { TextButton(onClick = model::dismissBackup) { Text("Listo") } }) }
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
                    else -> { val size = initialGifSize(path, type); saveElement(Element(0, id, "image", "", image = path, width = size.first, height = size.second, resourceId = resource, media = MediaOptions(type = type))) }
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
    LaunchedEffect(showQuickScript, projectId) {
        if (showQuickScript) projectId?.let(model::openQuickScript)
    }
    fun back() {
        when { showQuickScript -> showQuickScript = false; playing -> playing = false; showLibrary -> showLibrary = false; slideId != null -> slideId = null; else -> projectId = null }
    }
    fun play(fromCurrent:Boolean=false) {
        val p=project ?: return
        val first=if(fromCurrent) slide else playbackStart(p,slides)
        if(first==null) {playNotice="No hay láminas disponibles para reproducir.";return}
        if(p.skipDrafts && first.draft) {playNotice="Esta lámina está en borrador. Desactiva «Omitir láminas en borrador» para probarla.";return}
        playSlideId=first.id;playing=true
    }
    BackHandler(projectId != null || playing) { back() }
    Scaffold { padding ->
        if (playing) {
            Player(slides.find { it.id == playSlideId }, story.elements, Modifier.padding(padding), blockedTargets=slides.filter {project?.skipDrafts==true && it.draft}.map {it.id}.toSet(), leave={playing=false},slides=slides,projectPlayback=project?.playback ?: ProjectPlayback()) { target ->
                if (slides.any { it.id == target }) playSlideId = target
            }
        } else Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (projectId != null) TextButton(onClick = { back() },modifier=Modifier.semantics {contentDescription="Volver"}) { Text("‹") }
                if (projectId == null) Spacer(Modifier.weight(1f))
                else Text(if (showQuickScript) "Guion rápido · ${project?.name ?: ""}" else if (showLibrary) "Biblioteca · ${project?.name ?: ""}" else slide?.name ?: project?.name ?: "", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(12.dp), maxLines = 1,overflow=TextOverflow.Ellipsis)
                if(projectId==null) TextButton(onClick={backupImporter.launch(arrayOf("*/*"))}) {Text("Importar")}
                if(projectId==null) TextButton(onClick={showSettings=true}) {Text("Tema")}
                if (slide != null && !showLibrary) TextButton(onClick={model.undo(slide.id)},enabled=undo.slideId==slide.id && undo.count>0) {Text("Deshacer")}
                if (project != null && !showLibrary && !showQuickScript) TextButton(onClick = { librarySection="images";showLibrary = true }) { Text("Biblioteca") }
            }
            if (project != null && !showLibrary && !showQuickScript) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (slide != null) {
                    FilterChip(selected = calcoConfig.enabled && calcoConfig.mode != CalcoMode.NONE,
                        onClick = { val config = toggleCalco(calcoConfig); calcoPreferences.save(slide.projectId, config); calcoConfig = config },
                        label = { Text("Calco") })
                    TextButton(onClick = { play(true) }, modifier = Modifier.weight(1f)) { Text("▶ Reproducir desde aquí", style = MaterialTheme.typography.labelMedium) }
                }
                if (slides.isNotEmpty()) TextButton(onClick = { play() }, modifier = Modifier.weight(1f)) { Text("▶ Reproducir desde inicio", style = MaterialTheme.typography.labelMedium) }
            }
            if (!ready) Text("Abriendo proyectos…", Modifier.padding(24.dp))
            else if (projectId == null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.taleframe_banner), contentDescription = "TaleFrame",
                        modifier = Modifier.widthIn(max = 312.dp).fillMaxWidth().aspectRatio(3f),
                        contentScale = ContentScale.Fit)
                }
                Text("Tus historias · en este dispositivo", Modifier.padding(horizontal = 24.dp))
                Button(onClick = { nameRequest = NameRequest("projects", null, null, "") }, modifier = Modifier.padding(20.dp)) { Text("+ Crear proyecto") }
                if (story.projects.isEmpty()) Text("Crea tu primera historia para comenzar.", Modifier.padding(24.dp))
                ProjectLibrary(story, Modifier.weight(1f), open = { projectId = it.id },
                    rename = { nameRequest = NameRequest("projects", it.id, null, it.name) },
                    delete = { deleteRequest = DeleteRequest("projects", it.id, it.name) },
                    export = { exportProjectId = it.id; backupExporter.launch(ProjectBackup.suggestedFilename(it.name)) },
                    adjust = { coverAdjustmentId = it.id }, configurePlayback = { playbackSettingsId = it.id })
            } else if (showQuickScript) {
                val quickState = quickScript?.takeIf { it.projectId == projectId }
                if (quickState == null) Text("Abriendo guion…", Modifier.padding(24.dp))
                else QuickScriptScreen(quickState, quickBusy, Modifier.weight(1f),
                    saveInput = { model.saveQuickScriptInput(quickState.projectId, it) },
                    addText = { model.addQuickScriptText(quickState.projectId, it) },
                    createSlide = { draft, start, end, insertion -> model.createQuickScriptSlide(quickState.projectId, draft, start, end, insertion) },
                    undo = { model.undoQuickScript(quickState.projectId) }, slides = slides, elements = story.elements)
            } else if (showLibrary) {
                LibraryScreen(story, requireNotNull(projectId), Modifier.weight(1f), edit = model::edit,
                    import = { type, category -> importLibrary(type, category) },
                    replace = { r -> importLibrary(r.type, r.category, r) }, use = { r ->
                        resourceUseId = r.id; resourceUseSlide = slideId
                        resourceUseMode = if (r.type == "audio") "audio" else if (r.category == "background") "background" else "element"
                    },initialSection=librarySection,applyTemplate={applyingTemplate=it},createComposition={showLibrary=false;nameRequest=NameRequest("slides",null,projectId,"Composición")})
            } else if (slide == null) {
                if(project!=null) ProjectEntry(project,story,slides,preferences.getLong("last_slide_${project.id}",0L),
                    cover={coverPicker=true},resume={id->slideId=id},library={section->librarySection=section;showLibrary=true},play={play()},skip={value->model.edit {setSkipDrafts(project.id,value)}},configurePlayback={playbackSettingsId=project.id})
                if(project!=null) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(project.automaticBaseNavigation,{enabled->if(enabled) baseOffer=true else model.edit {setAutomaticBaseNavigation(project.id,false)}})
                    Text("Navegación base automática",style=MaterialTheme.typography.bodySmall)
                }
                Text("Álbum de láminas", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { nameRequest = NameRequest("slides", null, projectId, "Lámina ${slides.size + 1}") }) { Text("+ Crear lámina") }
                    TextButton(onClick = { showQuickScript = true }) { Text("Guion rápido") }
                    listOf("Grande", "Mediana", "Pequeña").forEach { size ->
                        FilterChip(albumSize == size, onClick = { albumSize = size; preferences.edit { putString("album_size", size) } }, label = { Text(size) })
                    }
                }
                if (slides.isEmpty()) Text("Añade la primera lámina de tu historia.", Modifier.padding(24.dp))
                Album(slides, story.elements, albumSize, Modifier.weight(1f),
                    open = { slideId = it.id; preferences.edit {putLong("last_slide_$projectId",it.id)} }, rename = { nameRequest = NameRequest("slides", it.id, null, it.name) },
                    delete = { deleteRequest = DeleteRequest("slides", it.id, it.name) },
                    duplicate = { model.edit { duplicateSlide(it.id) } }, reorder = { s, delta -> model.edit { reorderSlide(s.id, delta) } },initialId=project?.let {initialSlide(it,slides)?.id},setInitial={s->model.edit {setInitialSlide(s.projectId,s.id)}},setDraft={s->model.edit {setDraft(s.id,!s.draft)}},state=albumState,moveTo={s,index->model.edit {moveSlideTo(s.id,index)}})
            } else {
                SlideCanvas(slide, story.elements.filter { it.slideId == slide.id }, Modifier.weight(1f).fillMaxWidth(), editing = true,
                    undoRevision=undo.revision,onContext={quickMenu=it},positionIndicator=swipeIndicator,onSwipe={delta ->
                        val next=slides.getOrNull(slides.indexOf(slide)+delta)
                        if(next!=null) {slideId=next.id;preferences.edit {putLong("last_slide_$projectId",next.id)};swipeIndicator="${slides.indexOf(next)+1} / ${slides.size}"}
                    }, onSelect = { selectedId = it?.id }, onMove = { e -> model.edit { moveElement(e.id, e.x, e.y, e.freePosition) } },
                    onResize = { e -> model.edit { resizeElement(e.id, e.width, e.height, e.x, e.y, e.freePosition) } }, selectedId = selectedId,
                    showActions = actions, destinations = slides, reference = calcoSource,
                    referenceElements = story.elements.filter { it.slideId == calcoSource?.id }, referenceOpacity = calcoConfig.opacity,
                    movingBackground = movingBackground, onBackgroundMove = { s -> model.edit {
                        val current = read().slides.find { it.id == s.id }
                        if (current != null && !current.backgroundLocked) saveSlide(current.copy(backgroundX = s.backgroundX, backgroundY = s.backgroundY))
                    } })
                NarrativeScriptSection(slide) { text ->
                    model.edit { setNarrativeScript(slide.id, text.takeUnless { it.isEmpty() }) }
                }
                EditorToolbar(listOf(
                    EditorTool("background","Fondo") {showBackground=true},
                    EditorTool("character","+ Personaje") {showCharacters=true},
                    EditorTool("dialog","+ Diálogo") {showDialogs=true},
                    EditorTool("text","+ Texto") {elementRequest=Element(0,slide.id,"text","",y=.15f,width=.6f,height=.18f)},
                    EditorTool("button","+ Botón") {elementRequest=Element(0,slide.id,"button","",useDefaultTransition=true,x=.1f+(story.elements.count {it.slideId==slide.id && it.kind=="button"}%3)*.3f,y=.7f,backgroundColor=0xFF8E435F.toInt(),textColor=-1,width=.28f,height=.1f)},
                    EditorTool("image","+ Imagen") {importImage("element")},
                    EditorTool("gif","+ GIF") {importImage("element","gif")},
                    EditorTool("video","+ Video") {importImage("element","video")},
                    EditorTool("sequence","+ Secuencia") {importSequence()},
                    EditorTool("time","Audio / Tiempo") {showBehavior=true},
                    EditorTool("actions",if(actions) "Ocultar acciones" else "Ver acciones") {actions=!actions},
                    EditorTool("reference","Calco") {showReference=true},
                    EditorTool("move-background",if(movingBackground) "Terminar fondo" else "Mover fondo",slide.image!=null && slide.backgroundMode=="manual" && !slide.backgroundLocked) {movingBackground=!movingBackground},
                    EditorTool("deselect","Deseleccionar") {selectedId=null;movingBackground=false},
                    EditorTool("layers","Capas") {nameRequest=NameRequest("layers",null,null,"")},
                    EditorTool("panel","+ Panel") {model.edit {saveElement(Element(0,slide.id,"image","",width=.45f,height=.4f,backgroundColor=0xFFF4E8EB.toInt(),panel=PanelOptions()))}},
                    EditorTool("draft",if(slide.draft) "Marcar completa" else "Marcar borrador") {model.edit {setDraft(slide.id,!slide.draft)}}
                ))
            }
        }
    }
    quickMenu?.let {original ->
        val e=story.elements.find {it.id==original.id} ?: original
        AlertDialog(onDismissRequest={quickMenu=null},title={Text(if(e.kind=="image") e.sourceName.ifBlank {if(e.panel!=null) "Panel" else "Imagen"} else e.text.take(35))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            if(e.kind!="image") TextButton(onClick={quickMenu=null;quickText=e}){Text("Editar texto")}
            if(e.kind=="button") {
                TextButton(onClick={quickMenu=null;quickDestination=e}){Text("Destino")}
                TextButton(onClick={quickMenu=null;quickTransition=e}){Text("Transición")}
            }
            if(e.kind=="image") TextButton(onClick={quickMenu=null;if(e.characterId!=null) {changingExpressionId=e.id;expressionCharacterId=e.characterId} else importImage("replace",e.media.type.takeIf {it!="slideshow"} ?: "image",e.id)}){Text(if(e.characterId!=null) "Cambiar expresión" else "Cambiar recurso")}
            TextButton(onClick={quickMenu=null;elementRequest=e}){Text("Propiedades")}
            TextButton(onClick={quickMenu=null;model.edit {layer(e.id,true)}}){Text("Traer al frente")}
            TextButton(onClick={quickMenu=null;model.edit {layer(e.id,false)}}){Text("Enviar atrás")}
            if(e.kind in listOf("text","button")) {
                TextButton(onClick={quickMenu=null;savePresetElement=e;savePresetKind=if(e.kind=="button") "button" else "dialog"}){Text("Guardar como preset")}
                if(e.kind=="button") TextButton(onClick={quickMenu=null;savePresetElement=e;savePresetKind="action"}){Text("Guardar acción como preset")}
            }
            TextButton(onClick={quickMenu=null;model.edit {duplicateElement(e.id)}}){Text("Duplicar")}
            TextButton(onClick={quickMenu=null;model.edit {toggleLock(e.id)}}){Text(if(e.locked) "Desbloquear" else "Bloquear")}
        }},confirmButton={TextButton(onClick={quickMenu=null;deleteRequest=DeleteRequest("elements",e.id,"elemento")}){Text("Eliminar")}},dismissButton={TextButton(onClick={quickMenu=null}){Text("Cerrar")}})
    }
    quickText?.let {e ->
        QuickTextDialog(e.text,{quickText=null}) {text->model.edit {editElement(e,e.copy(text=text))};quickText=null}
    }
    quickDestination?.let {e ->DestinationDialog(slides,story.elements,e.targetId,e.slideId,{quickDestination=null}) {id->model.edit {editElement(e,e.copy(targetId=id))};quickDestination=null}}
    quickTransition?.let {e ->TransitionDialog(e.transition,{quickTransition=null},useDefault=e.useDefaultTransition,
        defaultEffect=effectiveTransition(project?.playback ?: ProjectPlayback(), slides.find {it.id==e.slideId} ?: Slide(0,0,""))) {effect,inherit->model.edit {editElement(e,e.copy(transition=effect,useDefaultTransition=inherit))};quickTransition=null}}
    if(baseOffer && project!=null) AlertDialog(onDismissRequest={baseOffer=false},title={Text("Generar navegación base")},text={Text("Se sincronizarán Anterior/Siguiente según el orden del álbum. Los botones narrativos conservarán sus destinos. Al desactivar, los botones creados permanecen sin actualizarse automáticamente.")},confirmButton={TextButton(onClick={model.edit {setAutomaticBaseNavigation(project.id,true)};baseOffer=false}){Text("Generar y activar")}},dismissButton={TextButton(onClick={baseOffer=false}){Text("Cancelar")}})
    nameRequest?.let { request ->
        if (request.table == "layers") {
            AlertDialog(onDismissRequest = { nameRequest = null }, title = { Text("Capas · arriba primero") }, text = {
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
        val relatedSlides = if (request.table == "slides") {
            val sourceIds = story.elements.filter { it.kind == "button" && it.targetId == request.id }
                .map { it.slideId }.toSet()
            story.slides.filter { it.id != request.id && (it.id in sourceIds || (it.autoEnabled && it.autoTargetId == request.id)) }
        } else emptyList()
        val contentMaxHeight = (LocalConfiguration.current.screenHeightDp * .4f).dp
        AlertDialog(onDismissRequest = { deleteRequest = null }, title = {
            Text("Eliminar ${request.name}", maxLines = if (request.table == "slides") 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        },
            text = {
                Column(Modifier.heightIn(max = contentMaxHeight)) {
                    Text(if (request.table == "projects") "Se eliminarán todas sus láminas y elementos." else if (request.table == "slides") "Se eliminarán sus elementos. Los botones que apuntan aquí quedarán sin destino." else "Se eliminará el elemento seleccionado.")
                    if (relatedSlides.isNotEmpty()) {
                        Text("Láminas relacionadas (${relatedSlides.size}):", Modifier.padding(top = 12.dp, bottom = 4.dp))
                        // About five short names; shrink further when the dialog has less space.
                        Column(Modifier.weight(1f, fill = false).heightIn(max = 200.dp)
                            .fillMaxWidth().verticalScroll(rememberScrollState())) {
                            relatedSlides.forEach { related ->
                                Text(related.name, Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { model.edit { delete(request.table, request.id) }; deleteRequest = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteRequest = null }) { Text("Cancelar") } })
    }
    elementRequest?.let { e -> ElementDialog(e, slides, story.elements, { elementRequest = null }, { updated -> model.edit { editElement(e, updated) }; elementRequest = null },
        replace = { elementRequest = null; importImage("replace", e.media.type.takeIf { it != "slideshow" } ?: "image", e.id) },
        addFrames = { importSequence(e.id) }, presets = story.presets.filter { it.projectId == projectId }, projectPlayback = project?.playback ?: ProjectPlayback(), expressions = story.expressions,assignLibrary={panelLibraryId=e.id;elementRequest=null}) }
    if (showBackground && slide != null) BackgroundDialog(slide, { showBackground = false },
        save = { s -> model.edit { editBackground(slide, s) }; showBackground = false }, import = { showBackground = false; importImage("background") },
        importGif = { showBackground = false; importImage("background", "gif") },
        importVideo = { showBackground = false; importImage("background", "video") })
    if (showBehavior && slide != null) SlideBehaviorDialog(slide, slides, story.elements, { showBehavior = false },
        save = { updated -> model.edit { editBackground(slide, updated) }; showBehavior = false },
        importAudio = { showBehavior = false; importImage("audio", "audio") }, actionPresets = story.presets.filter { it.projectId == projectId && it.kind == "action" }, projectPlayback = project?.playback ?: ProjectPlayback())
    if (showReference && slide != null) CalcoDialog(slides.filter { it.id != slide.id }, story.elements, calcoConfig,
        calcoReference(calcoConfig.copy(mode = CalcoMode.PREVIOUS, enabled = true), slide, slides),
        { showReference = false }, { config ->
            calcoPreferences.save(slide.projectId, config.copy(enabled = true))
            calcoConfig = config.copy(enabled = true); showReference = false
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
    panelLibraryId?.let {id->ResourcePicker(story.resources.filter {it.projectId==projectId && it.type=="image"},"Imagen del panel",{panelLibraryId=null},{r->if(r!=null) model.edit {val e=read().elements.find {it.id==id};if(e!=null) saveElement(e.copy(image=r.path,resourceId=r.id,media=MediaOptions()))};panelLibraryId=null})}
    story.projects.find { it.id == playbackSettingsId }?.let { playbackProject ->
        ProjectPlaybackDialog(playbackProject, { playbackSettingsId = null }) { settings ->
            model.edit { setProjectPlayback(playbackProject.id, settings) }; playbackSettingsId = null
        }
    }
    story.projects.find { it.id == coverAdjustmentId }?.let { coverProject ->
        CoverAdjustmentDialog(coverProject, story, { coverAdjustmentId = null }) { scale, x, y ->
            model.edit { setCoverAdjustment(coverProject.id, scale, x, y) }; coverAdjustmentId = null
        }
    }
    if(coverPicker && project!=null) ResourcePicker(story.resources.filter {it.projectId==project.id && it.type=="image"},"Portada del proyecto",{coverPicker=false},{r->model.edit {setCover(project.id,r?.id)};coverPicker=false},allowNone=true)
    if(savingTemplate && slide!=null) NameDialog("Guardar como plantilla",slide.name,{savingTemplate=false},{name->model.edit {templates.saveSlide(slide.id,name)};savingTemplate=false})
    applyingTemplate?.let {t->NameDialog("Nueva lámina desde ${t.name}","Lámina ${slides.size+1}",{applyingTemplate=null},{name->val p=projectId;if(p!=null) model.edit {templates.createSlide(p,name,t.id)};applyingTemplate=null;showLibrary=false;slideId=null})}
    if(showSettings) AlertDialog(onDismissRequest={showSettings=false},title={Text("Apariencia de TaleFrame")},text={Column {
        listOf("system" to "Seguir sistema","light" to "Rosa claro","dark" to "Oscuro").forEach {(key,label)->TextButton(onClick={preferences.edit {putString("theme",key)}}){Text(label)}}
    }},confirmButton={TextButton(onClick={showSettings=false}){Text("Cerrar")}})
    playNotice?.let {notice->AlertDialog(onDismissRequest={playNotice=null},title={Text("Reproducción")},text={Text(notice)},confirmButton={TextButton(onClick={playNotice=null}){Text("Entendido")}})}
    error?.let { message -> AlertDialog(onDismissRequest = { model.clearError() }, title = { Text("No se pudo completar el cambio") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.clearError() }) { Text("Aceptar") } }) }
}

@Composable
private fun NarrativeScriptSection(slide: Slide, onChange: (String) -> Unit) {
    var expanded by rememberSaveable(slide.id) { mutableStateOf(false) }
    var text by rememberSaveable(slide.id) { mutableStateOf(slide.narrativeScript.orEmpty()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Guion ▴" else "Guion ▾")
        }
        if (expanded) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Guion narrativo privado") },
                minLines = 3,
                maxLines = 6
            )
        }
    }
}

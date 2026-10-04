package com.r0ybt.taleframe.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    var elementRequest by remember { mutableStateOf<Element?>(null) }
    var showBackground by remember { mutableStateOf(false) }
    val project = story.projects.find { it.id == projectId }
    val slides = story.slides.filter { it.projectId == projectId }
    val slide = slides.find { it.id == slideId }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val current = slide
        if(uri != null && current != null) model.edit { background(current.id,current.color,importImage(uri)) }
    }
    fun back() {
        when { playing -> playing = false; slideId != null -> slideId = null; else -> projectId = null }
    }
    fun play() { playSlideId = slides.firstOrNull()?.id; playing = playSlideId != null }
    BackHandler(projectId != null || playing) { back() }
    Scaffold { padding ->
        if(playing) {
            Player(slides.find { it.id == playSlideId } ?: slides.firstOrNull(), story.elements, Modifier.padding(padding)) { target ->
                if(slides.any { it.id == target }) playSlideId = target
            }
        } else Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if(projectId != null) TextButton(onClick = { back() }) { Text("‹ Volver") }
                Text(if(slide != null) slide.name else project?.name ?: "TaleFrame", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(12.dp), maxLines = 1)
                if(slides.isNotEmpty()) TextButton(onClick = { play() }) { Text("▶ Play") }
            }
            if(!ready) Box(Modifier.fillMaxSize()) { Text("Abriendo proyectos…", Modifier.padding(24.dp)) }
            else if(projectId == null) {
                Text("Tus historias · en este dispositivo", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { nameRequest = NameRequest("projects",null,null,"") }, modifier = Modifier.padding(20.dp)) { Text("+ Crear proyecto") }
                if(story.projects.isEmpty()) Text("Crea tu primera historia para comenzar.", Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(story.projects, key = { it.id }) { p ->
                        StoryCard(p.name, "${story.slides.count { it.projectId == p.id }} láminas", { projectId = p.id },
                            { nameRequest = NameRequest("projects",p.id,null,p.name) }, { deleteRequest = DeleteRequest("projects",p.id,p.name) })
                    }
                }
            } else if(slide == null) {
                Button(onClick = { nameRequest = NameRequest("slides",null,projectId,"Lámina ${slides.size + 1}") }, modifier = Modifier.padding(20.dp)) { Text("+ Crear lámina") }
                if(slides.isEmpty()) Text("Añade la primera lámina de tu historia.", Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(slides, key = { it.id }) { s ->
                        StoryCard(s.name, if(s.id == slides.firstOrNull()?.id) "Inicio de la historia" else "Toca para diseñar", { slideId = s.id },
                            { nameRequest = NameRequest("slides",s.id,null,s.name) }, { deleteRequest = DeleteRequest("slides",s.id,s.name) })
                    }
                }
            } else {
                SlideCanvas(slide,story.elements.filter { it.slideId == slide.id }, Modifier.weight(1f).fillMaxWidth(), editing = true,
                    onSelect = { elementRequest = it }, onMove = { model.edit { saveElement(it) } })
                Text("Toca para editar · mantén pulsado y arrastra para mover", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { showBackground = true }) { Text("Fondo") }
                    TextButton(onClick = { elementRequest = Element(0,slide.id,"text","",x=.1f,y=.15f + (story.elements.count { it.slideId == slide.id && it.kind == "text" } % 4) * .15f) }) { Text("+ Texto") }
                    TextButton(onClick = { elementRequest = Element(0,slide.id,"button","",x=.1f + (story.elements.count { it.slideId == slide.id && it.kind == "button" } % 3) * .3f,y=.7f,backgroundColor=0xFF6750A4.toInt(),textColor=-1) }) { Text("+ Botón") }
                    TextButton(onClick = { play() }) { Text("▶") }
                }
            }
        }
    }
    nameRequest?.let { request ->
        NameDialog(if(request.id == null) "Crear" else "Renombrar", request.initial, { nameRequest = null }) { name ->
            model.edit { if(request.id != null) rename(request.table,request.id,name) else if(request.table == "projects") createProject(name) else createSlide(requireNotNull(request.parent),name) }
            nameRequest = null
        }
    }
    deleteRequest?.let { request ->
        AlertDialog(onDismissRequest = { deleteRequest = null }, title = { Text("Eliminar ${request.name}") },
            text = { Text(if(request.table == "projects") "Se eliminarán todas sus láminas y elementos. Esta acción no se puede deshacer." else "Se eliminarán sus elementos. Los botones que apuntan aquí quedarán sin destino.") },
            confirmButton = { TextButton(onClick = { model.edit { delete(request.table,request.id) }; deleteRequest = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteRequest = null }) { Text("Cancelar") } })
    }
    elementRequest?.let { element ->
        ElementDialog(element,slides,{ elementRequest = null }, { updated -> model.edit { saveElement(updated) }; elementRequest = null },
            { model.edit { delete("elements",element.id) }; elementRequest = null })
    }
    if(showBackground && slide != null) {
        AlertDialog(onDismissRequest = { showBackground = false }, title = { Text("Fondo de la lámina") },
            text = { Column { ColorPicker("Color sólido",slide.color) { color -> model.edit { background(slide.id,color,null) }; showBackground = false }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showBackground = false; imagePicker.launch(arrayOf("image/*")) }) { Text("Elegir imagen local") }
                Text("La imagen se copia al almacenamiento privado de TaleFrame.", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = { showBackground = false }) { Text("Cerrar") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { model.clearError() }, title = { Text("No se pudo completar el cambio") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.clearError() }) { Text("Aceptar") } }) }
}

@Composable
private fun StoryCard(name: String, subtitle: String, open: () -> Unit, rename: () -> Unit, delete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open)) {
        Column(Modifier.padding(16.dp)) {
            Text(name,style=MaterialTheme.typography.titleMedium)
            Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row { TextButton(onClick=rename) { Text("Renombrar") }; TextButton(onClick=delete) { Text("Eliminar") } }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=dismiss,title={ Text(title) },text={ OutlinedTextField(name,{ name=it },label={ Text("Nombre") },singleLine=true) },
        confirmButton={ TextButton(onClick={ save(name.trim()) },enabled=name.isNotBlank()) { Text("Guardar") } },dismissButton={ TextButton(onClick=dismiss) { Text("Cancelar") } })
}

@Composable
private fun ElementDialog(element: Element, slides: List<Slide>, dismiss: () -> Unit, save: (Element) -> Unit, delete: () -> Unit) {
    var text by remember { mutableStateOf(element.text) }
    var foreground by remember { mutableIntStateOf(element.textColor) }
    var background by remember { mutableIntStateOf(element.backgroundColor) }
    var target by remember { mutableStateOf(element.targetId) }
    AlertDialog(onDismissRequest=dismiss,title={ Text(if(element.kind == "text") "Cuadro de texto" else "Botón") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text,{text=it},label={ Text("Texto") }, minLines=2, maxLines=5)
            if(element.kind == "text") {
                ColorPicker("Color del texto",foreground) { foreground=it }
                ColorPicker("Color del cuadro",background) { background=it }
            } else {
                Text("Lámina de destino")
                Row(Modifier.fillMaxWidth().clickable { target=null }) { RadioButton(target==null,{target=null}); Text("Sin destino",Modifier.padding(top=12.dp)) }
                slides.forEach { slide -> Row(Modifier.fillMaxWidth().clickable { target=slide.id }) { RadioButton(target==slide.id,{target=slide.id}); Text(slide.name,Modifier.padding(top=12.dp)) } }
            }
            if(element.id != 0L) TextButton(onClick=delete) { Text("Eliminar elemento",color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={ TextButton(onClick={save(element.copy(text=text.trim(),textColor=foreground,backgroundColor=background,targetId=target))},enabled=text.isNotBlank()) { Text("Guardar") } },dismissButton={ TextButton(onClick=dismiss) { Text("Cancelar") } })
}

@Composable
private fun ColorPicker(label: String, selected: Int, pick: (Int) -> Unit) {
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

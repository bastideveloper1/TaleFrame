package com.r0ybt.taleframe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

@Composable
fun TemplatePreview(t:SlideTemplate,modifier:Modifier=Modifier) {
    SlideCanvas(t.slide.copy(id=0),t.elements.mapIndexed {i,e->e.copy(id=-(i+1L),slideId=0)},modifier.aspectRatio(.75f),preview=true)
}
@Composable
fun NewSlideDialog(story:Story,projectId:Long,initialName:String,dismiss:()->Unit,save:(String,Long?)->Unit) {
    var name by remember {mutableStateOf(initialName)}
    var templateId by remember {mutableStateOf<Long?>(null)}
    val templates=includedTemplates()+story.templates.filter {it.projectId==projectId}
    AlertDialog(onDismissRequest=dismiss,title={Text("Nueva lámina")},text={Column {
        OutlinedTextField(name,{name=it},label={Text("Nombre")},singleLine=true)
        Text("Punto de partida editable. Destinos sin asignar.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={templateId=null}) {Text(if(templateId==null) "✓ Vacía" else "Vacía")}
        LazyColumn(Modifier.testTag("new-slide-templates").heightIn(max=300.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(templates,key={it.id}) {t->Card(onClick={templateId=t.id},colors=CardDefaults.cardColors(containerColor=if(templateId==t.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                Row {TemplatePreview(t,Modifier.width(80.dp).height(100.dp));Column(Modifier.padding(8.dp)) {Text("${if(templateId==t.id) "✓ " else ""}${t.name}");Text(if(t.included) "Incluida" else "Tu plantilla",style=MaterialTheme.typography.labelSmall)}}
            }}
        }
    }},confirmButton={TextButton(onClick={save(name.trim(),templateId)},enabled=name.isNotBlank()){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}
@Composable
fun TemplatesLibrary(story:Story,projectId:Long,query:String,edit:(StoryRepository.()->Unit)->Unit,apply:(SlideTemplate)->Unit,create:()->Unit,modifier:Modifier=Modifier) {
    var rename by remember {mutableStateOf<SlideTemplate?>(null)}
    var delete by remember {mutableStateOf<SlideTemplate?>(null)}
    Column(modifier) {
        TextButton(onClick=create){Text("Crear composición para plantilla")}
        Text("Guarda una lámina con «Guardar como plantilla» en el editor.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items((includedTemplates()+story.templates.filter {it.projectId==projectId}).filter {it.name.contains(query,true)},key={it.id}) {t->Card(Modifier.testTag("template-${t.id}")) {
                Row {TemplatePreview(t,Modifier.width(100.dp).height(130.dp));Column(Modifier.weight(1f).padding(8.dp)) {Text(t.name,style=MaterialTheme.typography.titleSmall);Text(if(t.included) "Incluida" else "Tu plantilla",style=MaterialTheme.typography.labelSmall);TextButton(onClick={apply(t)}){Text("Aplicar en nueva lámina")}}}
                Row {TextButton(onClick={edit {templates.duplicate(t.id,projectId)}}){Text("Duplicar plantilla")};if(!t.included) {TextButton(onClick={rename=t}){Text("Renombrar")};TextButton(onClick={delete=t}){Text("Eliminar")}}}
            }}
        }
    }
    rename?.let {t->NameDialog("Renombrar plantilla",t.name,{rename=null},{name->edit {templates.rename(t.id,name)};rename=null})}
    delete?.let {t->AlertDialog(onDismissRequest={delete=null},title={Text("Eliminar ${t.name}")},text={Text("Las láminas creadas desde esta plantilla se conservarán.")},confirmButton={TextButton(onClick={edit {templates.delete(t.id)};delete=null}){Text("Eliminar")}},dismissButton={TextButton(onClick={delete=null}){Text("Cancelar")}})}
}

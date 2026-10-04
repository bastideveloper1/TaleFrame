package com.r0ybt.taleframe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

private data class LibraryDeletion(val table: String, val id: Long, val name: String)
@Composable
fun LibraryScreen(story: Story, projectId: Long, modifier: Modifier = Modifier,
    edit: (StoryRepository.() -> Unit) -> Unit, import: (String, String) -> Unit,
    replace: (Resource) -> Unit, use: (Resource) -> Unit) {
    var section by rememberSaveable(projectId) {mutableStateOf("images")}
    var query by rememberSaveable(projectId) {mutableStateOf("")}
    var onlyUnused by rememberSaveable(projectId) {mutableStateOf(false)}
    var characterId by rememberSaveable(projectId) {mutableStateOf<Long?>(null)}
    var characterRequest by remember {mutableStateOf<Character?>(null)}
    var expressionRequest by remember {mutableStateOf<Expression?>(null)}
    var presetRequest by remember {mutableStateOf<Preset?>(null)}
    var renameRequest by remember {mutableStateOf<Resource?>(null)}
    var deleteRequest by remember {mutableStateOf<LibraryDeletion?>(null)}
    val resources=remember(story.resources,projectId) {story.resources.filter {it.projectId==projectId}}
    val presets=remember(story.presets,projectId) {story.presets.filter {it.projectId==projectId}}
    val usages=remember(story,projectId) {resourceUsageIndex(story,projectId)}
    val character=story.characters.find {it.id==characterId && it.projectId==projectId}
    BackHandler(character != null) { characterId = null }
    Column(modifier.fillMaxWidth()) {
        if(character!=null) {
            Row {
                TextButton(onClick={characterId=null}) {Text("‹ Biblioteca")}
                Text(character.name,Modifier.weight(1f).padding(12.dp),style=MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={characterRequest=character}){Text("Editar personaje")}
                TextButton(onClick={presetRequest=presets.find {it.id==character.dialogPresetId}}){Text("Estilo de diálogo")}
                TextButton(onClick={expressionRequest=Expression(0,character.id,"",0)}){Text("+ Expresión")}
            }
            if(character.description.isNotBlank()) Text(character.description,Modifier.padding(12.dp))
            val expressions=story.expressions.filter {it.characterId==character.id}
            if(expressions.isEmpty()) Text("Importa imágenes en Biblioteca y crea las expresiones del personaje.",Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(expressions,key={it.id}) {e->Card {
                    Row {
                        ResourcePreview(resources.find {it.id==e.resourceId},Modifier.size(100.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name,Modifier.padding(8.dp));Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick={expressionRequest=e}){Text("Editar expresión")}
                                TextButton(onClick={edit {library.reorderExpression(e.id,-1)}},enabled=expressions.first().id!=e.id){Text("↑")}
                                TextButton(onClick={edit {library.reorderExpression(e.id,1)}},enabled=expressions.last().id!=e.id){Text("↓")}
                                TextButton(onClick={deleteRequest=LibraryDeletion("expressions",e.id,e.name)}){Text("Eliminar")}
                            }
                        }
                    }
                }}
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("characters" to "Personajes","background" to "Fondos","images" to "Imágenes","gif" to "GIF","video" to "Videos","audio" to "Audio","presets" to "Presets").forEach { (key,label)->FilterChip(section==key,{section=key;query=""},label={Text(label)}) }
            }
            OutlinedTextField(query,{query=it},label={Text("Buscar…")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp))
            when(section) {
                "characters" -> {
                    TextButton(onClick={characterRequest=Character(0,projectId,"")}){Text("+ Crear personaje")}
                    val characters=story.characters.filter {it.projectId==projectId && it.name.contains(query,true)}
                    LazyVerticalGrid(GridCells.Fixed(2),Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(characters,key={it.id}) {c->Card(onClick={characterId=c.id},modifier=Modifier.testTag("library-character-${c.id}")) {
                            ResourcePreview(resources.find {it.id==(c.portraitId?:story.expressions.firstOrNull {e->e.characterId==c.id}?.resourceId)},Modifier.fillMaxWidth().height(130.dp))
                            Text(c.name,Modifier.padding(8.dp));Text("${story.expressions.count {it.characterId==c.id}} expresiones",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={deleteRequest=LibraryDeletion("characters",c.id,c.name)}){Text("Eliminar personaje")}
                        }}
                    }
                }
                "presets" -> {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("dialog" to "+ Preset de diálogo","button" to "+ Preset de botón","action" to "+ Preset de acción").forEach { (kind,label)->TextButton(onClick={presetRequest=Preset(0,projectId,"",kind=kind)}){Text(label)} }
                    }
                    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(presets.filter {it.name.contains(query,true)},key={it.id}) {p->Card {
                            Text("${p.name} · ${when(p.kind){"button"->"Botón";"action"->"Acción";"narrator"->"Narrador";else->"Diálogo"}}",Modifier.padding(8.dp))
                            PresetPreview(p,Modifier.fillMaxWidth().height(100.dp))
                            Row {
                                TextButton(onClick={presetRequest=p}){Text("Editar preset")}
                                if(p.kind!="narrator") TextButton(onClick={deleteRequest=LibraryDeletion("presets",p.id,p.name)}){Text("Eliminar preset")}
                            }
                        }}
                    }
                }
                else -> {
                    Text("Reemplazar afecta a nuevas inserciones. Las instancias colocadas se conservan.", Modifier.padding(horizontal=12.dp,vertical=4.dp), style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if(section=="background") {
                            TextButton(onClick={import("image","background")}){Text("Importar fondo")}
                            TextButton(onClick={import("gif","background")}){Text("Fondo GIF")}
                            TextButton(onClick={import("video","background")}){Text("Fondo video")}
                        } else TextButton(onClick={val type=if(section=="images") "image" else section;import(type,type)}){Text("Importar recurso")}
                        FilterChip(onlyUnused,{onlyUnused=!onlyUnused},label={Text("Sin usar")})
                    }
                    val filtered=resources.filter {r->r.name.contains(query,true) && (if(section=="background") (r.category=="background" || (usages[r.id]?.backgrounds ?: 0)>0) else r.type==(if(section=="images") "image" else section)) && (!onlyUnused || usages[r.id]?.total==0)}
                    if(filtered.isEmpty()) Text("No hay recursos en esta vista. Importa uno o cambia el filtro.",Modifier.padding(16.dp))
                    LazyVerticalGrid(GridCells.Fixed(2),Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(filtered,key={it.id}) {r->Card(modifier=Modifier.testTag("resource-${r.id}")) {
                            ResourcePreview(r,Modifier.fillMaxWidth().height(130.dp))
                            Text(r.name,Modifier.padding(8.dp),maxLines=2)
                            val count=usages[r.id]?.total ?: 0
                            Text(if(count==0) "Sin usar" else "$count referencias",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick={use(r)}){Text("Usar")}
                                TextButton(onClick={renameRequest=r}){Text("Renombrar")}
                                TextButton(onClick={replace(r)}){Text("Reemplazar")}
                                TextButton(onClick={deleteRequest=LibraryDeletion("resources",r.id,r.name)}){Text("Eliminar")}
                            }
                        }}
                    }
                }
            }
        }
    }
    characterRequest?.let {c->CharacterDialog(c,resources,presets,{characterRequest=null},{updated->edit {library.saveCharacter(updated)};characterRequest=null})}
    expressionRequest?.let {e->ExpressionDialog(e,resources,{expressionRequest=null},{updated->edit {library.saveExpression(updated)};expressionRequest=null})}
    presetRequest?.let {p->PresetDialog(p,{presetRequest=null},{updated->edit {library.savePreset(updated)};presetRequest=null})}
    renameRequest?.let {r->NameDialog("Renombrar recurso",r.name,{renameRequest=null},{name->edit {library.renameResource(r.id,name)};renameRequest=null})}
    deleteRequest?.let {r->
        val usage=if(r.table=="resources") usages[r.id] else null
        val characterUses=if(r.table=="presets") story.characters.count {it.dialogPresetId==r.id} else 0
        val blocked=(usage?.total ?: 0)>0 || characterUses>0
        AlertDialog(onDismissRequest={deleteRequest=null},title={Text("Eliminar ${r.name}")},text={Text(when {
            usage!=null && usage.total>0 -> "Este recurso está utilizado en ${usage.description}. Retira las referencias antes de eliminarlo."
            characterUses>0 -> "Este preset está asociado a $characterUses personajes. Cambia sus estilos antes de eliminarlo."
            r.table=="characters" -> "Se eliminará el personaje y sus expresiones. Los elementos colocados conservarán sus imágenes, nombres y estilos."
            r.table=="expressions" -> "Los elementos colocados conservarán su imagen y transformación."
            r.table=="presets" -> "Los elementos existentes conservarán su estilo."
            else -> "Se retirará de Biblioteca. El archivo solo se eliminará si no quedan otras referencias."
        })},confirmButton={if(!blocked) TextButton(onClick={edit {when(r.table){"resources"->library.deleteResource(r.id);"characters"->library.deleteCharacter(r.id);"expressions"->library.deleteExpression(r.id);else->library.deletePreset(r.id)}};deleteRequest=null}){Text("Eliminar")}},dismissButton={TextButton(onClick={deleteRequest=null}){Text(if(blocked) "Cerrar" else "Cancelar")}})
    }
}

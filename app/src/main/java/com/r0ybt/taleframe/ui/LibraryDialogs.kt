package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.taleframe.data.*
import java.io.File

@Composable
fun ResourcePreview(resource: Resource?, modifier: Modifier = Modifier) {
    if(resource==null) Box(modifier) { Text("Sin imagen",Modifier.padding(12.dp)) }
    else if(!File(resource.path).exists()) Box(modifier) {Text("Recurso no disponible. Reemplázalo desde Biblioteca.",Modifier.padding(8.dp))}
    else if(resource.type=="audio") Column(modifier.padding(12.dp)) {
        Text("♫ Audio local"); Text("${File(resource.path).length()/1024} KB",style=MaterialTheme.typography.bodySmall)
    } else LocalMedia(resource.path,MediaOptions(type=resource.type,revision=resource.revision),modifier,preview=true)
}
@Composable
fun PresetPreview(p: Preset, modifier: Modifier = Modifier) {
    if(p.kind=="action") Column(modifier.padding(8.dp)) { Text(p.name);Text("${p.transition.type} · ${p.transition.duration} ms") }
    else DialogueVisual(styledElement(0,p,if(p.style.showName) "Personaje" else "").copy(text="Texto de ejemplo"),16.sp,8.dp,modifier)
}
@Composable
fun ResourcePicker(resources: List<Resource>, title: String, dismiss: () -> Unit, choose: (Resource?) -> Unit, allowNone: Boolean = false) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={ Column {
        OutlinedTextField(query,{query=it},label={Text("Buscar recurso")},singleLine=true)
        if(allowNone) TextButton(onClick={choose(null)}) { Text("Sin imagen") }
        if(resources.isEmpty()) Text("Importa imágenes desde Biblioteca para elegirlas aquí.",Modifier.padding(12.dp))
        LazyVerticalGrid(GridCells.Fixed(2),Modifier.height(300.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(resources.filter { it.name.contains(query,true) },key={it.id}) { r -> Card(onClick={choose(r)},modifier=Modifier.testTag("library-pick-${r.id}")) {
                ResourcePreview(r,Modifier.fillMaxWidth().height(100.dp));Text(r.name,Modifier.padding(8.dp),maxLines=2)
            } }
        }
    } },confirmButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}
@Composable
fun StyleControls(style: VisualStyle, change: (VisualStyle) -> Unit) {
    Text("Forma")
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf("rectangle" to "Rectángulo","rounded" to "Redondeado","oval" to "Óvalo","circle" to "Círculo").forEach { (key,label) -> FilterChip(style.shape==key,{change(style.copy(shape=key))},label={Text(label)}) }
    }
    Text("Fuente")
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf("default" to "Sistema","sans" to "Sans","serif" to "Serif","mono" to "Monoespaciada").forEach { (key,label) -> FilterChip(style.font==key,{change(style.copy(font=key))},label={Text(label)}) }
    }
    Text("Tamaño de texto: ${(style.textScale*100).toInt()} %")
    Slider(style.textScale,{change(style.copy(textScale=it))},valueRange=.5f..2f)
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf("start" to "Izquierda","center" to "Centro","end" to "Derecha").forEach { (key,label) -> FilterChip(style.alignment==key,{change(style.copy(alignment=key))},label={Text(label)}) }
    }
    Text("Opacidad del fondo: ${(style.backgroundOpacity*100).toInt()} %")
    Slider(style.backgroundOpacity,{change(style.copy(backgroundOpacity=it))})
    Text("Borde: ${style.borderWidth.toInt()} dp")
    Slider(style.borderWidth,{change(style.copy(borderWidth=it))},valueRange=0f..8f)
    if(style.borderWidth>0f) ColorPicker("Color del borde",style.borderColor) {change(style.copy(borderColor=it))}
    Row { Switch(style.showName,{change(style.copy(showName=it))});Text("Mostrar nombre",Modifier.padding(12.dp)) }
}
@Composable
fun PresetDialog(preset: Preset, dismiss: () -> Unit, save: (Preset) -> Unit) {
    var draft by remember(preset.id,preset.kind) { mutableStateOf(preset) }
    AlertDialog(onDismissRequest=dismiss,title={Text("Editar preset")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Afecta a nuevas instancias. Los elementos existentes conservan su estilo.")
        OutlinedTextField(draft.name,{draft=draft.copy(name=it)},label={Text("Nombre del preset")},singleLine=true)
        if(draft.kind=="action") TransitionControls(draft.transition) { draft=draft.copy(transition=it) }
        else {
            PresetPreview(draft,Modifier.fillMaxWidth().height(120.dp))
            ColorPicker("Color del texto",draft.textColor) {draft=draft.copy(textColor=it)}
            ColorPicker("Color del cuadro",draft.backgroundColor) {draft=draft.copy(backgroundColor=it)}
            StyleControls(draft.style) {draft=draft.copy(style=it)}
        }
    }},confirmButton={TextButton(onClick={save(draft)},enabled=draft.name.isNotBlank()){Text("Guardar preset")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}
@Composable
fun CharacterDialog(character: Character, resources: List<Resource>, presets: List<Preset>, dismiss: () -> Unit, save: (Character) -> Unit) {
    var draft by remember(character.id) {mutableStateOf(character)}
    var portraitPicker by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest=dismiss,title={Text(if(character.id==0L) "Crear personaje" else "Editar personaje")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft.name,{draft=draft.copy(name=it)},label={Text("Nombre del personaje")},singleLine=true)
        OutlinedTextField(draft.description,{draft=draft.copy(description=it)},label={Text("Descripción")},maxLines=3)
        ResourcePreview(resources.find {it.id==draft.portraitId},Modifier.fillMaxWidth().height(100.dp))
        TextButton(onClick={portraitPicker=true}) {Text("Elegir portrait")}
        Text("Estilo de diálogo")
        TextButton(onClick={draft=draft.copy(dialogPresetId=null)}) {Text(if(draft.dialogPresetId==null) "✓ Crear estilo propio" else "Crear estilo propio")}
        presets.filter {it.kind=="dialog"}.forEach { p -> TextButton(onClick={draft=draft.copy(dialogPresetId=p.id)}) {Text("${if(draft.dialogPresetId==p.id) "✓ " else ""}${p.name}")} }
    }},confirmButton={TextButton(onClick={save(draft)},enabled=draft.name.isNotBlank()){Text("Guardar personaje")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
    if(portraitPicker) ResourcePicker(resources.filter {it.type=="image"},"Portrait",{portraitPicker=false},{draft=draft.copy(portraitId=it?.id);portraitPicker=false},allowNone=true)
}
@Composable
fun ExpressionDialog(expression: Expression, resources: List<Resource>, dismiss: () -> Unit, save: (Expression) -> Unit) {
    var draft by remember(expression.id) {mutableStateOf(expression)}
    var pick by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest=dismiss,title={Text(if(expression.id==0L) "Crear expresión" else "Editar expresión")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft.name,{draft=draft.copy(name=it)},label={Text("Nombre de expresión")},singleLine=true)
        ResourcePreview(resources.find {it.id==draft.resourceId},Modifier.fillMaxWidth().height(130.dp))
        TextButton(onClick={pick=true}) {Text("Asignar imagen")}
        Text("Cambiar esta expresión afecta a nuevas inserciones. Las instancias colocadas conservan su imagen.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={save(draft)},enabled=draft.name.isNotBlank() && resources.any {it.id==draft.resourceId}){Text("Guardar expresión")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
    if(pick) ResourcePicker(resources.filter {it.type=="image"},"Imagen de expresión",{pick=false},{r->if(r!=null) draft=draft.copy(resourceId=r.id);pick=false})
}
@Composable
fun CharacterPicker(story: Story, projectId: Long, dismiss: () -> Unit, choose: (Character) -> Unit) {
    val characters=story.characters.filter {it.projectId==projectId}
    AlertDialog(onDismissRequest=dismiss,title={Text("Elegir personaje")},text={
        if(characters.isEmpty()) Text("Crea personajes y expresiones en Biblioteca → Personajes.")
        else LazyVerticalGrid(GridCells.Fixed(2),Modifier.height(320.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(characters,key={it.id}) {c->Card(onClick={choose(c)},modifier=Modifier.testTag("character-${c.id}")) {
                val fallback=story.expressions.firstOrNull {it.characterId==c.id}?.resourceId
                ResourcePreview(story.resources.find {it.id==(c.portraitId?:fallback)},Modifier.fillMaxWidth().height(100.dp));Text(c.name,Modifier.padding(8.dp))
            }}
        }
    },confirmButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}
@Composable
fun ExpressionPicker(character: Character, expressions: List<Expression>, resources: List<Resource>, dismiss: () -> Unit, choose: (List<Long>) -> Unit, allowSequence: Boolean = true) {
    var sequence by remember {mutableStateOf(false)}
    var ordered by remember {mutableStateOf<List<Long>>(emptyList())}
    AlertDialog(onDismissRequest=dismiss,title={Text("Expresiones de ${character.name}")},text={Column {
        if(expressions.isEmpty()) Text("Añade expresiones en Biblioteca → Personajes → ${character.name}.")
        if(allowSequence && expressions.size>1) Row {Switch(sequence,{sequence=it;ordered=emptyList()});Text("Crear secuencia",Modifier.padding(12.dp))}
        if(sequence) Text("Toca las imágenes en el orden deseado. La duración se ajusta en Editar.")
        LazyVerticalGrid(GridCells.Fixed(2),Modifier.height(300.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(expressions,key={it.id}) { e-> Card(onClick={if(!sequence) choose(listOf(e.id)) else ordered=if(e.id in ordered) ordered-e.id else ordered+e.id},modifier=Modifier.testTag("expression-${e.id}")) {
                ResourcePreview(resources.find {it.id==e.resourceId},Modifier.fillMaxWidth().height(100.dp))
                Text("${if(e.id in ordered) "${ordered.indexOf(e.id)+1}. " else ""}${e.name}",Modifier.padding(8.dp))
            }}
        }
    }},confirmButton={if(sequence) TextButton(onClick={choose(ordered)},enabled=ordered.isNotEmpty()){Text("Insertar secuencia")} else TextButton(onClick=dismiss){Text("Cancelar")}},dismissButton={if(sequence) TextButton(onClick=dismiss){Text("Cancelar")}})
}
@Composable
fun DialogPicker(story: Story, projectId: Long, dismiss: () -> Unit, choose: (Long?, Long?) -> Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text("Crear diálogo")},text={LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {TextButton(onClick={choose(null,null)}){Text("Diálogo genérico")}}
        items(story.characters.filter {it.projectId==projectId},key={"character-${it.id}"}) { c->Card(onClick={choose(c.id,null)}) {
            val p=story.presets.find {it.id==c.dialogPresetId}
            Text(c.name,Modifier.padding(8.dp));if(p!=null) PresetPreview(p,Modifier.fillMaxWidth().height(80.dp))
        }}
        items(story.presets.filter {it.projectId==projectId && it.kind in listOf("dialog","narrator")},key={"preset-${it.id}"}) {p->Card(onClick={choose(null,p.id)}) {
            Text(if(p.kind=="narrator") "Narrador" else p.name,Modifier.padding(8.dp));PresetPreview(p,Modifier.fillMaxWidth().height(80.dp))
        }}
    }},confirmButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun PresetPicker(presets: List<Preset>, title: String, dismiss: () -> Unit, choose: (Preset) -> Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items(presets,key={it.id}) {p->Card(onClick={choose(p)},modifier=Modifier.testTag("preset-${p.id}")) {
            Text(p.name,Modifier.padding(8.dp));PresetPreview(p,Modifier.fillMaxWidth().height(90.dp))
        }}
    }},confirmButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

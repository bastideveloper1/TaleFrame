package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

@Composable
fun SlidePreview(slide: Slide, elements: List<Element>, modifier: Modifier = Modifier) {
    SlideCanvas(slide, elements.filter { it.slideId == slide.id }, modifier.aspectRatio(.75f), preview = true)
}

@Composable
fun Album(slides: List<Slide>, elements: List<Element>, size: String, modifier: Modifier = Modifier,
    open: (Slide) -> Unit, rename: (Slide) -> Unit, delete: (Slide) -> Unit, duplicate: (Slide) -> Unit, reorder: (Slide, Int) -> Unit, initialId:Long? = slides.minByOrNull {it.id}?.id, setInitial:(Slide)->Unit = {}, setDraft:(Slide)->Unit = {}, state:LazyGridState=rememberLazyGridState(), moveTo:(Slide,Int)->Unit = {_,_->}) {
    val columns = when (size) { "Pequeña" -> 3; "Mediana" -> 2; else -> 1 }
    var dragging by remember {mutableStateOf<Long?>(null)}
    var insertion by remember {mutableIntStateOf(-1)}
    var edge by remember {mutableFloatStateOf(0f)}
    val currentSlides by rememberUpdatedState(slides)
    val currentMove by rememberUpdatedState(moveTo)
    LaunchedEffect(dragging,edge) {
        while(dragging!=null && edge!=0f) {state.scroll {scrollBy(edge)};delay(16)}
    }
    LazyVerticalGrid(GridCells.Fixed(columns), modifier.testTag("album"), state=state, contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(slides, key = { it.id }) { slide ->
            var menu by remember { mutableStateOf(false) }
            val accent=MaterialTheme.colorScheme.primary
            val drag=Modifier.pointerInput(slide.id,state) {
                var pointer=Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart={local ->
                        state.layoutInfo.visibleItemsInfo.find {it.key==slide.id}?.let {info->
                            val startOffset=Offset(info.offset.x.toFloat(),info.offset.y.toFloat())
                            pointer=startOffset+local;dragging=slide.id;insertion=currentSlides.indexOfFirst {it.id==slide.id}
                        }
                    },onDragCancel={dragging=null;insertion=-1;edge=0f},
                    onDragEnd={val index=insertion;if(index>=0) currentMove(slide,index);dragging=null;insertion=-1;edge=0f},
                    onDrag={change,amount ->
                        change.consume();pointer+=amount
                        val info=state.layoutInfo
                        info.visibleItemsInfo.minByOrNull {
                            (pointer-Offset(it.offset.x+it.size.width/2f,it.offset.y+it.size.height/2f)).getDistance()
                        }?.let {insertion=it.index}
                        val margin=64.dp.toPx()
                        edge=when {pointer.y<info.viewportStartOffset+margin -> -12.dp.toPx();pointer.y>info.viewportEndOffset-margin ->12.dp.toPx();else->0f}
                    })
            }
            Card(Modifier.fillMaxWidth().testTag("album-${slide.id}").then(drag)
                .then(if(insertion==slides.indexOf(slide) && dragging!=null) Modifier.border(3.dp,accent,MaterialTheme.shapes.medium) else Modifier)
                .graphicsLayer {alpha=if(dragging==slide.id) .65f else 1f}.clickable { open(slide) }) {
                if(insertion==slides.indexOf(slide) && dragging!=null) Text("Insertar aquí",Modifier.padding(6.dp),color=accent)
                SlidePreview(slide, elements, Modifier.fillMaxWidth())
                Column(Modifier.padding(8.dp)) {
                    Text(slide.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    val buttons = elements.filter { it.slideId == slide.id && it.kind == "button" }
                    val targets = buttons.mapNotNull { e -> slides.indexOfFirst { it.id == e.targetId }.takeIf { it >= 0 }?.plus(1) }.distinct()
                    Text(if (buttons.isEmpty()) (if (slide.autoEnabled) "Avance automático" else "FIN") else if (targets.isEmpty()) "Sin destino" else "→ ${targets.joinToString(", ")}${if (buttons.any { it.targetId == null }) " · ?" else ""}", style = MaterialTheme.typography.labelSmall)
                    if (slide.autoEnabled) Text("⏱ ${slide.autoSeconds} s → ${slides.find { it.id == slide.autoTargetId }?.name ?: "Sin destino"}", style = MaterialTheme.typography.labelSmall)
                    if(slide.draft) Text("Borrador / Incompleta",color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.labelSmall)
                    if (slide.id == initialId) Text("Inicio", style = MaterialTheme.typography.labelSmall)
                    Box {
                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("Opciones") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text={Text("Establecer como lámina inicial")},onClick={menu=false;setInitial(slide)})
                            DropdownMenuItem(text={Text(if(slide.draft) "Marcar completa" else "Marcar borrador")},onClick={menu=false;setDraft(slide)})
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; rename(slide) })
                            DropdownMenuItem(text = { Text("Duplicar lámina") }, onClick = { menu = false; duplicate(slide) })
                            DropdownMenuItem(text = { Text("Mover antes") }, enabled = slides.first().id != slide.id, onClick = { menu = false; reorder(slide, -1) })
                            DropdownMenuItem(text = { Text("Mover después") }, enabled = slides.last().id != slide.id, onClick = { menu = false; reorder(slide, 1) })
                            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; delete(slide) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationPicker(slides: List<Slide>, elements: List<Element>, selected: Long?, choose: (Long?) -> Unit, allowNone: Boolean = true, currentId:Long? = null) {
    if (allowNone) TextButton(onClick = { choose(null) }) { Text(if (selected == null) "✓ Sin destino" else "Sin destino") }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(slides, key = { it.id }) { slide ->
            Card(onClick = { choose(slide.id) }, modifier = Modifier.testTag("destination-${slide.id}").then(if(slide.id==currentId) Modifier.border(3.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.medium) else Modifier), colors = CardDefaults.cardColors(containerColor = if (selected == slide.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                SlidePreview(slide, elements, Modifier.fillMaxWidth())
                Text("${slides.indexOf(slide)+1}${if(slide.id==currentId) " · ACTUAL" else ""}${if(slide.draft) " · Borrador" else ""}",Modifier.padding(8.dp),style=MaterialTheme.typography.labelSmall)
                Text("${if (selected == slide.id) "✓ " else ""}${slide.name}", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun DestinationDialog(slides:List<Slide>,elements:List<Element>,selected:Long?,currentId:Long?,dismiss:()->Unit,choose:(Long?)->Unit) {
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth()) {Text("Ir a…",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton(onClick=dismiss){Text("Cancelar")}}
                Text("ACTUAL identifica la lámina editada. Puedes elegirla para crear un loop.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={choose(null)}){Text("Sin destino")}
                LazyVerticalGrid(GridCells.Fixed(2),Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(slides,key={it.id}) {s ->
                        Card(onClick={choose(s.id)},modifier=Modifier.testTag("destination-${s.id}")
                            .then(if(s.id==currentId) Modifier.border(3.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.medium) else Modifier),
                            colors=CardDefaults.cardColors(containerColor=if(s.id==selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                            SlidePreview(s,elements,Modifier.fillMaxWidth())
                            Text("${slides.indexOf(s)+1} · ${s.name}",Modifier.padding(8.dp))
                            if(s.id==currentId) Text("ACTUAL",Modifier.padding(horizontal=8.dp),color=MaterialTheme.colorScheme.primary)
                            if(s.draft) Text("Borrador",Modifier.padding(8.dp),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

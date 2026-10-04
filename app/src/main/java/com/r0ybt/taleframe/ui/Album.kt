package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
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
    open: (Slide) -> Unit, rename: (Slide) -> Unit, delete: (Slide) -> Unit, duplicate: (Slide) -> Unit, reorder: (Slide, Int) -> Unit) {
    val columns = when (size) { "Pequeña" -> 3; "Mediana" -> 2; else -> 1 }
    LazyVerticalGrid(GridCells.Fixed(columns), modifier, contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(slides, key = { it.id }) { slide ->
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { open(slide) }) {
                SlidePreview(slide, elements, Modifier.fillMaxWidth())
                Column(Modifier.padding(8.dp)) {
                    Text(slide.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    val buttons = elements.filter { it.slideId == slide.id && it.kind == "button" }
                    val targets = buttons.mapNotNull { e -> slides.indexOfFirst { it.id == e.targetId }.takeIf { it >= 0 }?.plus(1) }.distinct()
                    Text(if (buttons.isEmpty()) "FIN" else if (targets.isEmpty()) "Sin destino" else "→ ${targets.joinToString(", ")}${if (buttons.any { it.targetId == null }) " · ?" else ""}", style = MaterialTheme.typography.labelSmall)
                    if (slide.id == slides.minByOrNull { it.id }?.id) Text("Inicio", style = MaterialTheme.typography.labelSmall)
                    Box {
                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("Opciones") }
                        DropdownMenu(menu, { menu = false }) {
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
fun DestinationPicker(slides: List<Slide>, elements: List<Element>, selected: Long?, choose: (Long?) -> Unit, allowNone: Boolean = true) {
    if (allowNone) TextButton(onClick = { choose(null) }) { Text(if (selected == null) "✓ Sin destino" else "Sin destino") }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(slides, key = { it.id }) { slide ->
            Card(onClick = { choose(slide.id) }, modifier = Modifier.testTag("destination-${slide.id}"), colors = CardDefaults.cardColors(containerColor = if (selected == slide.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                SlidePreview(slide, elements, Modifier.fillMaxWidth())
                Text("${if (selected == slide.id) "✓ " else ""}${slide.name}", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

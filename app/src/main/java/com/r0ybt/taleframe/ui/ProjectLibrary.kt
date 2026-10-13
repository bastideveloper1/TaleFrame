package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

@Composable
fun ProjectLibrary(story: Story, modifier: Modifier = Modifier, open: (Project) -> Unit,
    rename: (Project) -> Unit, delete: (Project) -> Unit, export: (Project) -> Unit,
    adjust: (Project) -> Unit, configurePlayback: (Project) -> Unit = {}) {
    LazyVerticalGrid(GridCells.Adaptive(144.dp), modifier, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(story.projects, key = { it.id }) { project ->
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { open(project) }) {
                ProjectCover(project, story, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${story.slides.count { it.projectId == project.id }} láminas", style = MaterialTheme.typography.bodySmall)
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Opciones") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Abrir / editar") }, onClick = { menu = false; open(project) })
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; rename(project) })
                            DropdownMenuItem(text = { Text("Ajustar portada") }, enabled = project.coverResourceId != null,
                                onClick = { menu = false; adjust(project) })
                            DropdownMenuItem(text = { Text("Transiciones y secuencia") }, onClick = { menu = false; configurePlayback(project) })
                            DropdownMenuItem(text = { Text("Exportar proyecto") }, onClick = { menu = false; export(project) })
                            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; delete(project) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverAdjustmentDialog(project: Project, story: Story, dismiss: () -> Unit, save: (Float, Float, Float) -> Unit) {
    var scale by rememberSaveable(project.id, project.coverResourceId) { mutableFloatStateOf(project.coverScale) }
    var x by rememberSaveable(project.id, project.coverResourceId) { mutableFloatStateOf(project.coverX) }
    var y by rememberSaveable(project.id, project.coverResourceId) { mutableFloatStateOf(project.coverY) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .6f).dp
    AlertDialog(onDismissRequest = dismiss, title = { Text("Ajustar portada") }, text = {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            ProjectCover(project.copy(coverScale = scale, coverX = x, coverY = y), story,
                Modifier.width(120.dp).aspectRatio(2f / 3f))
            Text("El marco muestra cómo se verá en la biblioteca.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            Text("Escala", Modifier.fillMaxWidth())
            Slider(scale, { scale = it }, valueRange = .5f..4f)
            Text("Posición horizontal", Modifier.fillMaxWidth())
            Slider(x, { x = it }, valueRange = -1f..1f)
            Text("Posición vertical", Modifier.fillMaxWidth())
            Slider(y, { y = it }, valueRange = -1f..1f)
            TextButton(onClick = { scale = 1f; x = 0f; y = 0f }) { Text("Restablecer · Ajustar / Fit") }
        }
    }, confirmButton = { TextButton(onClick = { save(scale, x, y) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

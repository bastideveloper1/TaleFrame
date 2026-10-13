package com.r0ybt.taleframe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuickScriptScreen(
    state: QuickScriptState,
    busy: Boolean,
    modifier: Modifier = Modifier,
    saveInput: (String) -> Unit,
    addText: (String) -> Unit,
    createSlide: (Long, Int, Int, ScriptInsertionPoint) -> Unit,
    undo: () -> Unit,
    slides: List<Slide>,
    elements: List<Element>
) {
    // Large text stays in SQLite rather than the Activity's saved-state Bundle.
    var input by remember(state.projectId) { mutableStateOf(state.input) }
    var anchor by remember(state.projectId) { mutableStateOf<ScriptParagraph?>(null) }
    var selected by remember(state.projectId) { mutableStateOf<List<ScriptParagraph>>(emptyList()) }
    var insertion by remember(state.projectId) { mutableStateOf(ScriptInsertionPoint()) }
    var showInsertion by remember { mutableStateOf(false) }
    var lastAssignmentId by remember(state.projectId) { mutableStateOf(state.assignments.lastOrNull()?.id ?: 0L) }
    val location = scriptInsertionDescription(slides, insertion)
    val locationValid = runCatching { scriptInsertionIndex(slides.map { it.id }, insertion) }.isSuccess
    val lastCreated = slides.firstOrNull { it.id == state.assignments.lastOrNull()?.slideId }
    LaunchedEffect(state.assignments) {
        val last = state.assignments.lastOrNull()
        if (last != null && last.id > lastAssignmentId && last.slideId != null) {
            insertion = ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = last.slideId)
        } else if (!locationValid) insertion = ScriptInsertionPoint()
        lastAssignmentId = last?.id ?: 0L
    }
    var showOriginal by remember { mutableStateOf(false) }
    var showPrecise by remember(state.projectId) { mutableStateOf(false) }
    LaunchedEffect(state.drafts) { input = state.input }
    LaunchedEffect(state.assignments) { anchor = null; selected = emptyList(); showPrecise = false }
    val fragments by produceState<List<ScriptFragment>>(emptyList(), state.drafts, state.assignments) {
        value = withContext(Dispatchers.Default) { state.pendingFragments() }
    }
    val paragraphs by produceState<List<ScriptParagraph>>(emptyList(), state.drafts, state.assignments) {
        value = withContext(Dispatchers.Default) { state.pendingParagraphs() }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; saveInput(it) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pega o escribe texto narrativo") },
            minLines = 2,
            maxLines = 5
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Button(onClick = { addText(input) }, enabled = !busy && input.isNotEmpty()) { Text("Añadir texto pendiente") }
            TextButton(onClick = { showOriginal = true }, enabled = state.drafts.isNotEmpty()) { Text("Texto original") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { showInsertion = true }, enabled = !busy) { Text("Ubicación") }
            lastCreated?.let { last ->
                TextButton(onClick = { insertion = ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = last.id) }, enabled = !busy) {
                    Text("Continuar después de «${last.name}»")
                }
            }
        }
        Text("Nueva lámina: $location", style = MaterialTheme.typography.bodySmall)
        Text("Selecciona el primer párrafo y luego el último del fragmento. Cada salto de línea separa un párrafo.", style = MaterialTheme.typography.bodySmall)
        Text("Láminas creadas en Guion rápido: ${state.assignments.size}", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Button(onClick = {
                val first = selected.firstOrNull() ?: return@Button
                createSlide(first.draftId, first.start, selected.last().end, insertion)
            }, enabled = !busy && locationValid && selected.isNotEmpty()) { Text("Crear lámina") }
            TextButton(onClick = { showPrecise = true }, enabled = !busy && fragments.isNotEmpty()) { Text("Selección precisa") }
            TextButton(onClick = undo, enabled = !busy && state.assignments.isNotEmpty()) { Text("Deshacer") }
            TextButton(onClick = { anchor = null; selected = emptyList() }, enabled = selected.isNotEmpty()) { Text("Limpiar") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (paragraphs.isEmpty()) item { Text("No hay párrafos pendientes. Puedes añadir más texto.", Modifier.padding(12.dp)) }
            items(paragraphs, key = { "${it.draftId}:${it.start}" }) { paragraph ->
                val first = selected.firstOrNull()
                val last = selected.lastOrNull()
                val checked = first != null && last != null && first.draftId == paragraph.draftId && paragraph.start >= first.start && paragraph.end <= last.end
                Card(colors = CardDefaults.cardColors(containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        val first = anchor
                        if (first == null) { anchor = paragraph; selected = listOf(paragraph) }
                        else {
                            selected = scriptSelection(paragraphs, first, paragraph)
                            if (selected.size == 1) anchor = paragraph
                        }
                    }) {
                    Row(Modifier.padding(8.dp)) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(paragraph.text.ifBlank { "(Párrafo vacío)" }, modifier = Modifier.weight(1f), maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (showInsertion) ScriptInsertionDialog(slides, elements, insertion,
        dismiss = { showInsertion = false }, choose = { insertion = it; showInsertion = false })
    if (showPrecise) PreciseScriptSelectionDialog(
        fragments = fragments,
        busy = busy,
        dismiss = { showPrecise = false },
        createSlide = { draft, start, end -> createSlide(draft, start, end, insertion) },
        location = location,
        locationValid = locationValid
    )
    if (showOriginal) AlertDialog(
        onDismissRequest = { showOriginal = false },
        title = { Text("Texto original conservado") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.drafts, key = { it.id }) { draft ->
                    OutlinedTextField(value = draft.original, onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), minLines = 3, maxLines = 10)
                }
            }
        },
        confirmButton = { TextButton(onClick = { showOriginal = false }) { Text("Cerrar") } }
    )
}

@Composable
private fun PreciseScriptSelectionDialog(
    fragments: List<ScriptFragment>,
    busy: Boolean,
    dismiss: () -> Unit,
    createSlide: (Long, Int, Int) -> Unit,
    location: String,
    locationValid: Boolean
) {
    var selection by remember(fragments) { mutableStateOf<ScriptFragment?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text("Selección precisa") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Nueva lámina: $location", style = MaterialTheme.typography.bodySmall) }
                item { Text("Mantén pulsado el texto y mueve los controles de selección. Los tramos ya asignados quedan separados.", style = MaterialTheme.typography.bodySmall) }
                item {
                    OutlinedTextField(value = selection?.text.orEmpty(), onValueChange = {}, readOnly = true,
                        label = { Text("Fragmento que se guardará") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 3)
                }
                items(fragments, key = { "${it.draftId}:${it.start}" }) { fragment ->
                    var value by remember(fragment) { mutableStateOf(TextFieldValue(fragment.text)) }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { changed ->
                            // Read-only Compose fields provide Android selection handles without an editor.
                            if (changed.text == fragment.text) {
                                value = changed
                                val start = fragment.start + changed.selection.min
                                val end = fragment.start + changed.selection.max
                                selection = if (validScriptRange(fragment.text, changed.selection.min, changed.selection.max))
                                    ScriptFragment(fragment.draftId, start, end, fragment.text.substring(changed.selection.min, changed.selection.max)) else null
                            }
                        },
                        readOnly = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Texto pendiente") },
                        minLines = 3,
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selection?.let { createSlide(it.draftId, it.start, it.end) } }, enabled = !busy && locationValid && selection != null) {
                Text("Crear lámina con selección")
            }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun ScriptInsertionDialog(
    slides: List<Slide>, elements: List<Element>, point: ScriptInsertionPoint,
    dismiss: () -> Unit, choose: (ScriptInsertionPoint) -> Unit
) {
    var mode by remember(point) { mutableStateOf(point.mode) }
    var afterId by remember(point) { mutableStateOf(point.afterId) }
    val index = slides.indexOfFirst { it.id == afterId }
    val candidate = ScriptInsertionPoint(mode, afterId, if (mode == ScriptInsertionMode.BETWEEN) slides.getOrNull(index + 1)?.id else null)
    val valid = runCatching { scriptInsertionIndex(slides.map { it.id }, candidate) }.isSuccess
    AlertDialog(onDismissRequest = dismiss, title = { Text("Ubicación de la nueva lámina") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf(ScriptInsertionMode.END to "Al final", ScriptInsertionMode.START to "Al principio",
                    ScriptInsertionMode.AFTER to "Después de", ScriptInsertionMode.BETWEEN to "Entre dos").forEach { (option, label) ->
                    FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(label) })
                }
            }
            if (mode == ScriptInsertionMode.AFTER || mode == ScriptInsertionMode.BETWEEN) {
                Text("Elige la lámina anterior al punto de inserción.", style = MaterialTheme.typography.bodySmall)
                DestinationPicker(if (mode == ScriptInsertionMode.BETWEEN) slides.dropLast(1) else slides,
                    elements, afterId, choose = { afterId = it }, allowNone = false)
            }
            Text(scriptInsertionDescription(slides, candidate), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { choose(candidate) }, enabled = valid) { Text("Elegir ubicación") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

package com.r0ybt.taleframe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class EditorTool(val id:String,val label:String,val enabled:Boolean=true,val action:()->Unit)
internal fun toolOrder(saved:String?,available:List<String>):List<String> =
    ((saved?.split('|') ?: emptyList())+available).distinct().filter {it in available}

private data class ToolGroup(val label: String, val icon: String, val ids: List<String>)

@Composable
internal fun EditorToolbar(tools: List<EditorTool>) {
    val groups = listOf(
        ToolGroup("Fondo", "▧", listOf("background", "move-background")),
        ToolGroup("Personaje", "+", listOf("character")),
        ToolGroup("Añadir", "+", listOf("dialog", "text", "button", "image", "gif", "video", "sequence", "panel")),
        ToolGroup("Audio/Tiempo", "◷", listOf("time")),
        ToolGroup("Acciones", "↗", listOf("actions")),
        ToolGroup("Ajustar calco", "▱", listOf("reference")),
        ToolGroup("Capas", "≡", listOf("layers")),
        ToolGroup("Selección", "↖", listOf("deselect", "draft"))
    )
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        groups.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { group ->
                    val actions = group.ids.mapNotNull { id -> tools.find { it.id == id } }
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = {
                            if (actions.size == 1) actions.first().action() else menu = true
                        }, enabled = actions.any { it.enabled }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(2.dp)) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(group.icon, style = MaterialTheme.typography.labelLarge)
                                Text(group.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            actions.forEach { tool ->
                                DropdownMenuItem(text = { Text(tool.label) }, enabled = tool.enabled,
                                    onClick = { menu = false; tool.action() })
                            }
                        }
                    }
                }
            }
        }
    }
}

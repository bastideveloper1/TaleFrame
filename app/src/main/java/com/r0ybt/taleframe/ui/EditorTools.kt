package com.r0ybt.taleframe.ui

import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class EditorTool(val id:String,val label:String,val enabled:Boolean=true,val action:()->Unit)
internal fun toolOrder(saved:String?,available:List<String>):List<String> =
    ((saved?.split('|') ?: emptyList())+available).distinct().filter {it in available}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorToolbar(tools:List<EditorTool>,preferences:SharedPreferences,expanded:Boolean,toggle:()->Unit,layers:()->Unit,play:()->Unit,grid:String,setGrid:(String)->Unit) {
    var order by remember {mutableStateOf(toolOrder(preferences.getString("tool_order",null),tools.map {it.id}))}
    var moving by remember {mutableStateOf<String?>(null)}
    var gridMenu by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().height(60.dp).background(MaterialTheme.colorScheme.surfaceContainer).horizontalScroll(rememberScrollState())) {
        TextButton(onClick=toggle,modifier=Modifier.semantics {contentDescription=if(expanded) "Reducir herramientas" else "Mostrar herramientas"}) {Text(if(expanded) "‹ Herramientas" else "Herramientas ›")}
        if(!expanded) {TextButton(onClick=layers){Text("Elementos")};TextButton(onClick=play){Text("▶ Probar desde aquí")}}
        if(expanded) {
            Box {TextButton(onClick={gridMenu=true}){Text("▦ Cuadrícula: $grid")}
                DropdownMenu(gridMenu,{gridMenu=false}) {listOf("Off","Fina","Media").forEach {mode ->DropdownMenuItem(text={Text(mode)},onClick={setGrid(mode);gridMenu=false})}}
            }
            toolOrder(order.joinToString("|"),tools.map {it.id}).forEach {id->
                val tool=tools.first {it.id==id}
                Surface(Modifier.padding(3.dp).height(54.dp).combinedClickable(enabled=tool.enabled,onClick=tool.action,onLongClick={moving=id}),
                    shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.padding(horizontal=12.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {Text(tool.label,color=if(tool.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.4f),style=MaterialTheme.typography.labelLarge)}
                }
            }
        }
    }
    moving?.let {id->
        val index=order.indexOf(id)
        fun move(delta:Int) {val list=order.toMutableList();list.removeAt(index);list.add((index+delta).coerceIn(0,list.size),id);order=list;preferences.edit().putString("tool_order",list.joinToString("|")).apply();moving=null}
        AlertDialog(onDismissRequest={moving=null},title={Text("Ordenar ${tools.first {it.id==id}.label}")},text={Text("Mantén pulsada una herramienta para cambiar su posición.")},
            confirmButton={TextButton(onClick={move(-1)},enabled=index>0){Text("Mover antes")}},
            dismissButton={Column {TextButton(onClick={move(1)},enabled=index<order.lastIndex){Text("Mover después")};TextButton(onClick={moving=null}){Text("Cerrar")}}})
    }
}

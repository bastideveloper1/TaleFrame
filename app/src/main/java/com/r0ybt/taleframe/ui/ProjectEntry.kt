package com.r0ybt.taleframe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

@Composable
fun ProjectCover(project:Project,story:Story,modifier:Modifier=Modifier) {
    val image=story.resources.find {it.id==project.coverResourceId}
    if(image!=null) ResourcePreview(image,modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {Text("TaleFrame",color=MaterialTheme.colorScheme.onPrimaryContainer,style=MaterialTheme.typography.titleLarge);Text("Tu próxima historia",color=MaterialTheme.colorScheme.onPrimaryContainer,style=MaterialTheme.typography.bodySmall)}
    }
}
@Composable
fun ProjectEntry(project:Project,story:Story,slides:List<Slide>,lastId:Long,cover:()->Unit,resume:(Long)->Unit,library:(String)->Unit,play:()->Unit,skip:(Boolean)->Unit) {
    val start=initialSlide(project,slides)
    val playback=playbackStart(project,slides)
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            ProjectCover(project,story,Modifier.width(72.dp).height(88.dp))
            Column(Modifier.weight(1f).padding(start=12.dp)) {
                Text("${slides.size} láminas · ${slides.count {it.draft}} borradores",style=MaterialTheme.typography.bodySmall)
                Text("Inicio: ${start?.name ?: "Sin láminas"}",style=MaterialTheme.typography.labelSmall)
                if(project.skipDrafts && start?.draft==true) Text("Al omitir borradores: ${playback?.name ?: "sin escena disponible"}",style=MaterialTheme.typography.labelSmall)
                TextButton(onClick=cover){Text("Cambiar portada")}
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick={val s=slides.find {it.id==lastId} ?: start;s?.let {resume(it.id)}},enabled=slides.isNotEmpty()){Text("Continuar edición")}
            TextButton(onClick=play,enabled=slides.isNotEmpty()){Text("Reproducir desde inicio")}
            TextButton(onClick={library("characters")}){Text("Personajes")}
            TextButton(onClick={library("presets")}){Text("Presets")}
            TextButton(onClick={library("templates")}){Text("Plantillas")}
        }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(project.skipDrafts,skip);Text("Omitir láminas en borrador",style=MaterialTheme.typography.bodySmall)}
    }
}

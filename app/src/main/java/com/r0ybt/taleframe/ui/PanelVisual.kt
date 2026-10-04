package com.r0ybt.taleframe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.Element

@Composable
fun PanelVisual(e:Element,preview:Boolean,editing:Boolean) {
    val frame=requireNotNull(e.panel)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color(e.backgroundColor)).border(1.dp,Color(0xFF705A64)),contentAlignment=Alignment.Center) {
        if(e.image==null) Text("Panel · asignar imagen",color=Color(0xFF51434A),modifier=Modifier.padding(8.dp))
        else LocalMedia(e.image,e.media,Modifier.fillMaxSize().graphicsLayer {
            if(frame.mode=="manual") {scaleX=frame.scale;scaleY=frame.scale;translationX=frame.x*constraints.maxWidth;translationY=frame.y*constraints.maxHeight}
            alpha=e.opacity
        },preview,fill=frame.mode!="fit",editing=editing)
    }
}

@Composable
fun PanelControls(frame:com.r0ybt.taleframe.data.PanelOptions,change:(com.r0ybt.taleframe.data.PanelOptions)->Unit) {
    androidx.compose.material3.Text("Encuadre del panel")
    Row {
        listOf("fit" to "Fit","fill" to "Fill","manual" to "Manual").forEach {(mode,label)->androidx.compose.material3.FilterChip(frame.mode==mode,{change(frame.copy(mode=mode))},label={Text(label)})}
    }
    if(frame.mode=="manual") {
        Text("Escala interior: ${(frame.scale*100).toInt()} %")
        androidx.compose.material3.Slider(frame.scale,{change(frame.copy(scale=it))},valueRange=.25f..4f)
        Text("Encuadre horizontal")
        androidx.compose.material3.Slider(frame.x,{change(frame.copy(x=it))},valueRange=-1f..1f)
        Text("Encuadre vertical")
        androidx.compose.material3.Slider(frame.y,{change(frame.copy(y=it))},valueRange=-1f..1f)
    }
}

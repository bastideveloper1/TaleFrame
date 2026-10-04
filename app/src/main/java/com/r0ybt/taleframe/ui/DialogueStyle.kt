package com.r0ybt.taleframe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.r0ybt.taleframe.data.*

/** The same renderer serves slide instances and library previews. Legacy styles stay identical. */
@Composable
fun DialogueVisual(e: Element, font: TextUnit, padding: Dp, modifier: Modifier = Modifier, editing: Boolean = false) {
    val shape=remember(e.style.shape,e.kind) { when(e.style.shape) {
        "rectangle" -> RectangleShape
        "rounded" -> RoundedCornerShape(12.dp)
        "oval" -> GenericShape { size, _ -> addOval(Rect(0f,0f,size.width,size.height)) }
        "speech" -> GenericShape { size, _ ->
            val w=size.width;val h=size.height
            moveTo(w*.08f,0f);lineTo(w*.92f,0f);quadraticTo(w,0f,w,h*.08f);lineTo(w,h*.72f);quadraticTo(w,h*.82f,w*.92f,h*.82f);lineTo(w*.32f,h*.82f);lineTo(w*.16f,h);lineTo(w*.2f,h*.82f);lineTo(w*.08f,h*.82f);quadraticTo(0f,h*.82f,0f,h*.72f);lineTo(0f,h*.08f);quadraticTo(0f,0f,w*.08f,0f);close()
        }
        "thought" -> GenericShape {size,_->
            val w=size.width;val h=size.height
            addOval(Rect(0f,0f,w,h*.82f));addOval(Rect(w*.12f,h*.77f,w*.24f,h*.9f));addOval(Rect(w*.08f,h*.9f,w*.14f,h))
        }
        "circle" -> GenericShape { size, _ -> val radius=minOf(size.width,size.height)/2f;addOval(Rect(size.width/2-radius,size.height/2-radius,size.width/2+radius,size.height/2+radius)) }
        else -> RoundedCornerShape(if(e.kind=="button") 10.dp else 4.dp)
    } }
    val family=when(e.style.font) { "serif"->FontFamily.Serif;"mono"->FontFamily.Monospace;"sans"->FontFamily.SansSerif;else->FontFamily.Default }
    val align=when(e.style.alignment) { "center"->TextAlign.Center;"end"->TextAlign.End;else->TextAlign.Start }
    val surface=modifier.clip(shape).background(Color(e.backgroundColor).let { it.copy(alpha=it.alpha*e.style.backgroundOpacity) })
        .then(if(e.style.borderWidth>0f) Modifier.border(BorderStroke(e.style.borderWidth.dp,Color(e.style.borderColor)),shape) else Modifier)
    var overflow by androidx.compose.runtime.remember(e.id,e.text,e.style) {androidx.compose.runtime.mutableStateOf(false)}
    @Composable fun Content() {
        Text(if(e.style.showName && e.speakerName.isNotBlank()) "${e.speakerName}\n${e.text}" else e.text,
            modifier=if(e.width>0f && e.style.alignment!="start") Modifier.fillMaxWidth() else Modifier,
            color=Color(e.textColor),fontSize=font*e.style.textScale,lineHeight=font*e.style.textScale*1.25f,fontFamily=family,fontWeight=if(e.style.bold) FontWeight.Bold else FontWeight.Normal,fontStyle=if(e.style.italic) FontStyle.Italic else FontStyle.Normal,textAlign=align,overflow=TextOverflow.Ellipsis,onTextLayout={overflow=it.hasVisualOverflow})
    }
    Box {
    if(e.style.shape in listOf("oval","circle") && e.width>0f && e.height>0f) BoxWithConstraints(surface) {
        val diameter=minOf(maxWidth,maxHeight)
        val insetX=if(e.style.shape=="circle") (maxWidth-diameter*.7071f)/2 else maxWidth*.1465f
        val insetY=if(e.style.shape=="circle") (maxHeight-diameter*.7071f)/2 else maxHeight*.1465f
        Box(Modifier.fillMaxSize().padding(horizontal=maxOf(padding,insetX),vertical=maxOf(padding,insetY))) {Content()}
    } else if(e.style.shape in listOf("speech","thought") && e.height>0f) BoxWithConstraints(surface) {
        Box(Modifier.fillMaxSize().padding(start=maxOf(padding,maxWidth*(if(e.style.shape=="thought") .12f else .06f)),end=maxOf(padding,maxWidth*(if(e.style.shape=="thought") .12f else .06f)),top=padding,bottom=maxHeight*.2f)) {Content()}
    } else Box(surface.padding(padding)) {Content()}
    if(editing && overflow) Text("Texto no cabe",color=MaterialTheme.colorScheme.onErrorContainer,fontSize=font*.6f,modifier=Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.errorContainer))
    }

}

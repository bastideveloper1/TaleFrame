package com.r0ybt.taleframe.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.taleframe.data.Element
import com.r0ybt.taleframe.data.Slide
import com.r0ybt.taleframe.data.boundedPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class BackgroundImage(val bitmap: ImageBitmap? = null, val loading: Boolean = true)

@Composable
fun Player(slide: Slide?, elements: List<Element>, modifier: Modifier = Modifier, navigate: (Long) -> Unit) {
    Box(modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center) {
        Crossfade(targetState=slide,animationSpec=tween(300),label="Cambio de lámina") { current ->
            if(current != null) SlideCanvas(current,elements.filter { it.slideId==current.id },Modifier.fillMaxSize(),onNavigate=navigate)
        }
    }
}

/** Both modes use exactly the same 3:4 stage; letterboxing preserves positions. */
@Composable
fun SlideCanvas(slide: Slide, elements: List<Element>, modifier: Modifier = Modifier, editing: Boolean = false,
    onSelect: (Element) -> Unit = {}, onMove: (Element) -> Unit = {}, onNavigate: (Long) -> Unit = {}) {
    BoxWithConstraints(modifier.background(Color(0xFF101014)),contentAlignment=Alignment.Center) {
        val width = minOf(maxWidth,maxHeight * .75f)
        val height = width / .75f
        var stageSize by remember { mutableStateOf(IntSize.Zero) }
        val image by produceState(BackgroundImage(),slide.image) {
            value = BackgroundImage()
            val path = slide.image
            val decoded = if(path != null) withContext(Dispatchers.IO) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(path,bounds)
                    var sample = 1
                    while(bounds.outWidth/sample > 2048 || bounds.outHeight/sample > 2048) sample *= 2
                    BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample })?.asImageBitmap()
                } catch(_: Exception) { null }
            } else null
            value = BackgroundImage(decoded, false)
        }
        Box(Modifier.size(width,height).onSizeChanged { stageSize=it }.clip(RoundedCornerShape(if(editing) 8.dp else 0.dp)).background(Color(slide.color))) {
            image.bitmap?.let { Image(it,contentDescription="Fondo de ${slide.name}",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
            if(slide.image != null && image.bitmap == null && editing) Text(if(image.loading) "Cargando fondo…" else "No se pudo leer el fondo",color=Color.Gray,modifier=Modifier.padding(8.dp),fontSize=12.sp)
            elements.forEach { element -> key(element.id) {
                PositionedElement(element,stageSize,editing,onSelect,onMove,onNavigate)
            } }
        }
    }
}

@Composable
private fun PositionedElement(element: Element, stage: IntSize, editing: Boolean, onSelect: (Element) -> Unit,
    onMove: (Element) -> Unit, onNavigate: (Long) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var x by remember(element.x) { mutableFloatStateOf(element.x) }
    var y by remember(element.y) { mutableFloatStateOf(element.y) }
    val latestElement by rememberUpdatedState(element)
    val latestMove by rememberUpdatedState(onMove)
    val density=LocalDensity.current
    val maxWidth = with(density) { (stage.width * .8f).toDp() }
    val maxHeight = with(density) { stage.height.toDp() }
    // Typography scales with the stage, so editor and player reconstruct the same layout.
    val fontSize = with(density) { (stage.width * .045f).toSp() }
    val inset = with(density) { (stage.width * .025f).toDp() }
    val travelX=(stage.width-size.width).coerceAtLeast(1)
    val travelY=(stage.height-size.height).coerceAtLeast(1)
    val drag = if(editing) Modifier.pointerInput(stage,size) {
        detectDragGesturesAfterLongPress(
            onDragEnd={latestMove(latestElement.copy(x=x,y=y))},
            onDragCancel={ x=latestElement.x; y=latestElement.y },
            onDrag={ change,delta -> change.consume(); x=boundedPosition(x+delta.x/travelX); y=boundedPosition(y+delta.y/travelY) }
        )
    } else Modifier
    Box(Modifier.offset { IntOffset((x*travelX).roundToInt(),(y*travelY).roundToInt()) }
        .widthIn(max=maxWidth).heightIn(max=maxHeight).onSizeChanged { size=it }
        .then(drag).clip(RoundedCornerShape(if(element.kind=="button") 10.dp else 4.dp))
        .background(Color(element.backgroundColor))
        .clickable(enabled=editing || (element.kind=="button" && element.targetId!=null)) {
            if(editing) onSelect(latestElement) else latestElement.targetId?.let(onNavigate)
        }.padding(inset)) {
        Text(element.text,color=Color(element.textColor),fontSize=fontSize,lineHeight=fontSize*1.25f)
    }
}

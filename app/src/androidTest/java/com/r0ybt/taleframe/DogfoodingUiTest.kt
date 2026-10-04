package com.r0ybt.taleframe

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class DogfoodingUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cacheHitIsVisibleInTheVeryFirstComposition() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(ctx.cacheDir,"${UUID.randomUUID()}.png")
        try {
            Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).also {b->b.eraseColor(0xFF336699.toInt());file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            assertNotNull(LocalImageCache.load(file.path,384))
            var first:Boolean?=null
            compose.setContent {val image=localImage(file.path,384);SideEffect {if(first==null) first=image!=null}}
            compose.runOnIdle {assertEquals(true,first)}
        } finally {file.delete()}
    }
    @Test fun doubleTapSelectsCorrectElementAndQuickTextDoesNotDelayDragOrSwipe() {
        var e by mutableStateOf(Element(1,1,"text","Texto",x=.1f,y=.1f,width=.3f,height=.15f))
        var selected by mutableStateOf<Long?>(null);var context by mutableStateOf<Element?>(null);var text by mutableStateOf(false)
        var swipes=0;var commits=0
        compose.setContent {MaterialTheme {
            SlideCanvas(Slide(1,1,"A"),listOf(e),Modifier.fillMaxSize(),editing=true,selectedId=selected,onSelect={selected=it?.id},onContext={context=it},onMove={commits++;e=it},onSwipe={swipes+=it})
            context?.let {AlertDialog(onDismissRequest={context=null},title={Text("Contexto ${it.id}")},confirmButton={TextButton(onClick={text=true;context=null}){Text("Editar texto")}})}
            if(text) QuickTextDialog(e.text,{text=false}) {e=e.copy(text=it);text=false}
        }}
        compose.onNodeWithTag("element-1").performTouchInput {doubleClick()}
        compose.onNodeWithText("Contexto 1").assertExists();assertEquals(1L,selected)
        compose.onNodeWithText("Editar texto").performClick();compose.onNode(hasSetTextAction()).performTextReplacement("Nuevo");compose.onNodeWithText("Guardar").performClick();assertEquals("Nuevo",e.text)
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("stage").performTouchInput {down(b.center-stage.topLeft);advanceEventTime(16);moveBy(Offset(220f,0f));up()}
        assertEquals(1,commits);assertEquals(0,swipes)
        compose.onNodeWithTag("stage").performTouchInput {swipe(Offset(width*.85f,height*.85f),Offset(width*.1f,height*.85f),300)}
        assertEquals(1,swipes)
        compose.onNodeWithTag("stage").performTouchInput {down(Offset(width*.8f,height*.8f));advanceEventTime(16);moveBy(Offset(-15f,0f));up()}
        assertEquals(1,swipes)
    }
    @Test fun denseMediaCanvasWithGridKeepsDragInMemoryAndDoesNotRecreateVideoOwner() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val files=listOf("local-animation.gif","local-video.mp4").map {name->File(ctx.cacheDir,"${UUID.randomUUID()}-$name").also {f->InstrumentationRegistry.getInstrumentation().context.assets.open(name).use {input->f.outputStream().use {input.copyTo(it)}}}}
        var elements by mutableStateOf((1L..40L).map {Element(it,1,"image","",image=files[0].path,width=.07f,height=.07f,x=(it%8)/9f,y=(it/8)/8f)}+
            (41L..43L).map {Element(it,1,"image","",image=files[0].path,width=.12f,height=.12f,x=.2f*(it-40),y=.7f,media=MediaOptions("gif"))}+
            Element(44,1,"image","",image=files[1].path,width=.2f,height=.15f,x=.1f,y=.88f,media=MediaOptions("video"))+
            Element(45,1,"text","Mover",width=.2f,height=.15f,x=.7f,y=.9f))
        var selected by mutableStateOf<Long?>(null);var commits=0;var shown by mutableStateOf(true)
        try {
            compose.setContent {MaterialTheme {if(shown) SlideCanvas(Slide(1,1,"Densa"),elements,Modifier.fillMaxSize(),editing=true,grid="Fina",selectedId=selected,onSelect={selected=it?.id},onMove={e->commits++;elements=elements.map {if(it.id==e.id) e else it}})}}
            compose.waitUntil(10_000) {MediaDiagnostics.allocated>0}
            val created=MediaDiagnostics.created
            val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            val b=compose.onNodeWithTag("element-45").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("stage").performTouchInput {down(b.center-stage.topLeft);advanceEventTime(16);moveBy(Offset(-80f,-80f))}
            compose.runOnIdle {assertEquals(0,commits)}
            compose.onNodeWithTag("stage").performTouchInput {moveBy(Offset(-30f,-30f));up()}
            compose.runOnIdle {assertEquals(1,commits);assertEquals(created,MediaDiagnostics.created);shown=false}
            compose.waitForIdle();assertEquals(0,MediaDiagnostics.allocated)
        } finally {compose.runOnIdle {shown=false};files.forEach {it.delete()}}
    }
    @Test fun albumRetainsScrollAndLongDragReportsStableIdAndInsertion() {
        val slides=(1L..45L).map {Slide(it,1,"Página $it")}
        var editing by mutableStateOf(false);var moved:Pair<Long,Int>?=null
        compose.setContent {MaterialTheme {
            val state=rememberLazyGridState()
            if(editing) TextButton(onClick={editing=false}) {Text("Volver al álbum")}
            else Album(slides,emptyList(),"Pequeña",Modifier.fillMaxSize(),open={editing=true},rename={},delete={},duplicate={},reorder={_,_->},state=state,moveTo={s,index->moved=s.id to index})
        }}
        compose.onNodeWithTag("album").performScrollToIndex(36)
        compose.onNodeWithTag("album-38").performClick();compose.onNodeWithText("Volver al álbum").performClick();compose.onNodeWithTag("album-38").assertIsDisplayed()
        val first=compose.onNodeWithTag("album-37").fetchSemanticsNode().boundsInRoot
        val next=compose.onNodeWithTag("album-38").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("album-37").performTouchInput {down(center);advanceEventTime(650);moveBy(next.center-first.center);advanceEventTime(16);up()}
        assertEquals(37L,moved?.first);assertEquals(37,moved?.second)
    }
    @Test fun fullDestinationClearlyMarksCurrentAndAllowsSelfLinkByStableId() {
        var selected:Long?=null
        val slides=listOf(Slide(91,1,"Actual"),Slide(7,1,"Rama",draft=true))
        compose.setContent {MaterialTheme {DestinationDialog(slides,emptyList(),null,91,{}, {selected=it})}}
        compose.onNodeWithText("ACTUAL").assertExists();compose.onNodeWithText("Borrador").assertExists()
        compose.onNodeWithTag("destination-91").performClick();assertEquals(91L,selected)
        compose.onNodeWithTag("destination-7").performClick();assertEquals(7L,selected)
    }
    @Test fun gridAndToolOrderPersistAfterRemovingAndRecreatingToolbar() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences("dogfood-tools-${UUID.randomUUID()}",0)
        var shown by mutableStateOf(true);var generation by mutableIntStateOf(0)
        compose.setContent {MaterialTheme {
            if(shown) key(generation) {
                var grid by remember {mutableStateOf(prefs.getString("grid","Off") ?: "Off")}
                Column {SlideCanvas(Slide(1,1,"A"),emptyList(),Modifier.weight(1f),editing=true,grid=grid)
                    EditorToolbar(listOf(EditorTool("text","Texto"){},EditorTool("image","Imagen"){}),prefs,true,{}, {},{},grid,{grid=it;prefs.edit().putString("grid",it).apply()})}
            }
        }}
        compose.onNodeWithText("▦ Cuadrícula: Off").performClick();compose.onNodeWithText("Fina").performClick();compose.onNodeWithTag("editor-grid").assertExists()
        compose.onNodeWithText("Imagen").performScrollTo().performTouchInput {longClick()};compose.onNodeWithText("Mover antes").performClick()
        assertEquals("image|text",prefs.getString("tool_order",null))
        compose.runOnIdle {generation++};compose.onNodeWithTag("editor-grid").assertExists();assertEquals("Fina",prefs.getString("grid",null))
        compose.runOnIdle {shown=false};prefs.edit().clear().commit()
    }
    @Test fun allFourTransitionsAcrossColorsImagesGifAndVideoAvoidWhiteFrames() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val files=listOf(0xFFCC2233.toInt(),0xFF2266CC.toInt()).map {color ->File(ctx.cacheDir,"${UUID.randomUUID()}.png").also {file->Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).also {b->b.eraseColor(color);file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}}
        val mediaFiles=listOf("transition-animation.gif","transition-video.mp4").map {name ->File(ctx.cacheDir,"${UUID.randomUUID()}-$name").also {file->InstrumentationRegistry.getInstrumentation().context.assets.open(name).use {input->file.outputStream().use {input.copyTo(it)}}}}
        try {
            compose.mainClock.autoAdvance=false
            var landscape by mutableStateOf(false)
            var type by mutableStateOf("none");var generation by mutableIntStateOf(0);var current by mutableStateOf(Slide(1,1,"A",image=files[0].path))
            var destination by mutableStateOf(Slide(2,1,"B",image=files[1].path))
            var navigations=0
            compose.setContent {MaterialTheme {key(generation) {Player(current,listOf(
                Element(1,1,"button","Ir",y=.8f,width=.3f,height=.1f,targetId=2,transition=Transition(type,300)),
                Element(2,2,"button","Volver",y=.8f,width=.3f,height=.1f,targetId=1,transition=Transition(type,300))),(if(landscape) Modifier.width(400.dp).height(240.dp) else Modifier.fillMaxSize()).testTag("player-test"),
                slides=listOf(Slide(1,1,"A",image=files[0].path),destination)) {navigations++;current=if(it==1L) Slide(1,1,"A",image=files[0].path) else destination}}}}
            for(case in listOf(false to "color",false to "image",false to "gif",false to "video",true to "image")) for(effect in listOf("none","fade","left","right")) {
                val count=navigations
                val media=case.second
                compose.runOnIdle {
                    landscape=case.first
                    type=effect;generation++;current=Slide(1,1,"A",image=files[0].path)
                    destination=Slide(2,1,"B",color=if(media=="color") 0xFF226699.toInt() else -1,image=when(media) {"image"->files[1].path;"gif"->mediaFiles[0].path;"video"->mediaFiles[1].path;else->null},media=MediaOptions(if(media=="color") "image" else media))
                }
                compose.mainClock.advanceTimeBy(32)
                compose.waitUntil(10_000) {LocalImageCache.peek(files[0].path,1536)!=null}
                compose.mainClock.advanceTimeBy(32)
                compose.onNodeWithText("Ir").performClick();compose.mainClock.advanceTimeBy(32)
                compose.waitUntil(10_000) {navigations>count}
                repeat(8) {
                    compose.mainClock.advanceTimeBy(48);compose.waitForIdle()
                    val pixels=compose.onNodeWithTag("player-test").captureToImage().toPixelMap()
                    val color=pixels[pixels.width/2,pixels.height/2]
                    assertFalse("White frame during $media/$effect at $it: $color",color.red>.9f && color.green>.9f && color.blue>.9f)
                }
                compose.onNodeWithText("Volver").performClick();compose.mainClock.advanceTimeBy(32)
                compose.waitUntil(10_000) {navigations>count+1}
                repeat(8) {
                    compose.mainClock.advanceTimeBy(48);compose.waitForIdle()
                    val pixels=compose.onNodeWithTag("player-test").captureToImage().toPixelMap();val color=pixels[pixels.width/2,pixels.height/2]
                    assertFalse("White outgoing frame during $media/$effect",color.red>.9f && color.green>.9f && color.blue>.9f)
                }
            }
        } finally {(files+mediaFiles).forEach {it.delete()}}
    }
}

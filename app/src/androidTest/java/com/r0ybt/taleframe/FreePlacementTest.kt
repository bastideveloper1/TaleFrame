package com.r0ybt.taleframe

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class FreePlacementTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageClipsIdenticallyInEditorPlaybackAndExportedProjectAfterReopen() {
        val name="free-${UUID.randomUUID()}.db"
        val source=File(context.cacheDir,"${UUID.randomUUID()}.png")
        Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888).also {b ->
            for(y in 0 until 40) for(x in 0 until 30) b.setPixel(x,y,if(x<15) android.graphics.Color.RED else android.graphics.Color.BLUE)
            source.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        }
        var repo=StoryRepository(context,name)
        try {
            val project=repo.createProject("Encuadre")
            val slideId=repo.createSlide(project,"Tarjeta")
            val path=repo.importImage(Uri.fromFile(source))
            val legacyId=repo.saveElement(Element(0,slideId,"image","",image=path,x=.5f,y=.25f,width=.5f,height=.25f))
            val id=repo.saveElement(Element(0,slideId,"image","",image=path,width=2f,height=2f,x=-.5f,y=-.5f,freePosition=true))
            repo.moveElement(id,-.6f,-.7f,true)
            repo.resizeElement(id,2f,2f,-.5f,-.5f,true)
            repo.editElement(repo.read().elements.first {it.id==id},repo.read().elements.first {it.id==id}.copy(opacity=.9f))
            repo.editElement(repo.read().elements.first {it.id==id},repo.read().elements.first {it.id==id}.copy(opacity=1f))
            val saved=repo.read().elements.first {it.id==id}
            repo.close();repo=StoryRepository(context,name)
            assertEquals(saved,repo.read().elements.first {it.id==id})
            assertEquals(.5f,repo.read().elements.first {it.id==legacyId}.x,0f)
            val backup=ProjectBackup(context,repo)
            val output=ByteArrayOutputStream();backup.export(project,output)
            val restored=backup.prepare(ByteArrayInputStream(output.toByteArray())).use {backup.restore(it,"Copia")}
            val restoredSlide=repo.read().slides.single {it.projectId==restored}
            val restoredElement=repo.read().elements.single {it.slideId==restoredSlide.id && it.width==2f}
            assertEquals(saved.x,restoredElement.x,0f);assertEquals(saved.y,restoredElement.y,0f)
            assertEquals(saved.width,restoredElement.width,0f);assertEquals(saved.height,restoredElement.height,0f);assertTrue(restoredElement.freePosition)
            var e by mutableStateOf(saved)
            var editing by mutableStateOf(true)
            compose.setContent {MaterialTheme {SlideCanvas(Slide(1,1,"Tarjeta"),listOf(e),Modifier.fillMaxSize().testTag("canvas"),editing=editing)}}
            compose.waitUntil(10_000) {LocalImageCache.peek(requireNotNull(saved.image),1536)!=null}
            fun checkPixels() {
                val stage=compose.onNodeWithTag("stage")
                val pixels=stage.captureToImage().toPixelMap()
                val left=pixels[pixels.width/4,pixels.height/2]
                val right=pixels[pixels.width*3/4,pixels.height/2]
                assertTrue("Left crop: $left",left.red>.9f && left.blue<.1f)
                assertTrue("Right crop: $right",right.blue>.9f && right.red<.1f)
                val bounds=stage.fetchSemanticsNode().boundsInRoot
                val rootBounds=compose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
                val root=compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
                // Letterbox pixels immediately outside the card must not contain overflowing media.
                val points=listOf(Offset(bounds.left-4,bounds.center.y),Offset(bounds.right+4,bounds.center.y),Offset(bounds.center.x,bounds.top-4),Offset(bounds.center.x,bounds.bottom+4))
                var checked=0
                for(point in points) if(rootBounds.contains(point)) {
                    val color=root[(point.x-rootBounds.left).toInt(),(point.y-rootBounds.top).toInt()]
                    assertFalse("Media outside card: $color",(color.red>.9f && color.blue<.1f) || (color.blue>.9f && color.red<.1f));checked++
                }
                assertTrue("Need a letterbox sample",checked>0)
            }
            checkPixels()
            compose.runOnIdle {editing=false};checkPixels()
            compose.runOnIdle {e=restoredElement}
            compose.waitUntil(10_000) {LocalImageCache.peek(requireNotNull(restoredElement.image),1536)!=null};checkPixels()
        } finally {repo.close();context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively();source.delete()}
    }

    @Test fun freeImageDragResizeReturnAndContextMenuKeepSelectionAndLockBehavior() {
        var e by mutableStateOf(Element(1,1,"image","",image="/missing",width=.8f,height=.6f,x=.4f,y=.3f))
        var selected by mutableStateOf<Long?>(1)
        var moves=0;var resizes=0;var menus=0
        compose.setContent {MaterialTheme {SlideCanvas(Slide(1,1,"A"),listOf(e),Modifier.fillMaxSize(),editing=true,
            selectedId=selected,onSelect={selected=it?.id},onMove={e=it;moves++},onResize={e=it;resizes++},onContext={menus++})}}
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        fun resize(delta:Offset) {
            val handle=compose.onNodeWithTag("resize-handle-1").fetchSemanticsNode().boundsInRoot.center-stage.topLeft
            compose.onNodeWithTag("stage").performTouchInput {down(handle);advanceEventTime(16);moveBy(delta);up()}
        }
        resize(Offset(stage.width,stage.height))
        compose.runOnIdle {assertTrue(e.width>1f);assertTrue(e.height>1f);assertEquals(1,resizes)}
        compose.onNodeWithTag("stage").performTouchInput {down(Offset(stage.width*.3f,stage.height*.3f));advanceEventTime(16);moveBy(Offset(-stage.width*.4f,-stage.height*.4f));up()}
        compose.runOnIdle {assertTrue(e.x<0);assertTrue(e.y<0);assertEquals(1,moves);assertEquals(1L,selected)}
        compose.onNodeWithTag("stage").performTouchInput {doubleClick(Offset(stage.width*.3f,stage.height*.3f))}
        compose.runOnIdle {assertEquals(1,menus)}
        resize(Offset(-stage.width*.95f,-stage.height*.95f))
        compose.runOnIdle {assertEquals(2,resizes);assertTrue(e.width<1f);assertTrue(e.height<1f)}
        val b=elementBounds(e,stage.width,stage.height,0f,0f)
        compose.onNodeWithTag("stage").performTouchInput {down(Offset(10f,10f));advanceEventTime(16);moveBy(Offset(-b.left+stage.width*.1f,-b.top+stage.height*.1f));up()}
        compose.runOnIdle {assertTrue(e.x>=0);assertTrue(e.y>=0);e=e.copy(locked=true)}
        val locked=e
        compose.onNodeWithTag("stage").performTouchInput {down(Offset(stage.width*.2f,stage.height*.2f));advanceEventTime(16);moveBy(Offset(80f,80f));up()}
        compose.runOnIdle {assertEquals(locked,e);assertEquals(1L,selected);e=e.copy(locked=false)}
        compose.onNodeWithTag("stage").performTouchInput {down(Offset(stage.width*.2f,stage.height*.2f));advanceEventTime(16);moveBy(Offset(stage.width*2,stage.height*2));up()}
        compose.onNodeWithTag("offstage-move-1").assertExists()
        compose.runOnIdle {e=e.copy(locked=true)}
        compose.onNodeWithTag("stage").performTouchInput {doubleClick(center)}
        compose.runOnIdle {assertEquals(2,menus);e=e.copy(locked=false)}
        val returnDelta=Offset((.1f-e.x)*stage.width,(.1f-e.y)*stage.height)
        compose.onNodeWithTag("stage").performTouchInput {down(center);advanceEventTime(16);moveBy(returnDelta);up()}
        compose.runOnIdle {assertEquals(.1f,e.x,.001f);assertEquals(.1f,e.y,.001f)}
        compose.onNodeWithTag("offstage-move-1").assertDoesNotExist()
    }

    @Test fun oversizedGifKeepsAnimatingAndSameDrawableAcrossMoveAndResize() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=28)
        val file=File(context.cacheDir,"${UUID.randomUUID()}.gif")
        InstrumentationRegistry.getInstrumentation().context.assets.open("transition-animation.gif").use {input->file.outputStream().use {input.copyTo(it)}}
        var e by mutableStateOf(Element(1,1,"image","",image=file.path,media=MediaOptions("gif"),width=2f,height=2f,x=-.5f,y=-.5f,freePosition=true))
        var shown by mutableStateOf(true)
        val name="free-gif-${UUID.randomUUID()}.db"
        var repo=StoryRepository(context,name)
        val project=repo.createProject("GIF")
        val slideId=repo.createSlide(project,"GIF")
        val path=repo.importMedia(Uri.fromFile(file),"gif")
        val id=repo.saveElement(e.copy(id=0,slideId=slideId,image=path))
        e=repo.read().elements.single {it.id==id}
        var drawable:AnimatedImageDrawable?=null
        fun find(v:View):AnimatedImageDrawable? {
            if(v is ImageView && v.drawable is AnimatedImageDrawable) return v.drawable as AnimatedImageDrawable
            if(v is ViewGroup) for(i in 0 until v.childCount) find(v.getChildAt(i))?.let {return it}
            return null
        }
        try {
            compose.setContent {MaterialTheme {if(shown) SlideCanvas(Slide(1,1,"GIF"),listOf(e),Modifier.fillMaxSize().testTag("gif-canvas"),editing=true,selectedId=id,
                onMove={repo.moveElement(it.id,it.x,it.y,it.freePosition);e=repo.read().elements.single {row->row.id==id}},
                onResize={repo.resizeElement(it.id,it.width,it.height,it.x,it.y,it.freePosition);e=repo.read().elements.single {row->row.id==id}})}}
            compose.waitUntil(10_000) {compose.runOnUiThread {drawable=find(compose.activity.window.decorView)};drawable?.isRunning==true}
            val original=requireNotNull(drawable)
            val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("stage").performTouchInput {down(Offset(stage.width*.3f,stage.height*.3f));advanceEventTime(16);moveBy(Offset(40f,30f));up()}
            val handle=compose.onNodeWithTag("resize-handle-$id").fetchSemanticsNode().boundsInRoot.center-stage.topLeft
            compose.onNodeWithTag("stage").performTouchInput {down(handle);advanceEventTime(16);moveBy(Offset(80f,60f));up()}
            compose.runOnUiThread {assertSame(original,find(compose.activity.window.decorView));assertTrue(original.isRunning)}
            val initial=compose.onNodeWithTag("stage").captureToImage().toPixelMap()
            val first=initial[initial.width/2,initial.height/2]
            compose.waitUntil(5_000) {
                val pixels=compose.onNodeWithTag("stage").captureToImage().toPixelMap()
                pixels[pixels.width/2,pixels.height/2]!=first
            }
            val canvasBounds=compose.onNodeWithTag("gif-canvas").fetchSemanticsNode().boundsInRoot
            val canvas=compose.onNodeWithTag("gif-canvas").captureToImage().toPixelMap()
            val outside=canvas[0,0]
            if(stage.top-canvasBounds.top>4) assertEquals(outside,canvas[(stage.center.x-canvasBounds.left).toInt(),(stage.top-canvasBounds.top-4).toInt()])
            val saved=e
            repo.close();repo=StoryRepository(context,name)
            assertEquals(saved,repo.read().elements.single {it.id==id})
            val backup=ProjectBackup(context,repo)
            val output=ByteArrayOutputStream();backup.export(project,output)
            val restored=backup.prepare(ByteArrayInputStream(output.toByteArray())).use {backup.restore(it,"GIF copia")}
            val target=repo.read().slides.single {it.projectId==restored}
            val restoredGif=repo.read().elements.single {it.slideId==target.id}
            assertEquals(saved.copy(id=restoredGif.id,slideId=restoredGif.slideId,image=restoredGif.image),restoredGif)
            assertArrayEquals(file.readBytes(),File(requireNotNull(restoredGif.image)).readBytes())
            compose.runOnIdle {shown=false};compose.waitForIdle();assertFalse(original.isRunning)
        } finally {compose.runOnIdle {shown=false};repo.close();context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively();file.delete()}
    }
}

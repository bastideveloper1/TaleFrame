package com.r0ybt.taleframe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun buttonCancelsPreviousTimerAndOnlyDestinationTimerRuns() {
        compose.mainClock.autoAdvance=false
        val a=Slide(1,1,"A",autoEnabled=true,autoSeconds=1f,autoTargetId=3)
        val b=Slide(2,1,"B")
        var current by mutableStateOf(a)
        val visits=mutableListOf<Long>()
        val button=Element(1,1,"button","Salir",targetId=2,transition=Transition("left",400),width=.4f,height=.2f)
        compose.setContent { MaterialTheme { Player(current,listOf(button)) { visits+=it; current=b } } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Salir").performClick()
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000); compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(2L),visits) }
    }
    @Test fun automaticActionFiresOnceAndSelfNavigationStartsNewVisit() {
        compose.mainClock.autoAdvance=false
        val slide=Slide(1,1,"Auto",autoEnabled=true,autoSeconds=.2f,autoTargetId=1,transition=Transition("none"))
        var visits=0
        var shown by mutableStateOf(true)
        compose.setContent { MaterialTheme { if(shown) Player(slide,emptyList()) { visits++ } } }
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        compose.runOnIdle { assertTrue(visits>=1); shown=false }
        val count=visits
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertEquals(count,visits)
    }
    @Test fun sequenceChangesFrameHoldsLastAndPreservesOneLayerGeometry() {
        compose.mainClock.autoAdvance=false
        val element=Element(1,1,"image","",image="/missing",width=.4f,height=.4f,rotation=20f,opacity=.6f,
            media=MediaOptions("slideshow",loop=false,frames=listOf("/a","/b","/c"),seconds=.2f))
        compose.setContent { MaterialTheme { SlideCanvas(Slide(1,1,"Secuencia"),listOf(element),Modifier.fillMaxSize()) } }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithContentDescription("Imagen 1 de 3",useUnmergedTree=true).assertExists()
        val before=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(220); compose.waitForIdle()
        compose.onNodeWithContentDescription("Imagen 2 de 3",useUnmergedTree=true).assertExists()
        compose.mainClock.advanceTimeBy(220); compose.waitForIdle()
        compose.onNodeWithContentDescription("Imagen 3 de 3",useUnmergedTree=true).assertExists()
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.onNodeWithContentDescription("Imagen 3 de 3",useUnmergedTree=true).assertExists()
        assertEquals(before,compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot)
        compose.onAllNodesWithTag("element-1").assertCountEquals(1)
    }
    @Test fun gifAnimatesAndStopsItsDrawableWhenLeaving() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 28)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"${UUID.randomUUID()}.gif")
        InstrumentationRegistry.getInstrumentation().context.assets.open("local-animation.gif").use { input -> file.outputStream().use { input.copyTo(it) } }
        val decoded=android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(file))
        assertTrue("GIF asset must animate: ${decoded.javaClass}",decoded is android.graphics.drawable.AnimatedImageDrawable)
        var shown by mutableStateOf(true)
        compose.setContent { MaterialTheme { if (shown) LocalMedia(file.path,MediaOptions("gif"),Modifier.fillMaxSize()) } }
        var drawable: android.graphics.drawable.AnimatedImageDrawable?=null
        fun find(view: android.view.View): android.graphics.drawable.AnimatedImageDrawable? {
            if (view is android.widget.ImageView && view.drawable is android.graphics.drawable.AnimatedImageDrawable) return view.drawable as android.graphics.drawable.AnimatedImageDrawable
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.waitUntil(10_000) { compose.runOnUiThread { drawable=find(compose.activity.window.decorView) }; drawable?.isRunning==true }
        compose.runOnIdle { shown=false }
        compose.waitForIdle()
        assertFalse(requireNotNull(drawable).isRunning)
        file.delete()
    }
    @Test fun leavingCanvasReleasesAudioVideoAndDraggingDoesNotRecreatePlayers() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun fixture(asset: String)=File(context.cacheDir,"${UUID.randomUUID()}-$asset").also { file -> InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } } }
        val video=fixture("local-video.mp4"); val audio=fixture("local-audio.wav")
        var shown by mutableStateOf(true)
        var element by mutableStateOf(Element(1,1,"image","",image=video.path,media=MediaOptions("video"),width=.4f,height=.4f))
        var selected by mutableStateOf<Long?>(null)
        val baseline=MediaDiagnostics.allocated
        compose.setContent { MaterialTheme { if(shown) Column {
            SlideCanvas(Slide(1,1,"Video",audio=audio.path),listOf(element),Modifier.fillMaxSize(),editing=true,onSelect={ selected=it?.id },onMove={element=it},selectedId=selected)
            // Audio belongs to Play; exercise the same disposable owner separately.
            SlideAudio(Slide(1,1,"Audio",audio=audio.path),false)
        } } }
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline+2 }
        val created=MediaDiagnostics.created
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val before=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("stage").performTouchInput { down(before.center-stage.topLeft); advanceEventTime(16); moveBy(Offset(100f,80f)); up() }
        compose.runOnIdle { assertTrue(element.x>.1f); assertEquals(created,MediaDiagnostics.created); shown=false }
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline }
        compose.runOnIdle { shown=true }
        compose.waitUntil(10_000) { MediaDiagnostics.allocated>baseline }
        compose.runOnIdle { shown=false }
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline }
        compose.runOnIdle { shown=true }
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline+2 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline+2 }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { MediaDiagnostics.allocated==baseline }
        video.delete(); audio.delete()
    }
}

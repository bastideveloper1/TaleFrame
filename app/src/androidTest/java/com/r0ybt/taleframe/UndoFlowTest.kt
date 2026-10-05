package com.r0ybt.taleframe

import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.state.StoryViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class UndoFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[StoryViewModel::class.java]
    private fun tap(text:String) {
        val node=compose.onNodeWithText(text)
        try {node.assertIsDisplayed()} catch(_:AssertionError) {node.performScrollTo()}
        node.performClick();compose.waitForIdle()
    }
    private fun waitText(text:String) {compose.waitUntil(10_000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun count(n:Int) {compose.waitUntil(10_000) {model().undoState.value.count==n}}
    private fun back() {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    private fun menu(id:Long) {compose.onNodeWithTag("element-$id").performTouchInput {doubleClick()};compose.onNodeWithText("Propiedades").assertExists()}
    private fun drawable(view:View):AnimatedImageDrawable? {
        if(view is ImageView && view.drawable is AnimatedImageDrawable) return view.drawable as AnimatedImageDrawable
        if(view is ViewGroup) for(i in 0 until view.childCount) drawable(view.getChildAt(i))?.let {return it}
        return null
    }

    @Test fun realEditorUndoGroupsGesturesRestoresGifTextDeleteAndClearsAcrossProjects() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=28)
        val repo=StoryRepository(compose.activity)
        val file=File(compose.activity.cacheDir,"${UUID.randomUUID()}.gif")
        InstrumentationRegistry.getInstrumentation().context.assets.open("transition-animation.gif").use {input->file.outputStream().use {input.copyTo(it)}}
        val name="Undo UI ${System.nanoTime()}";val otherName="$name otro"
        val slideName="Encuadre Undo";val otherSlideName="Otro editor Undo"
        var project=0L;var otherProject=0L;var slide=0L;var gifId=0L;var textId=0L;var otherSlide=0L
        try {
            waitText("+ Crear proyecto")
            model().edit {
                project=createProject(name);slide=createSlide(project,slideName)
                val path=importMedia(Uri.fromFile(file),"gif")
                gifId=saveElement(Element(0,slide,"image","",image=path,sourceName="GIF Undo",media=MediaOptions("gif"),freePosition=true,
                    width=2f,height=2f,x=-.35f,y=-.3f))
                textId=saveElement(Element(0,slide,"text","Texto anterior",x=.2f,y=.15f,width=.4f,height=.12f))
                otherProject=createProject(otherName);otherSlide=createSlide(otherProject,otherSlideName)
            }
            waitText(name);tap(name);tap(slideName)
            compose.waitUntil(10_000) {model().undoState.value.slideId==slide}
            compose.onNodeWithText("Deshacer").assertIsNotEnabled()
            val original=repo.read().elements.first {it.id==gifId}
            var animated:AnimatedImageDrawable?=null
            compose.waitUntil(10_000) {compose.runOnUiThread {animated=drawable(compose.activity.window.decorView)};animated?.isRunning==true}
            val owner=requireNotNull(animated)
            val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("stage").performTouchInput {
                down(Offset(stage.width*.3f,stage.height*.4f));advanceEventTime(16);moveBy(Offset(40f,25f));advanceEventTime(500);moveBy(Offset(40f,25f))
            }
            assertEquals(0,model().undoState.value.count)
            compose.onNodeWithTag("stage").performTouchInput {advanceEventTime(500);moveBy(Offset(40f,25f));up()}
            count(1);compose.onNodeWithText("Deshacer").assertIsEnabled()
            assertNotEquals(original,repo.read().elements.first {it.id==gifId})
            tap("Deshacer");count(0);assertEquals(original,repo.read().elements.first {it.id==gifId})
            compose.runOnUiThread {assertSame(owner,drawable(compose.activity.window.decorView));assertTrue(owner.isRunning)}
            compose.onNodeWithText("Deshacer").assertIsNotEnabled()

            val handle=compose.onNodeWithTag("resize-handle-$gifId").fetchSemanticsNode().boundsInRoot.center-stage.topLeft
            compose.onNodeWithTag("stage").performTouchInput {down(handle);advanceEventTime(16);moveBy(Offset(50f,40f));advanceEventTime(700);moveBy(Offset(50f,40f))}
            assertEquals(0,model().undoState.value.count)
            compose.onNodeWithTag("stage").performTouchInput {up()};count(1)
            assertTrue(repo.read().elements.first {it.id==gifId}.width>original.width)
            tap("Deshacer");count(0);assertEquals(original,repo.read().elements.first {it.id==gifId})
            compose.runOnUiThread {assertSame(owner,drawable(compose.activity.window.decorView));assertTrue(owner.isRunning)}

            val originalText=repo.read().elements.first {it.id==textId}
            menu(textId);tap("Editar texto")
            compose.onNode(hasSetTextAction()).assertIsFocused().performTextReplacement("Primer cambio")
            compose.onNode(hasSetTextAction()).performTextReplacement("Texto confirmado")
            assertEquals(0,model().undoState.value.count)
            tap("Guardar");count(1)
            assertEquals("Texto confirmado",repo.read().elements.first {it.id==textId}.text)
            tap("Deshacer");count(0);assertEquals(originalText,repo.read().elements.first {it.id==textId})

            menu(textId);tap("Duplicar");count(1);assertEquals(3,repo.read().elements.count {it.slideId==slide})
            tap("Deshacer");count(0);assertEquals(2,repo.read().elements.count {it.slideId==slide})
            menu(gifId);tap("Traer al frente");count(1)
            tap("Deshacer");count(0);assertEquals(original.layer,repo.read().elements.first {it.id==gifId}.layer)
            menu(gifId);tap("Bloquear");count(1);assertTrue(repo.read().elements.first {it.id==gifId}.locked)
            tap("Deshacer");count(0);assertEquals(original,repo.read().elements.first {it.id==gifId})

            menu(gifId);tap("Eliminar");tap("Eliminar");count(1)
            assertTrue(repo.read().elements.none {it.id==gifId});assertTrue(File(requireNotNull(original.image)).exists())
            tap("Deshacer");count(0);assertEquals(original,repo.read().elements.first {it.id==gifId})
            compose.waitUntil(10_000) {compose.runOnUiThread {animated=drawable(compose.activity.window.decorView)};animated?.isRunning==true}
            StoryRepository(compose.activity).use {reopened->assertEquals(original,reopened.read().elements.first {it.id==gifId})}

            menu(textId);tap("Editar texto");compose.onNode(hasSetTextAction()).performTextReplacement("Cambio sin historial");tap("Guardar");count(1)
            back();compose.waitUntil(10_000) {model().undoState.value.slideId==null};count(0)
            back();tap(otherName);tap(otherSlideName)
            compose.waitUntil(10_000) {model().undoState.value.slideId==otherSlide}
            compose.onNodeWithText("Deshacer").assertIsNotEnabled()
            back();back();tap(name);tap(slideName)
            compose.waitUntil(10_000) {model().undoState.value.slideId==slide}
            compose.onNodeWithText("Deshacer").assertIsNotEnabled()
            assertEquals("Cambio sin historial",repo.read().elements.first {it.id==textId}.text)
        } finally {
            compose.activityRule.scenario.close()
            if(project!=0L) repo.delete("projects",project)
            if(otherProject!=0L) repo.delete("projects",otherProject)
            repo.close();file.delete()
        }
    }
}

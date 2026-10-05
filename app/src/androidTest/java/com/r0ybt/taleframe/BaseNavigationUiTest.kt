package com.r0ybt.taleframe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.SlideCanvas
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class BaseNavigationUiTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(test:(StoryRepository,Slide,List<Element>)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="base-ui-${UUID.randomUUID()}.db"
        StoryRepository(context,name).use {repo->try {
            val project=repo.createProject("Base")
            val slides=listOf("A","B","C").map {repo.createSlide(project,it)}
            repo.setAutomaticBaseNavigation(project,true)
            test(repo,repo.read().slides.single {it.id==slides[1]},repo.read().elements.filter {it.slideId==slides[1]})
        } finally {context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively()}}
    }
    private fun fits(label:String) {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(1,layouts.size);assertFalse("Overflow for $label",layouts.single().hasVisualOverflow)
        repeat(layouts.single().lineCount) {assertFalse(layouts.single().isLineEllipsized(it))}
    }
    @Test fun customLabelsFitEqualControlsAndNavigateWithExpandedTouchTargets()=fixture {repo,slide,elements->
        elements.forEach {repo.editElement(it,it.copy(text=if(it.baseNavigation=="previous") "Volver" else "Continuar con la historia"))}
        val buttons=repo.read().elements.filter {it.slideId==slide.id}
        val visits=mutableListOf<Long>()
        compose.setContent {MaterialTheme {SlideCanvas(slide,buttons,Modifier.width(300.dp).height(400.dp),onNavigate={visits+=it})}}
        fits("Volver");fits("Continuar con la historia")
        val previous=buttons.single {it.baseNavigation=="previous"};val next=buttons.single {it.baseNavigation=="next"}
        val a=compose.onNodeWithTag("element-${previous.id}").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("element-${next.id}").fetchSemanticsNode().boundsInRoot
        assertEquals(a.width,b.width,0f);assertEquals(a.height,b.height,0f)
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        // Tap just outside the painted box, inside the existing minimum touch target.
        compose.onNodeWithTag("stage").performTouchInput {click(Offset(b.center.x-stage.left,b.bottom-stage.top+4f))}
        compose.onNodeWithText("Volver").performClick()
        assertEquals(listOf(next.targetId,previous.targetId),visits)
    }
    @Test fun compactPairResizesTogetherDuringOneGestureAndUndoRestoresBoth()=fixture {repo,slide,initial->
        var elements by mutableStateOf(initial)
        val selected=initial.single {it.baseNavigation=="previous"}.id
        var revision by mutableLongStateOf(0)
        val history=ElementUndoHistory(repo);history.beginSession(slide.id)
        try {
            compose.setContent {MaterialTheme {SlideCanvas(slide,elements,Modifier.width(300.dp).height(400.dp),editing=true,
                selectedId=selected,undoRevision=revision,onResize={e->history.edit {resizeElement(e.id,e.width,e.height,e.x,e.y)};elements=repo.read().elements.filter {it.slideId==slide.id}})}}
            val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            val before=initial.associate {it.id to compose.onNodeWithTag("element-${it.id}").fetchSemanticsNode().boundsInRoot}
            compose.onNodeWithTag("stage").performTouchInput {down(before.getValue(selected).bottomRight-stage.topLeft-Offset(3f,3f));advanceEventTime(16);moveBy(Offset(50f,35f))}
            assertEquals(0,history.count)
            val during=initial.map {compose.onNodeWithTag("element-${it.id}").fetchSemanticsNode().boundsInRoot}
            assertTrue(during[0].width>before.getValue(initial[0].id).width)
            assertEquals(during[0].width,during[1].width,0f);assertEquals(during[0].height,during[1].height,0f)
            compose.onNodeWithTag("stage").performTouchInput {up()};assertEquals(1,history.count)
            assertTrue(elements.all {it.width==elements[0].width && it.height==elements[0].height})
            compose.runOnIdle {assertTrue(history.undo());elements=repo.read().elements.filter {it.slideId==slide.id};revision=history.revision}
            assertEquals(initial,elements)
            initial.forEach {assertEquals(before.getValue(it.id),compose.onNodeWithTag("element-${it.id}").fetchSemanticsNode().boundsInRoot)}
        } finally {history.beginSession(null)}
    }
}

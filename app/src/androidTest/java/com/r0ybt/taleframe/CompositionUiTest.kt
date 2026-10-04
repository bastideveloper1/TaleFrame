package com.r0ybt.taleframe

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompositionUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun omittedDraftBlocksButtonAndTimerButKeepsOtherActionsAvailable() {
        compose.mainClock.autoAdvance=false
        val slide=Slide(1,1,"A",autoEnabled=true,autoSeconds=.2f,autoTargetId=2)
        val elements=listOf(Element(1,1,"button","Borrador",targetId=2,width=.3f,height=.12f),Element(2,1,"button","Alternativa",targetId=3,x=.65f,y=.7f,width=.3f,height=.12f))
        val visits=mutableListOf<Long>()
        compose.setContent {MaterialTheme {Player(slide,elements,blockedTargets=setOf(2)) {visits+=it}}}
        compose.mainClock.advanceTimeBy(250);compose.waitForIdle()
        compose.onNodeWithText("Destino en borrador").assertExists();assertTrue(visits.isEmpty())
        compose.onNodeWithText("Permanecer aquí").performClick();compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Borrador").performClick();compose.mainClock.advanceTimeBy(32);compose.onNodeWithText("Destino en borrador").assertExists();assertTrue(visits.isEmpty())
        compose.onNodeWithText("Permanecer aquí").performClick();compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Alternativa").performClick();compose.mainClock.advanceTimeBy(500);compose.waitForIdle();assertEquals(listOf(3L),visits)
    }
    @Test fun templatePickerCreatesNamedEditableLayoutAndCanStillChooseEmpty() {
        var result:Pair<String,Long?>?=null
        compose.setContent {MaterialTheme {NewSlideDialog(Story(),1,"Nueva",{}, {name,id->result=name to id})}}
        compose.onNodeWithTag("new-slide-templates").performScrollToNode(hasText("4 paneles"));compose.onNodeWithText("4 paneles").performClick();compose.onNodeWithText("Guardar").performClick()
        assertEquals("Nueva",result?.first);assertEquals(-5L,result?.second)
        compose.onNodeWithText("Vacía").performClick();compose.onNodeWithText("Guardar").performClick();assertNull(result?.second)
    }
    @Test fun textOverflowFeedbackBelongsOnlyToEditorAndDoesNotShrinkChosenStyle() {
        var editing by mutableStateOf(true)
        val style=VisualStyle(shape="speech",bold=true,italic=true,textScale=2f)
        val e=Element(1,1,"text","Texto largo ".repeat(100),width=.2f,height=.08f,style=style)
        compose.setContent {MaterialTheme {SlideCanvas(Slide(1,1,"A"),listOf(e),editing=editing)}}
        compose.onNodeWithText("Texto no cabe").assertExists()
        compose.runOnIdle {editing=false};compose.onNodeWithText("Texto no cabe").assertDoesNotExist();assertEquals(2f,e.style.textScale,0f)
    }
    @Test fun buttonShowsPressedStateAndReturnsToNormalWithOneNavigation() {
        compose.mainClock.autoAdvance=false
        val button=Element(1,1,"button","Continuar",targetId=2,width=.4f,height=.15f,style=VisualStyle(shape="rounded"))
        var visits=0
        compose.setContent {MaterialTheme {SlideCanvas(Slide(1,1,"A"),listOf(button),onNavigate={visits++})}}
        compose.mainClock.advanceTimeBy(32)
        val node=compose.onNodeWithTag("element-1")
        val normal=node.fetchSemanticsNode().boundsInRoot
        node.performTouchInput {down(center)};compose.mainClock.advanceTimeBy(32)
        assertTrue(node.fetchSemanticsNode().boundsInRoot.width<normal.width)
        node.performTouchInput {up()};compose.mainClock.advanceTimeBy(32)
        assertEquals(normal,node.fetchSemanticsNode().boundsInRoot);assertEquals(1,visits)
    }

}

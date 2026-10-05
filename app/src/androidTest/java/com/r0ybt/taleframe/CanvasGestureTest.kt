package com.r0ybt.taleframe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.SlideCanvas
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasGestureTest {
    @get:Rule val compose=createComposeRule()

    @Test fun downSelectsImmediatelyShortDragFollowsAndOnlyUpCommitsLockedRemainsSelectable() {
        var element by mutableStateOf(Element(1,1,"button","X",x=.3f,y=.3f,width=.06f,height=.05f))
        var selected by mutableStateOf<Long?>(null)
        var commits=0
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize()) {
                SlideCanvas(Slide(1,1,"Test"),listOf(element),Modifier.weight(1f).fillMaxWidth(),editing=true,
                    selectedId=selected,onSelect={selected=it?.id},onMove={commits++;element=it})
                Text("Selección: $selected",Modifier.height(48.dp))
            }
        } }
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val before=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        // Start just outside the tiny visible button, within its expanded touch area.
        val start=before.center-stage.topLeft+Offset(22f,0f)
        compose.onNodeWithTag("stage").performTouchInput { down(start) }
        compose.onNodeWithText("Selección: 1").assertExists()
        assertEquals(0,commits)
        compose.onNodeWithTag("stage").performTouchInput { advanceEventTime(16);moveBy(Offset(100f,80f)) }
        compose.waitForIdle()
        val during=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        assertTrue(during.left>before.left+50f)
        assertEquals(0,commits) // No persistence calls while finger remains down.
        compose.onNodeWithTag("stage").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,commits) }
        val selectedPosition=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot.center-stage.topLeft
        compose.onNodeWithTag("stage").performTouchInput { down(selectedPosition);advanceEventTime(16);moveBy(Offset(80f,40f));up() }
        compose.runOnIdle { assertEquals(2,commits);assertEquals(.06f,element.width,0f);element=element.copy(locked=true);selected=null }
        val locked=element
        val position=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot.center-stage.topLeft
        compose.onNodeWithTag("stage").performTouchInput { down(position);advanceEventTime(16);moveBy(Offset(80f,80f));up() }
        compose.onNodeWithText("Selección: 1").assertExists()
        compose.runOnIdle { assertEquals(locked,element);assertEquals(2,commits) }
    }

    @Test fun cancelledDragReturnsToSavedGeometryWithoutCommitting() {
        val element=Element(1,1,"text","Cancelación",width=.5f,height=.2f)
        var commits=0
        compose.setContent { MaterialTheme {
            SlideCanvas(Slide(1,1,"Cancel"),listOf(element),Modifier.fillMaxSize(),editing=true,onMove={commits++})
        } }
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val before=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("stage").performTouchInput {
            down(before.center-stage.topLeft);advanceEventTime(16);moveBy(Offset(100f,100f));cancel()
        }
        compose.runOnIdle { assertEquals(0,commits) }
        assertEquals(before,compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun undoClearsUnacknowledgedGesturePreviewWithoutAnotherTouch() {
        val original=Element(1,1,"image","",image="/missing",width=.4f,height=.3f,x=.2f,y=.2f)
        var undoRevision by mutableLongStateOf(0)
        var commits=0
        compose.setContent {MaterialTheme {
            SlideCanvas(Slide(1,1,"Undo"),listOf(original),Modifier.fillMaxSize(),editing=true,
                onMove={commits++},undoRevision=undoRevision)
        }}
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val before=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("stage").performTouchInput {
            down(before.center-stage.topLeft);advanceEventTime(16);moveBy(Offset(80f,80f));up()
        }
        assertEquals(1,commits)
        assertNotEquals(before,compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {undoRevision++}
        assertEquals(before,compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun resizeUsesHandleAndCalcoActionsNeverAppearInPlayer() {
        var element by mutableStateOf(Element(1,1,"text","Actual",width=.4f,height=.2f))
        var selected by mutableStateOf<Long?>(1)
        var resized=0
        var editing by mutableStateOf(true)
        val reference=Slide(2,1,"Referencia")
        val referenceElement=Element(2,2,"text","Solo calco",x=.7f,y=.7f)
        val button=Element(3,1,"button","Ir",x=.7f,y=.7f,targetId=2,width=.2f,height=.1f)
        compose.setContent { MaterialTheme {
            SlideCanvas(Slide(1,1,"Actual"),listOf(element,button),Modifier.fillMaxSize(),editing=editing,
                selectedId=selected,onSelect={selected=it?.id},onResize={resized++;element=it},showActions=true,
                destinations=listOf(reference),reference=reference,referenceElements=listOf(referenceElement))
        } }
        compose.onNodeWithText("Solo calco").assertExists()
        compose.onNodeWithText("→ Referencia").assertExists()
        val stage=compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val bounds=compose.onNodeWithTag("element-1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("stage").performTouchInput {
            down(bounds.bottomRight-stage.topLeft-Offset(3f,3f));advanceEventTime(16);moveBy(Offset(70f,60f));up()
        }
        compose.runOnIdle { assertEquals(1,resized);assertTrue(element.width>.4f);assertTrue(element.height>.2f);editing=false }
        compose.onNodeWithText("Solo calco").assertDoesNotExist()
        compose.onNodeWithText("→ Referencia").assertDoesNotExist()
    }
}

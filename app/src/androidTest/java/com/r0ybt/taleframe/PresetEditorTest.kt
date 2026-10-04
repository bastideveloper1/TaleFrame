package com.r0ybt.taleframe

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.ui.ElementDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetEditorTest {
    @get:Rule val compose=createComposeRule()
    private fun tap(text:String) {
        val node=compose.onNodeWithText(text)
        try {node.assertIsDisplayed()} catch(_:AssertionError) {node.performScrollTo()}
        node.performClick();compose.waitForIdle()
    }
    @Test fun visualAndActionPresetsApplyIndependentlyWithoutOverwritingDestination() {
        val style=Preset(1,1,"Decisión principal","button",backgroundColor=0xFF123456.toInt(),style=VisualStyle(shape="oval",alignment="center"))
        val action=Preset(2,1,"Salida rápida","action",transition=Transition("left",400))
        val original=Element(0,1,"button","Continuar",targetId=2,width=.28f,height=.1f)
        var result:Element?=null
        compose.setContent {MaterialTheme {ElementDialog(original,listOf(Slide(1,1,"A"),Slide(2,1,"B")),emptyList(),{}, {result=it},presets=listOf(style,action))}}
        tap("Elegir preset de botón");tap("Decisión principal")
        tap("Elegir preset de acción");tap("Salida rápida")
        tap("Guardar")
        val saved=requireNotNull(result)
        assertEquals(2L,saved.targetId);assertEquals(style.style,saved.style);assertEquals(style.backgroundColor,saved.backgroundColor)
        assertEquals(action.transition,saved.transition);assertEquals(style.id,saved.presetId)
        assertEquals("Continuar",saved.text)
    }
}

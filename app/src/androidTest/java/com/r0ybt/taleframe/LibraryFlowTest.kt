package com.r0ybt.taleframe

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun tap(text:String) {
        val node=compose.onNodeWithText(text)
        try {node.assertIsDisplayed()} catch(_:AssertionError) {node.performScrollTo()}
        node.performClick();compose.waitForIdle()
    }
    private fun field(label:String,text:String) {compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text)}
    private fun await(text:String) {compose.waitUntil(10_000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun projectLibraryCharactersExpressionsDialogAndNarratorWorkInExistingEditor() {
        val repo=StoryRepository(compose.activity);val name="Biblioteca UI ${System.nanoTime()}"
        var project:Long?=null;val files=mutableListOf<File>()
        try {
            await("+ Crear proyecto");tap("+ Crear proyecto");field("Nombre",name);tap("Guardar");await(name);tap(name)
            project=repo.read().projects.first {it.name==name}.id
            tap("+ Crear lámina");field("Nombre","Escena biblioteca");tap("Guardar");await("Escena biblioteca")
            // Seed only local SAF-equivalent resources; all character/preset/editor operations use UI.
            listOf("Retrato normal" to -1,"Retrato feliz" to 0x80FF0000.toInt()).forEach { (label,color)->
                val file=File(compose.activity.cacheDir,"${System.nanoTime()}.png");files+=file
                Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888).also {bitmap->bitmap.eraseColor(color);file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
                repo.library.importResource(requireNotNull(project),Uri.fromFile(file),label,"image","image")
            }
            tap("Biblioteca");tap("Personajes");tap("+ Crear personaje");field("Nombre del personaje","Guillermo");field("Descripción","Protagonista");tap("Guardar personaje");await("Guillermo");tap("Guillermo")
            for((expression,resource) in listOf("Normal" to "Retrato normal","Feliz" to "Retrato feliz")) {
                tap("+ Expresión");field("Nombre de expresión",expression);tap("Asignar imagen");tap(resource);tap("Guardar expresión");await(expression)
            }
            val character=repo.read().characters.first {it.projectId==project}.id
            tap("‹ Biblioteca");compose.onNodeWithContentDescription("Volver").performClick();compose.waitForIdle();tap("Escena biblioteca")
            tap("+ Personaje");tap("Guillermo");tap("Normal")
            compose.waitUntil(10_000) {repo.read().elements.any {it.characterId==character && it.kind=="image"}}
            val image=repo.read().elements.first {it.characterId==character && it.kind=="image"}
            compose.onNodeWithTag("element-${image.id}").performTouchInput {down(center);advanceEventTime(16);moveBy(Offset(80f,60f));up()}
            compose.waitUntil(10_000) {repo.read().elements.first {it.id==image.id}.x>image.x}
            tap("Bloquear");compose.waitUntil(10_000) {repo.read().elements.first {it.id==image.id}.locked}
            val before=repo.read().elements.first {it.id==image.id}
            tap("Cambiar expresión");tap("Feliz")
            compose.waitUntil(10_000) {repo.read().elements.first {it.id==image.id}.expressionId!=before.expressionId}
            val after=repo.read().elements.first {it.id==image.id}
            assertEquals(before.x,after.x,0f);assertEquals(before.y,after.y,0f);assertEquals(before.width,after.width,0f);assertTrue(after.locked)
            tap("+ Diálogo");tap("Guillermo")
            compose.waitUntil(10_000) {repo.read().elements.any {it.characterId==character && it.kind=="text"}}
            val dialog=repo.read().elements.first {it.characterId==character && it.kind=="text"}
            assertTrue(dialog.style.showName);assertEquals("Guillermo",dialog.speakerName)
            compose.onNodeWithText("Guillermo\nEscribe tu diálogo…").assertExists()
            tap("+ Diálogo");tap("Narrador")
            compose.waitUntil(10_000) {repo.read().elements.count {it.kind=="text"}==2}
            val narrator=repo.read().elements.first {it.kind=="text" && it.characterId==null}
            assertFalse(narrator.style.showName);assertEquals("rectangle",narrator.style.shape)
            val snapshot=repo.read();compose.activityRule.scenario.recreate();await("Escena biblioteca")
            assertEquals(snapshot,repo.read());compose.onNodeWithTag("element-${image.id}").assertExists()
            tap("▶ Play");compose.onNodeWithText("+ Personaje").assertDoesNotExist();compose.onNodeWithText("Cambiar expresión").assertDoesNotExist()
            compose.onNodeWithText("Guillermo\nEscribe tu diálogo…").assertExists()
        } finally {compose.activityRule.scenario.close();project?.let {repo.delete("projects",it)};repo.close();files.forEach {it.delete()}}
    }
}

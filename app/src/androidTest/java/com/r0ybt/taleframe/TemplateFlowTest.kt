package com.r0ybt.taleframe

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TemplateFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun tap(text:String) {if(compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("new-slide-templates").performScrollToNode(hasText(text));val n=compose.onNodeWithText(text);try {n.assertIsDisplayed()} catch(_:AssertionError) {n.performScrollTo()};n.performClick();compose.waitForIdle()}
    private fun waitText(text:String) {compose.waitUntil(10_000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun back() {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    @Test fun coverTemplatePanelReuseDraftAndCollapsedToolbarSurviveRecreation() {
        val repo=StoryRepository(compose.activity);val projectName="Plantillas UI ${System.nanoTime()}";var project:Long?=null
        val file=File(compose.activity.cacheDir,"template-flow.png")
        try {
            waitText("+ Crear proyecto");tap("+ Crear proyecto");compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextReplacement(projectName);tap("Guardar");waitText(projectName);tap(projectName)
            project=repo.read().projects.first {it.name==projectName}.id
            Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).also {b->b.eraseColor(0xFFFFAABB.toInt());file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            val r=repo.library.importResource(requireNotNull(project),Uri.fromFile(file),"Portada PNG","image","image")
            tap("+ Crear lámina");compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextReplacement("Página collage");tap("4 paneles");tap("Guardar");waitText("Página collage")
            tap("Cambiar portada");tap("Portada PNG");compose.waitUntil(10_000) {repo.read().projects.first {it.id==project}.coverResourceId==r}
            tap("Página collage");tap("Elementos")
            val panel=repo.read().elements.first {e->repo.read().slides.any {it.id==e.slideId && it.projectId==project}}
            tap("Imagen ${panel.id}");tap("Editar");tap("Elegir imagen de Biblioteca");tap("Portada PNG")
            compose.waitUntil(10_000) {repo.read().elements.first {it.id==panel.id}.image!=null}
            tap("Guardar como plantilla");compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextReplacement("Mi collage");tap("Guardar")
            compose.waitUntil(10_000) {repo.read().templates.any {it.projectId==project && it.name=="Mi collage"}}
            tap("Marcar borrador");compose.waitUntil(10_000) {repo.read().slides.first {it.id==panel.slideId}.draft}
            compose.onNodeWithContentDescription("Reducir herramientas").performScrollTo().performClick();compose.onNodeWithText("+ Texto").assertDoesNotExist();compose.activityRule.scenario.recreate();compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Mostrar herramientas").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithContentDescription("Mostrar herramientas").performScrollTo().performClick()
            back();tap("+ Crear lámina");compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextReplacement("Página reutilizada");tap("Mi collage");tap("Guardar");waitText("Página reutilizada")
            val next=repo.read().slides.first {it.projectId==project && it.name=="Página reutilizada"}
            val placed=repo.read().elements.filter {it.slideId==next.id};assertEquals(4,placed.size);assertTrue(placed.all {it.panel!=null});assertEquals(1,placed.count {it.resourceId==r});assertFalse(next.draft)
            tap("Página reutilizada");compose.onAllNodesWithText("▶ Probar desde aquí")[0].performClick();compose.onNodeWithText("+ Texto").assertDoesNotExist();compose.onNodeWithText("Texto no cabe").assertDoesNotExist();back();waitText("+ Texto")
        } finally {compose.activityRule.scenario.close();project?.let {repo.delete("projects",it)};repo.close();file.delete()}
    }
}

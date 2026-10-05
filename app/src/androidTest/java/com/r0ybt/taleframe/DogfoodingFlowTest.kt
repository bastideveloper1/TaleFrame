package com.r0ybt.taleframe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.taleframe.data.StoryRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DogfoodingFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun tap(text:String) {val node=compose.onNodeWithText(text);try {node.assertIsDisplayed()} catch(_:AssertionError) {node.performScrollTo()};node.performClick();compose.waitForIdle()}
    private fun name(text:String,label:String="Nombre") {compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text);tap("Guardar")}
    private fun waitText(text:String) {compose.waitUntil(10_000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun back() {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    @Test fun realContextQuickTextDeleteAndCurrentPlaybackKeepOfficialStart() {
        val repo=StoryRepository(compose.activity);val projectName="Dogfood ${System.nanoTime()}";var p:Long?=null
        try {
            waitText("+ Crear proyecto");tap("+ Crear proyecto");name(projectName);waitText(projectName);tap(projectName);p=repo.read().projects.first {it.name==projectName}.id
            tap("+ Crear lámina");name("Uno");waitText("Uno");tap("+ Crear lámina");name("Dos");waitText("Dos");tap("Dos")
            tap("+ Texto");name("Original","Texto");waitText("Original")
            val before=repo.read().elements.single {e->repo.read().slides.any {it.id==e.slideId && it.projectId==p}}
            compose.onNodeWithTag("element-${before.id}").performTouchInput {doubleClick()};compose.onNodeWithText("Propiedades").assertExists();listOf("Traer al frente","Enviar atrás","Guardar como preset","Duplicar","Bloquear","Eliminar").forEach {compose.onNodeWithText(it).assertExists()};compose.onNodeWithText("Editar texto").performClick();name("Corregido","Texto")
            compose.waitUntil(10_000) {repo.read().elements.first {it.id==before.id}.text=="Corregido"}
            val edited=repo.read().elements.first {it.id==before.id};assertEquals(before.copy(text="Corregido"),edited)
            compose.onAllNodesWithText("▶ Probar desde aquí")[0].performClick();compose.onNodeWithText("Corregido").assertExists();compose.onNodeWithText("Mostrar nombre de lámina").performClick();compose.onNodeWithText("Lámina 2 · Dos").assertExists();compose.onNodeWithText("✓ Mostrar nombre de lámina").performClick();compose.onNodeWithText("Lámina 2 · Dos").assertDoesNotExist()
            assertNull(repo.read().projects.first {it.id==p}.initialSlideId);back()
            compose.onNodeWithTag("element-${before.id}").performTouchInput {doubleClick()};compose.onNode(hasText("Eliminar") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Eliminar elemento").assertExists();compose.onNode(hasText("Eliminar") and hasAnyAncestor(isDialog())).performClick();compose.waitUntil(10_000) {repo.read().elements.none {it.id==before.id}}
        } finally {compose.activityRule.scenario.close();p?.let {repo.delete("projects",it)};repo.close()}
    }
}

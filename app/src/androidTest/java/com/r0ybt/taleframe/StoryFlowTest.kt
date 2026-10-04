package com.r0ybt.taleframe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.taleframe.data.StoryRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tap(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performClick(); compose.waitForIdle()
    }
    private fun name(text: String, label: String = "Nombre") { compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text); tap("Guardar") }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun awaitText(text: String) { compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }

    @Test fun visualStoryCanBranchReturnAndSurviveActivityRecreation() {
        val projectName="Historia prueba UI ${System.nanoTime()}"
        val repository=StoryRepository(compose.activity)
        var projectId: Long?=null
        try {
            awaitText("+ Crear proyecto")
            tap("+ Crear proyecto"); name(projectName); awaitText(projectName)
            projectId=repository.read().projects.first { it.name==projectName }.id
            tap(projectName)
            tap("+ Crear lámina"); name("Lámina 1"); awaitText("Lámina 1"); tap("Lámina 1")
            tap("Fondo"); compose.onNodeWithText("Elegir imagen local").assertExists()
            compose.onNodeWithContentDescription("Color #000000").performClick()
            compose.waitUntil(10_000) { repository.read().slides.any { it.projectId==projectId && it.color==0xFF000000.toInt() } }
            tap("+ Texto"); name("¿Quieres entrar?", "Texto"); awaitText("¿Quieres entrar?")
            compose.onNodeWithText("¿Quieres entrar?").performTouchInput {
                down(center); advanceEventTime(16); moveBy(Offset(120f,80f)); up()
            }
            compose.waitUntil(10_000) { repository.read().elements.any { it.text=="¿Quieres entrar?" && it.x>.1f && it.y>.15f } }
            back()
            tap("+ Crear lámina"); name("Lámina 2"); awaitText("Lámina 2"); tap("Lámina 2")
            tap("+ Texto"); name("Llegaste a la segunda lámina", "Texto"); awaitText("Llegaste a la segunda lámina")
            tap("+ Botón"); compose.onNode(hasSetTextAction() and hasText("Texto")).performTextReplacement("Volver"); tap("Lámina 1"); tap("Guardar"); awaitText("Volver")
            back()
            tap("+ Crear lámina"); name("Lámina 3"); awaitText("Lámina 3")
            tap("Pequeña")
            compose.activityRule.scenario.recreate(); awaitText("Pequeña")
            compose.onNodeWithText("Pequeña").assertIsSelected()
            tap("Lámina 1")
            tap("+ Botón"); compose.onNode(hasSetTextAction() and hasText("Texto")).performTextReplacement("Entrar"); tap("Lámina 2"); tap("Guardar"); awaitText("Entrar")
            tap("+ Botón"); compose.onNode(hasSetTextAction() and hasText("Texto")).performTextReplacement("Irme"); tap("Lámina 3"); tap("Guardar"); awaitText("Irme")
            compose.onNodeWithText("Entrar").performTouchInput {
                down(center); advanceEventTime(16); moveBy(Offset(80f,-100f)); up()
            }
            compose.onNodeWithText("Irme").performTouchInput {
                down(center); advanceEventTime(16); moveBy(Offset(-80f,80f)); up()
            }
            compose.waitUntil(10_000) { repository.read().elements.any { it.text=="Irme" && it.y>.7f } }
            tap("Ver acciones")
            compose.onNodeWithText("→ Lámina 2").assertExists()
            compose.onNodeWithText("→ Lámina 3").assertExists()
            tap("Calco")
            val referenceId = repository.read().slides.first { it.projectId==projectId && it.name=="Lámina 2" }.id
            compose.onNodeWithTag("destination-$referenceId").performScrollTo().performClick()
            tap("Guardar")
            compose.onNodeWithText("Llegaste a la segunda lámina").assertExists()
            val saved=repository.read()
            compose.activityRule.scenario.recreate()
            awaitText("¿Quieres entrar?"); compose.onNodeWithText("Entrar").assertExists()
            assertEquals(saved,repository.read())
            tap("▶ Play")
            compose.onNodeWithText("→ Lámina 2").assertDoesNotExist()
            compose.onNodeWithText("Llegaste a la segunda lámina").assertDoesNotExist()
            compose.onNodeWithText("+ Texto").assertDoesNotExist()
            compose.onNodeWithText("Fondo").assertDoesNotExist()
            tap("Entrar"); awaitText("Llegaste a la segunda lámina")
            tap("Volver"); awaitText("¿Quieres entrar?")
            tap("Irme"); compose.waitForIdle()
            compose.onNodeWithText("¿Quieres entrar?").assertDoesNotExist()
            back(); awaitText("+ Texto")
        } finally {
            compose.activityRule.scenario.close()
            projectId?.let { repository.delete("projects",it) }
            repository.close()
        }
    }
}

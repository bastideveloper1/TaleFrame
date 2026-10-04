package com.r0ybt.taleframe

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.r0ybt.taleframe.data.*
import com.r0ybt.taleframe.state.StoryViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class BackupFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model(): StoryViewModel = ViewModelProvider(compose.activity)[StoryViewModel::class.java]
    private fun waitText(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(text: String) { val node = compose.onNodeWithText(text); try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }; node.performClick(); compose.waitForIdle() }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun fixture(test: (String, File, StoryRepository) -> Unit) {
        val name = "Respaldo UI ${System.nanoTime()}"
        val file = File(compose.activity.cacheDir, "$name.taleframe")
        val repo = StoryRepository(compose.activity)
        try {
            waitText("+ Crear proyecto")
            compose.onNodeWithText("Importar").assertIsDisplayed()
            compose.runOnUiThread { model().edit {
                val p = createProject(name); val a = createSlide(p, "Entrada"); val c = createSlide(p, "Llegada")
                saveElement(Element(0, a, "text", "Historia recuperable"))
                saveElement(Element(0, a, "button", "Continuar historia", targetId = c))
                saveElement(Element(0, c, "text", "Final recuperado"))
            } }
            compose.waitUntil(15000) { repo.read().projects.any { it.name == name } }
            test(name, file, repo)
        } finally {
            compose.activityRule.scenario.close()
            repo.read().projects.filter { it.name.startsWith(name) }.forEach { repo.delete("projects", it.id) }
            repo.close(); file.delete()
        }
    }
    @Test fun exportShareCopyDialogRecreationImportPlaybackAndEditingWorkTogether() = fixture { name, file, repo ->
        val p = repo.read().projects.single { it.name == name }.id
        compose.runOnUiThread { model().exportProject(p, Uri.fromFile(file)) }
        waitText("Proyecto exportado"); compose.onNodeWithText("Compartir").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); waitText("Proyecto exportado"); tap("Listo")
        compose.runOnUiThread { model().importProject(Uri.fromFile(file)) }
        waitText("Importar como copia")
        compose.activityRule.scenario.recreate(); waitText("Importar como copia"); tap("Importar como copia")
        waitText("$name (2)"); waitText("+ Crear lámina")
        val copy = repo.read().projects.single { it.name == "$name (2)" }.id
        assertNotEquals(p, copy)
        tap("▶ Play"); waitText("Historia recuperable"); tap("Continuar historia"); waitText("Final recuperado")
        back(); tap("Entrada"); waitText("+ Texto"); tap("+ Texto")
        waitText("Guardar")
        compose.onNode(hasSetTextAction() and hasText("Texto")).performTextReplacement("Editado tras restaurar")
        tap("Guardar")
        compose.waitUntil(15000) { repo.read().elements.any { it.text == "Editado tras restaurar" } }
        compose.activityRule.scenario.recreate(); waitText("Editado tras restaurar")
        assertEquals(2, repo.read().projects.count { it.name.startsWith(name) })
    }
    @Test fun cancelledCopyAndCorruptImportKeepOriginalProjectsAndCleanTemporaryFiles() = fixture { name, file, repo ->
        val p = repo.read().projects.single { it.name == name }.id
        compose.runOnUiThread { model().exportProject(p, Uri.fromFile(file)) }; waitText("Proyecto exportado"); tap("Listo")
        val before = repo.read()
        compose.runOnUiThread { model().importProject(Uri.fromFile(file)) }; waitText("Importar como copia"); tap("Cancelar")
        compose.waitUntil(15000) { compose.activity.cacheDir.listFiles()!!.none { it.name.startsWith("backup-") } }
        assertEquals(before, repo.read())
        file.writeText("archivo inválido")
        compose.runOnUiThread { model().importProject(Uri.fromFile(file)) }
        waitText("El archivo TaleFrame está incompleto o dañado.")
        assertEquals(before, repo.read())
        compose.onNodeWithText("Importar").assertExists()
    }
}

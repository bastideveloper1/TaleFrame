package com.r0ybt.taleframe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScriptInsertionPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test: (String) -> Unit) {
        val name = "insertion-${UUID.randomUUID()}.db"
        try { test(name) } finally {
            context.deleteDatabase(name)
            File(context.filesDir, "backgrounds-$name").deleteRecursively()
        }
    }
    private fun original(repo: StoryRepository): Story {
        val project = repo.createProject("Historia existente")
        val a = repo.createSlide(project, "A")
        val b = repo.createSlide(project, "B")
        val c = repo.createSlide(project, "C")
        repo.saveSlide(repo.read().slides.first { it.id == a }.copy(color = 0xFF123456.toInt(), narrativeScript = "Guion terminado",
            autoEnabled = true, autoTargetId = c, transition = Transition("fade", 500)))
        repo.saveElement(Element(0, a, "text", "Texto existente"))
        repo.saveElement(Element(0, a, "button", "Bifurcación", targetId = c, transition = Transition("right", 650)))
        repo.setInitialSlide(project, b)
        repo.setAutomaticBaseNavigation(project, true)
        return repo.read()
    }
    private fun assertOriginalContents(before: Story, after: Story) {
        before.slides.forEach { old -> assertEquals(old, after.slides.first { it.id == old.id }.copy(order = old.order)) }
        assertEquals(before.elements, after.elements)
        assertEquals(before.projects, after.projects)
    }

    @Test fun beginningMiddleAndEndPreserveIdsContentsAndRoutesAndUndoRestoresOrder() {
        ScriptInsertionMode.entries.forEach { mode -> fixture { name ->
            StoryRepository(context, name).use { repo ->
                val baseline = original(repo)
                val project = baseline.projects.single().id
                repo.quickScripts.addText(project, "  Guion nuevo ñ 📝\r\n")
                val p = repo.quickScripts.read(project).pendingParagraphs().single()
                val a = baseline.slides[0].id
                val b = baseline.slides[1].id
                val point = ScriptInsertionPoint(mode, afterId = a, beforeId = b)
                val index = scriptInsertionIndex(baseline.slides.map { it.id }, point)
                val created = repo.quickScripts.createSlide(project, p.draftId, p.start, p.end, point)
                val expected = baseline.slides.map { it.id }.toMutableList().apply { add(index, created) }
                assertEquals(expected, repo.read().slides.map { it.id })
                assertOriginalContents(baseline, repo.read())
                assertEquals(p.text, repo.read().slides.first { it.id == created }.narrativeScript)
                assertFalse(repo.read().elements.any { it.slideId == created })
                repo.quickScripts.undo(project)
                assertEquals(baseline, repo.read())
                assertEquals(p.text, repo.quickScripts.read(project).pendingFragments().single().text)
            }
        } }
    }

    @Test fun consecutiveInsertionReopensAndBacksUpInNarrativeOrderAndUndoRemainsSafe() = fixture { name ->
        var baseline = Story()
        var project = 0L
        var expected = emptyList<Long>()
        StoryRepository(context, name).use { repo ->
            baseline = original(repo)
            project = baseline.projects.single().id
            repo.quickScripts.addText(project, "Uno\nDos\nTres\n")
            val paragraphs = repo.quickScripts.read(project).pendingParagraphs()
            val first = repo.quickScripts.createSlide(project, paragraphs[0].draftId, paragraphs[0].start, paragraphs[0].end,
                ScriptInsertionPoint(ScriptInsertionMode.BETWEEN, baseline.slides[0].id, baseline.slides[1].id))
            val second = repo.quickScripts.createSlide(project, paragraphs[1].draftId, paragraphs[1].start, paragraphs[1].end,
                ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = first))
            val third = repo.quickScripts.createSlide(project, paragraphs[2].draftId, paragraphs[2].start, paragraphs[2].end, ScriptInsertionPoint(ScriptInsertionMode.START))
            assertTrue(repo.quickScripts.read(project).assignments.all { !it.dirty })
            expected = listOf(third, baseline.slides[0].id, first, second, baseline.slides[1].id, baseline.slides[2].id)
        }
        StoryRepository(context, name).use { repo ->
            assertEquals(expected, repo.read().slides.map { it.id })
            assertOriginalContents(baseline, repo.read())
            val backup = ProjectBackup(context, repo)
            val bytes = ByteArrayOutputStream().also { backup.export(project, it) }.toByteArray()
            backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
                val restored = backup.restore(prepared, "Copia")
                val source = repo.read().slides.filter { it.projectId == project }
                val copy = repo.read().slides.filter { it.projectId == restored }
                assertEquals(source.map { it.name to it.order }, copy.map { it.name to it.order })
                assertEquals(source.map { it.narrativeScript }, copy.map { it.narrativeScript })
                val sourceIds = source.associate { it.id to it.name }
                val copyIds = copy.associate { it.id to it.name }
                fun routes(elements: List<Element>, ids: Map<Long, String>) = elements.filter { it.slideId in ids }.map { Triple(ids[it.slideId], it.text, ids[it.targetId]) }
                assertEquals(routes(repo.read().elements, sourceIds), routes(repo.read().elements, copyIds))
                repo.quickScripts.undo(restored)
                repo.quickScripts.undo(restored)
                repo.quickScripts.undo(restored)
                assertEquals(baseline.slides.map { it.name to it.order }, repo.read().slides.filter { it.projectId == restored }.map { it.name to it.order })
            }
            repo.quickScripts.undo(project)
            repo.quickScripts.undo(project)
            repo.quickScripts.undo(project)
            val after = repo.read()
            assertEquals(baseline.projects, after.projects.filter { it.id == project })
            assertEquals(baseline.slides, after.slides.filter { it.projectId == project })
            assertEquals(baseline.elements, after.elements.filter { it.slideId in baseline.slides.map { s -> s.id } })
            assertEquals("Uno\nDos\nTres\n", repo.quickScripts.read(project).pendingFragments().single().text)
        }
    }

    @Test fun internalOrderShiftsPreserveVisualDirtyFlagsAndConnectionsBlockUndo() = fixture { name ->
        StoryRepository(context, name).use { repo ->
            val baseline = original(repo)
            val project = baseline.projects.single().id
            repo.quickScripts.addText(project, "Uno\nDos\n")
            val p = repo.quickScripts.read(project).pendingParagraphs()
            val first = repo.quickScripts.createSlide(project, p[0].draftId, p[0].start, p[0].end)
            repo.saveElement(Element(0, first, "text", "Edición visual"))
            val second = repo.quickScripts.createSlide(project, p[1].draftId, p[1].start, p[1].end, ScriptInsertionPoint(ScriptInsertionMode.START))
            assertTrue(repo.quickScripts.read(project).assignments.first().dirty)
            repo.saveElement(Element(0, baseline.slides[0].id, "button", "Nueva conexión", targetId = second))
            val before = repo.read()
            try { repo.quickScripts.undo(project); fail("No debe eliminar una lámina con conexiones") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.read())
            val link = repo.read().elements.last { it.targetId == second }
            repo.delete("elements", link.id)
            repo.quickScripts.undo(project)
            val withVisualEdit = repo.read()
            try { repo.quickScripts.undo(project); fail("No debe eliminar una lámina modificada") }
            catch (_: IllegalArgumentException) { }
            assertEquals(withVisualEdit, repo.read())
            assertTrue(repo.read().slides.any { it.id == first })
        }
    }
}

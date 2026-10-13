package com.r0ybt.taleframe

import android.database.sqlite.SQLiteDatabase
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
class QuickScriptPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test: (String) -> Unit) {
        val name = "quick-${UUID.randomUUID()}.db"
        try { test(name) } finally {
            context.deleteDatabase(name)
            File(context.filesDir, "backgrounds-$name").deleteRecursively()
        }
    }

    @Test fun pendingAssignmentsAndUndoSurviveReopenAndPreserveExistingSlides() = fixture { name ->
        val original = "Antes\n\nMedio 📝\r\nDespués"
        var project = 0L
        var baseline = Story()
        var created = 0L
        StoryRepository(context, name).use { repo ->
            project = repo.createProject("Historia existente")
            val a = repo.createSlide(project, "Terminada A")
            val b = repo.createSlide(project, "Terminada B")
            repo.saveElement(Element(0, a, "button", "Ir", targetId = b))
            repo.setAutomaticBaseNavigation(project, true)
            baseline = repo.read()
            repo.quickScripts.saveInput(project, original)
        }
        StoryRepository(context, name).use { repo ->
            assertEquals(original, repo.quickScripts.read(project).input)
            repo.quickScripts.addText(project, original)
            val pending = repo.quickScripts.read(project).pendingParagraphs()
            created = repo.quickScripts.createSlide(project, pending[1].draftId, pending[1].start, pending[2].end)
            val after = repo.read()
            assertEquals(baseline.slides, after.slides.filter { it.id != created })
            assertEquals(baseline.elements, after.elements)
            assertEquals(baseline.projects, after.projects)
            val slide = after.slides.last()
            assertEquals(created, slide.id)
            assertTrue(slide.draft)
            assertNull(slide.image); assertNull(slide.audio)
            assertEquals("\nMedio 📝\r\n", slide.narrativeScript)
            assertFalse(after.elements.any { it.slideId == created })
        }
        StoryRepository(context, name).use { repo ->
            val state = repo.quickScripts.read(project)
            assertEquals(listOf("Antes\n", "Después"), state.pendingParagraphs().map { it.text })
            assertEquals(original, state.drafts.single().original)
            repo.quickScripts.undo(project)
            assertEquals(original, repo.quickScripts.read(project).pendingParagraphs().joinToString("") { it.text })
            assertEquals(baseline, repo.read())
        }
    }

    @Test fun refusesUndoAfterVisualEditEvenIfThatEditWasRemoved() = fixture { name ->
        StoryRepository(context, name).use { repo ->
            val project = repo.createProject("P")
            repo.quickScripts.addText(project, "Narración")
            val paragraph = repo.quickScripts.read(project).pendingParagraphs().single()
            val slide = repo.quickScripts.createSlide(project, paragraph.draftId, paragraph.start, paragraph.end)
            val element = repo.saveElement(Element(0, slide, "text", "Cambio visual"))
            repo.delete("elements", element)
            val before = repo.read()
            val pending = repo.quickScripts.read(project)
            try { repo.quickScripts.undo(project); fail("Debe conservar la lámina modificada") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.read())
            assertEquals(pending, repo.quickScripts.read(project))
        }
    }

    @Test fun refusesUndoWhenAnotherSlideLinksToTheProvisionalSlide() = fixture { name ->
        StoryRepository(context, name).use { repo ->
            val project = repo.createProject("P")
            val existing = repo.createSlide(project, "Existente")
            repo.quickScripts.addText(project, "Texto")
            val p = repo.quickScripts.read(project).pendingParagraphs().single()
            val slide = repo.quickScripts.createSlide(project, p.draftId, p.start, p.end)
            repo.saveElement(Element(0, existing, "button", "Ir", targetId = slide))
            val before = repo.read()
            try { repo.quickScripts.undo(project); fail("No debe romper conexiones") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.read())
            assertEquals(1, repo.quickScripts.read(project).assignments.size)
        }
    }

    @Test fun backupRestoresInputOriginalAssignmentsAndSafeUndoWithRemappedIds() = fixture { name ->
        StoryRepository(context, name).use { repo ->
            val project = repo.createProject("P")
            val text = "A\r\n\r\nB 📝\n"
            repo.quickScripts.addText(project, text)
            val p = repo.quickScripts.read(project).pendingParagraphs().first()
            repo.quickScripts.createSlide(project, p.draftId, p.start, p.end)
            repo.quickScripts.saveInput(project, "Próximo pegado\n\n")
            val backup = ProjectBackup(context, repo)
            val bytes = ByteArrayOutputStream().also { backup.export(project, it) }.toByteArray()
            backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
                val restored = backup.restore(prepared, "Copia")
                val state = repo.quickScripts.read(restored)
                assertEquals("Próximo pegado\n\n", state.input)
                assertEquals(text, state.drafts.single().original)
                assertEquals(listOf("\r\n", "B 📝\n"), state.pendingParagraphs().map { it.text })
                repo.quickScripts.undo(restored)
                assertEquals(text, repo.quickScripts.read(restored).pendingParagraphs().joinToString("") { it.text })
                assertEquals(1, repo.read().slides.count { it.projectId == project })
            }
        }
    }

    @Test fun longSingleParagraphSurvivesSQLiteReadsAndExport() = fixture { name ->
        val text = "Narración privada ñ 📝  ".repeat(150000)
        StoryRepository(context, name).use { repo ->
            val project = repo.createProject("Texto extenso")
            repo.quickScripts.saveInput(project, text)
            assertEquals(text, repo.quickScripts.read(project).input)
            repo.quickScripts.addText(project, text)
            val paragraph = repo.quickScripts.read(project).pendingParagraphs().single()
            repo.quickScripts.createSlide(project, paragraph.draftId, paragraph.start, paragraph.end)
            assertEquals(text, repo.read().slides.single().narrativeScript)
            val backup = ProjectBackup(context, repo)
            val bytes = ByteArrayOutputStream().also { backup.export(project, it) }.toByteArray()
            backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
                val restored = backup.restore(prepared, "Copia extensa")
                assertEquals(text, repo.quickScripts.read(restored).drafts.single().original)
                assertEquals(text, repo.read().slides.first { it.projectId == restored }.narrativeScript)
            }
        }
    }

    @Test fun migrationFromNinePreservesExistingRowsAndScript() = fixture { name ->
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE slides(id INTEGER PRIMARY KEY, project_id INTEGER, narrative_script TEXT)")
            db.execSQL("CREATE TABLE elements(id INTEGER PRIMARY KEY, slide_id INTEGER, text TEXT)")
            db.execSQL("INSERT INTO projects VALUES(1,'Existente')")
            db.execSQL("INSERT INTO slides VALUES(7,1,'Guion anterior')")
            db.execSQL("INSERT INTO elements VALUES(9,7,'Visual anterior')")
            db.version = 9
        }
        StoryRepository(context, name).use { repo ->
            assertEquals(10, repo.readableDatabase.version)
            repo.readableDatabase.rawQuery("SELECT narrative_script FROM slides WHERE id=7", null).use { assertTrue(it.moveToFirst()); assertEquals("Guion anterior", it.getString(0)) }
            repo.readableDatabase.rawQuery("SELECT text FROM elements WHERE id=9", null).use { assertTrue(it.moveToFirst()); assertEquals("Visual anterior", it.getString(0)) }
            assertEquals("", repo.quickScripts.read(1).input)
            assertTrue(repo.quickScripts.read(1).drafts.isEmpty())
        }
    }
    @Test fun preciseSelectionSurvivesReopenAndUndoWithoutChangingEarlierSlides() = fixture { name ->
        val original = "  Antes una frase 📝; después.  \r\n\r\nFinal ñ  "
        val start = original.indexOf("una")
        val end = original.indexOf("; después")
        var project = 0L
        var baseline = Story()
        var created = 0L
        StoryRepository(context, name).use { repo ->
            project = repo.createProject("Existente")
            val a = repo.createSlide(project, "Terminada A")
            val b = repo.createSlide(project, "Terminada B")
            repo.saveElement(Element(0, a, "button", "Ir", targetId = b))
            repo.setAutomaticBaseNavigation(project, true)
            baseline = repo.read()
            repo.quickScripts.addText(project, original)
            val draft = repo.quickScripts.read(project).drafts.single()
            created = repo.quickScripts.createSlide(project, draft.id, start, end)
            assertEquals(original.substring(start, end), repo.read().slides.last().narrativeScript)
        }
        StoryRepository(context, name).use { repo ->
            val state = repo.quickScripts.read(project)
            assertEquals(original, state.drafts.single().original)
            assertEquals(listOf(original.substring(0, start), original.substring(end)), state.pendingFragments().map { it.text })
            assertEquals(baseline.slides, repo.read().slides.filter { it.id != created })
            assertEquals(baseline.elements, repo.read().elements)
            val before = repo.read()
            try { repo.quickScripts.createSlide(project, state.drafts.single().id, 0, original.length); fail("No debe reutilizar texto asignado") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.read())
            assertEquals(state, repo.quickScripts.read(project))
            repo.quickScripts.undo(project)
            assertEquals(original, repo.quickScripts.read(project).pendingFragments().single().text)
            assertEquals(baseline, repo.read())
        }
    }

    @Test fun preciseSelectionAcrossParagraphsSurvivesBackupAndCannotUndoVisualEdits() = fixture { name ->
        StoryRepository(context, name).use { repo ->
            val project = repo.createProject("Precisa")
            val original = "  Primer párrafo\r\n\r\n  Segundo ñ 📝\n\nTercero  "
            val start = original.indexOf("párrafo")
            val end = original.indexOf(" ñ")
            repo.quickScripts.addText(project, original)
            val draft = repo.quickScripts.read(project).drafts.single()
            repo.quickScripts.createSlide(project, draft.id, start, end)
            val backup = ProjectBackup(context, repo)
            val bytes = ByteArrayOutputStream().also { backup.export(project, it) }.toByteArray()
            backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
                val restored = backup.restore(prepared, "Copia")
                val state = repo.quickScripts.read(restored)
                val slide = repo.read().slides.first { it.projectId == restored }
                assertEquals(original.substring(start, end), slide.narrativeScript)
                assertEquals(listOf(original.substring(0, start), original.substring(end)), state.pendingFragments().map { it.text })
                repo.quickScripts.undo(restored)
                assertEquals(original, repo.quickScripts.read(restored).pendingFragments().single().text)
                assertEquals(1, repo.read().slides.count { it.projectId == project })
            }
            val slide = repo.read().slides.first { it.projectId == project }
            repo.background(slide.id, 0xFF123456.toInt(), null)
            val before = repo.read()
            val state = repo.quickScripts.read(project)
            try { repo.quickScripts.undo(project); fail("No debe eliminar la lámina editada") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.read())
            assertEquals(state, repo.quickScripts.read(project))
        }
    }

}

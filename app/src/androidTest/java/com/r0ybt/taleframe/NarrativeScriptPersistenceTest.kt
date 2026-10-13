package com.r0ybt.taleframe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.ProjectBackup
import com.r0ybt.taleframe.data.StoryRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NarrativeScriptPersistenceTest {
    @Test fun scriptSurvivesReopenDuplicationAndBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "script-${UUID.randomUUID()}.db"
        val text = "  Narración privada: ñ 📝\n".repeat(1000)
        try {
            val id = StoryRepository(context, name).use { repo ->
                val project = repo.createProject("Historia")
                val slide = repo.createSlide(project, "Inicio")
                slide
            }
            val before = StoryRepository(context, name).use { repo ->
                assertNull(repo.read().slides.single().narrativeScript)
                val original = repo.read()
                repo.setNarrativeScript(id, text)
                assertEquals(original.elements, repo.read().elements)
                assertEquals(original.slides.single(), repo.read().slides.single().copy(narrativeScript = null))
                repo.duplicateSlide(id)
                repo.read()
            }
            StoryRepository(context, name).use { repo ->
                assertEquals(before, repo.read())
                assertTrue(repo.read().slides.all { it.narrativeScript == text })
                val backup = ProjectBackup(context, repo)
                val bytes = ByteArrayOutputStream().also { backup.export(before.projects.single().id, it) }.toByteArray()
                backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
                    val restored = backup.restore(prepared, "Copia")
                    assertTrue(repo.read().slides.filter { it.projectId == restored }.all { it.narrativeScript == text })
                }
                repo.setNarrativeScript(id, null)
                assertNull(repo.read().slides.first { it.id == id }.narrativeScript)
            }
        } finally {
            context.deleteDatabase(name)
            File(context.filesDir, "backgrounds-$name").deleteRecursively()
        }
    }
}

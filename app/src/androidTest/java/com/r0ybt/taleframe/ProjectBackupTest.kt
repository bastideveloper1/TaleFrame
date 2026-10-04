package com.r0ybt.taleframe

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ProjectBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test: (StoryRepository, String) -> Unit) {
        val name = "backup-${UUID.randomUUID()}.db"
        StoryRepository(context, name).use { repo -> try { test(repo, name) } finally {
            context.deleteDatabase(name); File(context.filesDir, "backgrounds-$name").deleteRecursively()
        } }
    }
    private fun image(repo: StoryRepository, project: Long, name: String, color: Int): Long {
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png")
        try {
            Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(color); source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            return repo.library.importResource(project, Uri.fromFile(source), name, "image", "image")
        } finally { source.delete() }
    }
    private fun asset(repo: StoryRepository, p: Long, name: String, type: String): Long {
        val source = File(context.cacheDir, "${UUID.randomUUID()}-$name")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input -> source.outputStream().use { input.copyTo(it) } }
            return repo.library.importResource(p, Uri.fromFile(source), name, type, type)
        } finally { source.delete() }
    }
    private fun complete(repo: StoryRepository): Long {
        val p = repo.createProject("Bosque")
        val slide = listOf("A", "B", "C", "D", "E").associateWith { repo.createSlide(p, it) }
        val portrait = image(repo, p, "Retrato", 0x80FF0000.toInt())
        val expressionImage = image(repo, p, "Expresión", 0x8000FF00.toInt())
        image(repo, p, "Recurso sin colocar", 0x800000FF.toInt())
        val gif = asset(repo, p, "local-animation.gif", "gif")
        val video = asset(repo, p, "local-video.mp4", "video")
        val audio = asset(repo, p, "local-audio.wav", "audio")
        val dialog = repo.library.savePreset(Preset(0, p, "Diálogo", style = VisualStyle(shape = "speech", showName = true)))
        val action = repo.library.savePreset(Preset(0, p, "Acción", kind = "action", style = VisualStyle(bold = true)))
        val button = repo.library.savePreset(Preset(0, p, "Botón", kind = "button", transition = Transition("right", 650)))
        val character = repo.library.saveCharacter(Character(0, p, "Luna", "Una descripción", portrait, dialog))
        val normal = repo.library.saveExpression(Expression(0, character, "Normal", portrait))
        val happy = repo.library.saveExpression(Expression(0, character, "Feliz", expressionImage))
        repo.library.reorderExpression(happy, -1)
        val c = repo.library.insertCharacter(slide.getValue("A"), listOf(normal, happy))
        val original = repo.read().elements.first { it.id == c }
        repo.saveElement(original.copy(x = .7f, y = .4f, width = .3f, height = .45f, rotation = 25f, flipped = true, opacity = .6f, locked = true, panel = PanelOptions("manual", 1.3f, .2f, -.2f)))
        repo.saveElement(styledElement(slide.getValue("A"), repo.read().presets.first { it.id == dialog }, "Luna", character).copy(text = "Hola", style = VisualStyle(shape = "thought", italic = true)))
        repo.saveElement(styledElement(slide.getValue("A"), repo.read().presets.first { it.kind == "narrator" }).copy(text = "Narración"))
        repo.saveElement(styledElement(slide.getValue("A"), repo.read().presets.first { it.id == action }).copy(text = "Acción"))
        listOf(Triple("A", "C", "Entrar"), Triple("A", "D", "Huir"), Triple("C", "E", "Final")).forEach { (from, to, label) ->
            repo.saveElement(styledElement(slide.getValue(from), repo.read().presets.first { it.id == button }).copy(text = label, targetId = slide.getValue(to)))
        }
        repo.library.insertResource(slide.getValue("A"), gif)
        repo.library.insertResource(slide.getValue("D"), video)
        val resources = repo.read().resources.associateBy { it.id }
        repo.saveElement(Element(0, slide.getValue("A"), "image", "Secuencia", image = resources.getValue(portrait).path, resourceId = portrait,
            media = MediaOptions(type = "slideshow", frames = listOf(resources.getValue(portrait).path, resources.getValue(expressionImage).path), seconds = .4f)))
        repeat(30) { repo.library.insertResource(slide.getValue("B"), portrait) }
        repo.saveSlide(repo.read().slides.first { it.id == slide.getValue("C") }.copy(image = resources.getValue(video).path, backgroundResourceId = video, media = MediaOptions(type = "video", muted = false),
            audio = resources.getValue(audio).path, audioResourceId = audio, audioLoop = false, audioVolume = .4f, autoEnabled = true, autoSeconds = 9f, autoTargetId = slide.getValue("E"), transition = Transition("left", 700), backgroundMode = "manual", backgroundScale = 1.8f, backgroundLocked = true))
        repo.setCover(p, portrait); repo.setInitialSlide(p, slide.getValue("C")); repo.setDraft(slide.getValue("B"), true); repo.setSkipDrafts(p, true)
        repo.setAutomaticBaseNavigation(p, true)
        repo.reorderSlide(slide.getValue("D"), -2)
        repo.templates.saveSlide(slide.getValue("A"), "Composición con personaje")
        // An old template owns bytes no longer pointed to by the catalog.
        val historical = image(repo, p, "Imagen histórica", 0x80AAAA00.toInt())
        repo.library.insertResource(slide.getValue("E"), historical)
        repo.templates.saveSlide(slide.getValue("E"), "Snapshot histórico")
        val otherProject = repo.createProject("No debe viajar en el backup")
        val replacement = image(repo, otherProject, "Reemplazo", 0x80AA00AA.toInt())
        val replacementPath = repo.read().resources.first { it.id == replacement }.path
        repo.writableDatabase.execSQL("UPDATE resources SET path=?,revision=revision+1 WHERE id=?", arrayOf<Any>(replacementPath, historical))
        return p
    }
    private fun export(repo: StoryRepository, p: Long): ByteArray = ByteArrayOutputStream().also { ProjectBackup(context, repo).export(p, it) }.toByteArray()
    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().also { result ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; result[entry.name] = zip.readBytes() } }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    private fun normalized(story: Story, p: Long): Story {
        val project = story.projects.single { it.id == p }
        val slides = story.slides.filter { it.projectId == p }
        val s = slides.sortedBy { it.id }.mapIndexed { i, value -> value.id to i + 1L }.toMap()
        val resources = story.resources.filter { it.projectId == p }
        val r = resources.sortedBy { it.id }.mapIndexed { i, value -> value.id to i + 1L }.toMap()
        val characters = story.characters.filter { it.projectId == p }
        val c = characters.sortedBy { it.id }.mapIndexed { i, value -> value.id to i + 1L }.toMap()
        val expressions = story.expressions.filter { it.characterId in c }
        val x = expressions.sortedBy { it.id }.mapIndexed { i, value -> value.id to i + 1L }.toMap()
        val presets = story.presets.filter { it.projectId == p }
        val pr = presets.sortedBy { it.id }.mapIndexed { i, value -> value.id to i + 1L }.toMap()
        val elements = story.elements.filter { it.slideId in s }
        val e = elements.sortedBy { it.id }.mapIndexed { i, value -> value.id to i + 1L }.toMap()
        fun path(value: String?) = value?.let { ProjectBackup.sha256(File(it)) }
        fun media(value: MediaOptions) = value.copy(frames = value.frames.map { path(it)!! })
        fun slide(value: Slide) = value.copy(id = s[value.id] ?: value.id, projectId = 1, image = path(value.image), media = media(value.media), audio = path(value.audio), autoTargetId = value.autoTargetId?.let { s.getValue(it) }, backgroundResourceId = value.backgroundResourceId?.let { r.getValue(it) }, audioResourceId = value.audioResourceId?.let { r.getValue(it) })
        fun element(value: Element) = value.copy(id = e[value.id] ?: value.id, slideId = s[value.slideId] ?: value.slideId, image = path(value.image), media = media(value.media), targetId = value.targetId?.let { s.getValue(it) }, resourceId = value.resourceId?.let { r.getValue(it) }, characterId = value.characterId?.let { c.getValue(it) }, expressionId = value.expressionId?.let { x.getValue(it) }, presetId = value.presetId?.let { pr.getValue(it) }, expressionFrames = value.expressionFrames.map { x.getValue(it) })
        return Story(listOf(project.copy(id = 1, name = "Proyecto", coverResourceId = project.coverResourceId?.let { r.getValue(it) }, initialSlideId = project.initialSlideId?.let { s.getValue(it) })),
            slides.map(::slide), elements.map(::element), resources.map { it.copy(id = r.getValue(it.id), projectId = 1, path = path(it.path)!!) },
            characters.map { it.copy(id = c.getValue(it.id), projectId = 1, portraitId = it.portraitId?.let { v -> r.getValue(v) }, dialogPresetId = it.dialogPresetId?.let { v -> pr.getValue(v) }) },
            expressions.map { it.copy(id = x.getValue(it.id), characterId = c.getValue(it.characterId), resourceId = r.getValue(it.resourceId)) },
            presets.map { it.copy(id = pr.getValue(it.id), projectId = 1) }, story.templates.filter { it.projectId == p }.mapIndexed { i, value -> value.copy(id = i + 1L, projectId = 1, slide = slide(value.slide), elements = value.elements.map(::element)) })
    }
    @Test fun completeRoundTripRemapsEveryRelationshipPreservesSnapshotsAndUnusedLibrary() = fixture { repo, _ ->
        val original = complete(repo)
        val bytes = export(repo, original)
        InstrumentationRegistry.getArguments().getString("backupFixture")?.let { filename -> File(context.cacheDir, filename).writeBytes(bytes) }
        val before = normalized(repo.read(), original)
        val store = ProjectBackup(context, repo)
        val copy = store.restore(store.prepare(ByteArrayInputStream(bytes)), "Bosque (2)")
        assertNotEquals(original, copy)
        assertEquals(before, normalized(repo.read(), copy))
        assertEquals(3, repo.read().projects.size)
        val restored = repo.read()
        val character = restored.characters.first { it.projectId == copy }
        val expression = restored.expressions.last { it.characterId == character.id }
        val placed = restored.elements.first { it.slideId in restored.slides.filter { s -> s.projectId == copy }.map { s -> s.id } && it.kind == "image" && it.characterId == character.id }
        repo.library.changeExpression(placed.id, expression.id)
        assertEquals(expression.id, repo.read().elements.first { it.id == placed.id }.expressionId)
        val preset = restored.presets.first { it.projectId == copy && it.name == "Diálogo" }
        val dialogue = restored.elements.first { it.presetId == preset.id }
        repo.library.savePreset(preset.copy(style = VisualStyle(bold = true)))
        assertEquals(dialogue.style, repo.read().elements.first { it.id == dialogue.id }.style)
        val template = restored.templates.first { it.projectId == copy && it.name == "Composición con personaje" }
        val made = repo.templates.createSlide(copy, "Desde plantilla", template.id)
        assertTrue(repo.read().elements.any { it.slideId == made && it.characterId == character.id })
    }
    @Test fun branchingABCDAutoAdvanceInitialAndBaseButtonsFollowNewSlideIds() = fixture { repo, _ ->
        val p = complete(repo); val store = ProjectBackup(context, repo)
        val copy = store.restore(store.prepare(ByteArrayInputStream(export(repo, p))))
        val story = repo.read(); val slides = story.slides.filter { it.projectId == copy }.associateBy { it.name }
        listOf(Triple("A", "C", "Entrar"), Triple("A", "D", "Huir"), Triple("C", "E", "Final")).forEach { (from, to, text) ->
            assertEquals(slides.getValue(to).id, story.elements.single { it.slideId == slides.getValue(from).id && it.text == text }.targetId)
        }
        assertEquals(slides.getValue("E").id, slides.getValue("C").autoTargetId)
        assertEquals(slides.getValue("C").id, story.projects.single { it.id == copy }.initialSlideId)
        val ordered = slides.values.sortedBy { it.order }
        ordered.forEachIndexed { index, slide -> story.elements.filter { it.slideId == slide.id && it.baseNavigation != null }.forEach { button -> assertEquals(ordered[index + if (button.baseNavigation == "next") 1 else -1].id, button.targetId) } }
    }
    @Test fun thirtyInstancesStoreOneBlobAndImportReusesExistingBytesAcrossProjects() = fixture { repo, _ ->
        val p = repo.createProject("Treinta"); val s = repo.createSlide(p, "A"); val r = image(repo, p, "Una imagen", -1)
        repeat(30) { repo.library.insertResource(s, r) }
        val bytes = export(repo, p)
        assertEquals(1, entries(bytes).keys.count { it.startsWith("resources/") })
        val originalPath = repo.read().resources.single().path
        val store = ProjectBackup(context, repo); val copy = store.restore(store.prepare(ByteArrayInputStream(bytes)))
        assertEquals(originalPath, repo.read().resources.single { it.projectId == copy }.path)
        assertEquals(1, File(originalPath).parentFile!!.listFiles()!!.size)
        repo.delete("projects", p); assertTrue(File(originalPath).isFile)
    }
    @Test fun restoreFromEmptyPrivateStoreWithSourceDestroyedAndReopenEditMedia() = fixture { source, sourceName ->
        val p = complete(source); val expected = normalized(source.read(), p)
        val outside = File(context.getExternalFilesDir(null), "restore-${UUID.randomUUID()}.taleframe")
        source.exportTo(p, outside)
        source.delete("projects", p); context.deleteDatabase(sourceName); File(context.filesDir, "backgrounds-$sourceName").deleteRecursively()
        fixture { target, name ->
            assertTrue(target.read().projects.isEmpty())
            val store = ProjectBackup(context, target); val copy = store.restore(store.prepare(outside.inputStream()))
            assertEquals(expected, normalized(target.read(), copy))
            val first = target.read().slides.first { it.projectId == copy }
            target.saveElement(Element(0, first.id, "text", "Edición restaurada"))
            StoryRepository(context, name).use { reopened ->
                assertTrue(reopened.read().elements.any { it.text == "Edición restaurada" })
                assertTrue(reopened.read().resources.all { File(it.path).isFile })
                assertEquals(first.projectId, copy)
            }
        }
        outside.delete()
    }
    private fun StoryRepository.exportTo(p: Long, file: File) = file.outputStream().use { ProjectBackup(context, this).export(p, it) }
    @Test fun invalidAndMaliciousPackagesNeverWriteRowsFilesOrLeaveCache() = fixture { repo, _ ->
        val p = repo.createProject("Original"); image(repo, p, "Imagen", -1)
        val valid = entries(export(repo, p)); val manifest = JSONObject(String(valid.getValue("manifest.json")))
        val blob = valid.keys.single { it.startsWith("resources/") }
        val before = repo.read(); val files = File(before.resources.single().path).parentFile!!.list()!!.toSet()
        fun withProject(raw: ByteArray): ByteArray {
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
            return zip(valid + ("project.json" to raw) + ("manifest.json" to JSONObject(manifest.toString()).put("projectSha256", hash).toString().toByteArray()))
        }
        val dangling = JSONObject(String(valid.getValue("project.json"))).apply { getJSONArray("projects").getJSONObject(0).put("initial_slide_id", 999999) }.toString().toByteArray()
        val cases = listOf(
            "No es ZIP".toByteArray(), byteArrayOf(80, 75, 3, 4, 0),
            zip(valid.filterKeys { it != "manifest.json" }),
            zip(valid + ("project.json" to "{invalid".toByteArray())),
            zip(valid.filterKeys { it != blob }),
            zip(valid + (blob to valid.getValue(blob).copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })),
            zip(valid + ("manifest.json" to JSONObject(manifest.toString()).put("formatVersion", 99).toString().toByteArray())),
            zip(valid + ("../escape" to byteArrayOf(1))), zip(valid + ("/absolute" to byteArrayOf(1))),
            zip(valid + ("resources\\..\\escape" to byteArrayOf(1))), zip(valid + ("script.js" to byteArrayOf(1))),
            zip(valid + ("manifest.json" to JSONObject(manifest.toString()).put("format", "other").toString().toByteArray())),
            withProject(valid.getValue("project.json") + "{}".toByteArray()), withProject(dangling)
        )
        cases.forEachIndexed { i, bytes ->
            try { ProjectBackup(context, repo).prepare(ByteArrayInputStream(bytes)).use { fail("Invalid case $i accepted") } }
            catch (e: ProjectBackup.InvalidPackage) { if (i == 6) assertEquals("Este proyecto fue creado con una versión más reciente de TaleFrame.", e.message) }
            assertEquals(before, repo.read()); assertEquals(files, File(before.resources.single().path).parentFile!!.list()!!.toSet())
            assertTrue(context.cacheDir.listFiles()!!.none { it.name.startsWith("backup-") })
        }
    }
    @Test fun duplicateEntriesCompressionBombEntryCountAndDeepJsonAreRejectedBeforeWrites() = fixture { repo, _ ->
        val p = repo.createProject("Límites"); val valid = entries(export(repo, p)); val before = repo.read()
        val duplicate = zip(valid + ("manifestXjson" to byteArrayOf(1))).toString(Charsets.ISO_8859_1).replace("manifestXjson", "manifest.json").toByteArray(Charsets.ISO_8859_1)
        val bomb = zip(valid + ("resources/${"0".repeat(64)}" to ByteArray(17 * 1024 * 1024)))
        val tooMany = linkedMapOf<String, ByteArray>().apply { putAll(valid); repeat(10000) { put("resources/${it.toString(16).padStart(64, '0')}", byteArrayOf()) } }
        val deep = zip(valid + ("project.json" to ("{\"x\":".repeat(40) + "0" + "}".repeat(40)).toByteArray()))
        val broad = zip(valid + ("project.json" to ("[" + List(100001) { "{}" }.joinToString(",") + "]").toByteArray()))
        listOf(duplicate, bomb, zip(tooMany), deep, broad).forEach { bytes ->
            try { ProjectBackup(context, repo).prepare(ByteArrayInputStream(bytes)).use { fail("Malicious archive accepted") } } catch (_: ProjectBackup.InvalidPackage) { }
            assertEquals(before, repo.read())
            assertTrue(context.cacheDir.listFiles()!!.none { it.name.startsWith("backup-") })
        }
    }
    @Test fun databaseFailureRollsBackAllRowsAndOnlyNewFiles() = fixture { source, _ ->
        val p = complete(source); val bytes = export(source, p)
        fixture { target, _ ->
            val existing = target.createProject("Existente"); image(target, existing, "Compartida", 0x80FF0000.toInt())
            val before = target.read(); val directory = File(before.resources.single().path).parentFile!!; val files = directory.list()!!.toSet()
            target.writableDatabase.execSQL("CREATE TRIGGER fail_backup BEFORE INSERT ON elements BEGIN SELECT RAISE(ABORT,'Injected failure'); END")
            try { ProjectBackup(context, target).let { it.restore(it.prepare(ByteArrayInputStream(bytes))) }; fail("Failure expected") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, target.read()); assertEquals(files, directory.list()!!.toSet()); assertTrue(File(before.resources.single().path).isFile)
            assertTrue(context.cacheDir.listFiles()!!.none { it.name.startsWith("backup-") })
        }
    }
    @Test fun interruptedImportMarkerCleansOrphansAfterRestartButKeepsCommittedAndSharedMedia() = fixture { repo, name ->
        val p = repo.createProject("Conservado"); image(repo, p, "Compartido", -1)
        val directory = File(repo.read().resources.single().path).parentFile!!
        val shared = File(repo.read().resources.single().path)
        val orphan = File(directory, "incomplete-import").apply { writeBytes(byteArrayOf(1, 2)) }
        val marker = File(directory, ".backup-pending-test").apply { writeText("") }
        StoryRepository(context, name).use { reopened -> ProjectBackup(context, reopened).recoverInterruptedImport() }
        assertTrue(shared.isFile); assertFalse(orphan.exists()); assertFalse(marker.exists())
        assertEquals("Conservado", repo.read().projects.single().name)
    }
    @Test fun copyNameAndPortablePathsDoNotExposePrivateLocations() = fixture { repo, _ ->
        val p = complete(repo); val archive = entries(export(repo, p))
        assertFalse(String(archive.getValue("project.json")).contains(context.filesDir.path))
        assertEquals("Bosque (4)", ProjectBackup.copyName("Bosque", listOf("Bosque", "Bosque (2)", "Bosque (3)")))
        assertEquals("Bosque___.taleframe", ProjectBackup.suggestedFilename("Bosque/:?"))
        val prepared = ProjectBackup(context, repo).prepare(ByteArrayInputStream(zip(archive)))
        val directory = prepared.directory; prepared.close(); assertFalse(directory.exists())
    }
    @Test fun legacyCatalogEntriesWithIdenticalBytesKeepNamesAndIdsWithOneArchiveBlob() = fixture { repo, name ->
        val p = repo.createProject("Biblioteca antigua"); val r = image(repo, p, "Primera", -1)
        val original = File(repo.read().resources.single().path)
        val alias = File(original.parentFile, "legacy-duplicate.png"); original.copyTo(alias)
        repo.library.addResource(p, "Segunda", "image", "background", alias.path)
        val bytes = export(repo, p)
        assertEquals(1, entries(bytes).keys.count { it.startsWith("resources/") })
        val store = ProjectBackup(context, repo); val copy = store.restore(store.prepare(ByteArrayInputStream(bytes)))
        val restored = repo.read().resources.filter { it.projectId == copy }
        assertEquals(listOf("Primera", "Segunda"), restored.map { it.name })
        assertEquals(2, restored.map { it.id }.toSet().size)
        assertEquals(1, restored.map { ProjectBackup.sha256(File(it.path)) }.toSet().size)
    }
    @Test fun legacyDuplicateCharactersAndDeletedTemplateOriginsRemainRestorable() = fixture { repo, _ ->
        val p = repo.createProject("Legado"); val slide = repo.createSlide(p, "A"); val r = image(repo, p, "Retrato", -1)
        val c = repo.library.saveCharacter(Character(0, p, "Luna", portraitId = r))
        val c2 = repo.library.saveCharacter(Character(0, p, "Otra"))
        repo.writableDatabase.execSQL("UPDATE characters SET name='Luna' WHERE id=?", arrayOf(c2))
        val x = repo.library.saveExpression(Expression(0, c, "Normal", r)); repo.library.insertCharacter(slide, listOf(x)); repo.templates.saveSlide(slide, "Snapshot")
        repo.library.deleteExpression(x)
        val store = ProjectBackup(context, repo); val copy = store.restore(store.prepare(ByteArrayInputStream(export(repo, p))))
        assertEquals(2, repo.read().characters.count { it.projectId == copy && it.name == "Luna" })
        val template = repo.read().templates.single { it.projectId == copy }
        val made = repo.templates.createSlide(copy, "Conservada", template.id)
        assertTrue(repo.read().elements.any { it.slideId == made && it.image != null })
    }
}

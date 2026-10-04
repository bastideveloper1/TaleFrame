package com.r0ybt.taleframe.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import java.security.MessageDigest
import java.io.File
import java.util.UUID

/** SQLite stays private. All multi-row operations use transactions and stable IDs. */
class StoryRepository(context: Context, databaseName: String = "taleframe.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 4) {
    private val imageDir = File(context.filesDir, if (databaseName == "taleframe.db") "backgrounds" else "backgrounds-$databaseName").apply { mkdirs() }
    val library = LibraryStore(this)
    private val resolver = context.contentResolver

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE slides(id INTEGER PRIMARY KEY AUTOINCREMENT,
            project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
            name TEXT NOT NULL, color INTEGER NOT NULL DEFAULT -1, image TEXT,
            sort_order INTEGER NOT NULL DEFAULT 0, background_mode TEXT NOT NULL DEFAULT 'fill',
            background_scale REAL NOT NULL DEFAULT 1, background_x REAL NOT NULL DEFAULT 0,
            background_y REAL NOT NULL DEFAULT 0, background_locked INTEGER NOT NULL DEFAULT 0)""")
        createElements(db)
        addMediaColumns(db)
        LibraryStore.createSchema(db)
        db.execSQL("CREATE INDEX slides_project ON slides(project_id)")
        db.execSQL("CREATE INDEX elements_slide ON elements(slide_id)")
    }

    private fun createElements(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE elements(id INTEGER PRIMARY KEY AUTOINCREMENT,
            slide_id INTEGER NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
            kind TEXT NOT NULL CHECK(kind IN ('text','button','image')), text TEXT NOT NULL,
            x REAL NOT NULL, y REAL NOT NULL, text_color INTEGER NOT NULL,
            background_color INTEGER NOT NULL, target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL,
            image TEXT, width REAL NOT NULL DEFAULT 0, height REAL NOT NULL DEFAULT 0,
            rotation REAL NOT NULL DEFAULT 0, flipped INTEGER NOT NULL DEFAULT 0,
            opacity REAL NOT NULL DEFAULT 1, locked INTEGER NOT NULL DEFAULT 0,
            layer_order INTEGER NOT NULL DEFAULT 0)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..3 && newVersion == 4)
        if (oldVersion == 1) {
            // SQLiteOpenHelper wraps migration in a transaction. Never drop projects/slides.
            db.execSQL("ALTER TABLE slides ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE slides ADD COLUMN background_mode TEXT NOT NULL DEFAULT 'fill'")
            db.execSQL("ALTER TABLE slides ADD COLUMN background_scale REAL NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE slides ADD COLUMN background_x REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE slides ADD COLUMN background_y REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE slides ADD COLUMN background_locked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE slides SET sort_order=(SELECT COUNT(*) FROM slides s WHERE s.project_id=slides.project_id AND s.id<slides.id)")
            val oldSequence = db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='elements'", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            db.execSQL("ALTER TABLE elements RENAME TO elements_v1")
            createElements(db)
            db.execSQL("""INSERT INTO elements(id,slide_id,kind,text,x,y,text_color,background_color,target_id,layer_order)
                SELECT id,slide_id,kind,text,x,y,text_color,background_color,target_id,
                (SELECT COUNT(*) FROM elements_v1 e WHERE e.slide_id=elements_v1.slide_id AND e.id<elements_v1.id)
                FROM elements_v1""")
            db.execSQL("DROP TABLE elements_v1")
            // Deleted IDs must not be reused, including databases whose elements are all deleted.
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='elements'")
            db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES('elements',?)", arrayOf(oldSequence))
            db.execSQL("CREATE INDEX elements_slide ON elements(slide_id)")
        }
        if (oldVersion < 3) addMediaColumns(db)
        LibraryStore.createSchema(db)
        LibraryStore.backfill(db)
    }

    private fun addMediaColumns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE slides ADD COLUMN settings TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE slides ADD COLUMN auto_target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL")
        db.execSQL("ALTER TABLE elements ADD COLUMN settings TEXT NOT NULL DEFAULT '{}'")
    }

    fun read(): Story {
        val db = readableDatabase
        val projects = mutableListOf<Project>()
        val slides = mutableListOf<Slide>()
        val elements = mutableListOf<Element>()
        db.rawQuery("SELECT id,name FROM projects ORDER BY id", null).use { c ->
            while (c.moveToNext()) projects += Project(c.getLong(0), c.getString(1))
        }
        db.rawQuery("SELECT id,project_id,name,color,image,sort_order,background_mode,background_scale,background_x,background_y,background_locked,settings,auto_target_id,background_resource_id,audio_resource_id FROM slides ORDER BY project_id,sort_order,id", null).use { c ->
            while (c.moveToNext()) slides += Slide(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getString(4), c.getInt(5), c.getString(6), c.getFloat(7), c.getFloat(8), c.getFloat(9), c.getInt(10) != 0).withSettings(c.getString(11), if (c.isNull(12)) null else c.getLong(12)).copy(backgroundResourceId = if(c.isNull(13)) null else c.getLong(13), audioResourceId = if(c.isNull(14)) null else c.getLong(14))
        }
        db.rawQuery("SELECT id,slide_id,kind,text,x,y,text_color,background_color,target_id,image,width,height,rotation,flipped,opacity,locked,layer_order,settings,resource_id,character_id,expression_id,preset_id FROM elements ORDER BY slide_id,layer_order,id", null).use { c ->
            while (c.moveToNext()) elements += Element(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getFloat(4), c.getFloat(5), c.getInt(6), c.getInt(7), if (c.isNull(8)) null else c.getLong(8), c.getString(9), c.getFloat(10), c.getFloat(11), c.getFloat(12), c.getInt(13) != 0, c.getFloat(14), c.getInt(15) != 0, c.getInt(16)).withSettings(c.getString(17)).copy(resourceId=if(c.isNull(18)) null else c.getLong(18),characterId=if(c.isNull(19)) null else c.getLong(19),expressionId=if(c.isNull(20)) null else c.getLong(20),presetId=if(c.isNull(21)) null else c.getLong(21))
        }
        return library.fill(Story(projects, slides, elements))
    }

    fun createProject(name: String): Long {
        var id=0L
        transaction { id=insertOrThrow("projects", null, ContentValues().apply { put("name", name.trim()) });LibraryStore.seedNarrator(this,id) }
        return id
    }
    fun createSlide(projectId: Long, name: String): Long = writableDatabase.insertOrThrow("slides", null, ContentValues().apply {
        put("project_id", projectId); put("name", name.trim())
        put("sort_order", readableDatabase.rawQuery("SELECT COALESCE(MAX(sort_order)+1,0) FROM slides WHERE project_id=?",arrayOf(projectId.toString())).use { it.moveToFirst();it.getInt(0) })
    })
    fun rename(table: String, id: Long, name: String) {
        require(table in listOf("projects", "slides"))
        writableDatabase.update(table, ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id.toString()))
    }
    fun delete(table: String, id: Long) {
        require(table in listOf("projects", "slides", "elements"))
        writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
        cleanImages()
    }

    fun saveElement(e: Element): Long {
        require(e.kind in listOf("text", "button", "image"))
        if (e.targetId != null) {
            readableDatabase.rawQuery("SELECT 1 FROM slides a JOIN slides b ON a.project_id=b.project_id WHERE a.id=? AND b.id=?", arrayOf(e.slideId.toString(), e.targetId.toString())).use {
                require(it.moveToFirst()) { "El destino debe pertenecer al proyecto" }
            }
        }
        require(e.kind != "image" || e.image != null)
        require(e.media.type != "slideshow" || e.media.frames.isNotEmpty()) { "La secuencia necesita al menos una imagen" }
        val projectId = readableDatabase.rawQuery("SELECT project_id FROM slides WHERE id=?",arrayOf(e.slideId.toString())).use { require(it.moveToFirst()) { "La lámina ya no existe" };it.getLong(0) }
        validateLibraryReference("resources", e.resourceId, projectId)
        validateLibraryReference("characters", e.characterId, projectId)
        validateLibraryReference("presets", e.presetId, projectId)
        if(e.expressionId!=null) readableDatabase.rawQuery("SELECT 1 FROM expressions WHERE id=? AND character_id=?",arrayOf(e.expressionId.toString(),e.characterId?.toString() ?: "")).use { require(it.moveToFirst()) { "La expresión debe pertenecer al personaje" } }
        val layer = if (e.id == 0L) readableDatabase.rawQuery("SELECT COALESCE(MAX(layer_order)+1,0) FROM elements WHERE slide_id=?",arrayOf(e.slideId.toString())).use { it.moveToFirst();it.getInt(0) } else e.layer
        val values = ContentValues().apply {
            put("resource_id",e.resourceId);put("character_id",e.characterId);put("expression_id",e.expressionId);put("preset_id",e.presetId);put("settings", e.settings()); put("slide_id", e.slideId); put("kind", e.kind); put("text", e.text)
            put("x", boundedPosition(e.x)); put("y", boundedPosition(e.y))
            put("text_color", e.textColor); put("background_color", e.backgroundColor); put("target_id", e.targetId)
            put("image", if (e.media.type == "slideshow") e.media.frames.first() else e.image); put("width", boundedValue(e.width, 0f, 1f, 0f)); put("height", boundedValue(e.height, 0f, 1f, 0f))
            put("rotation", boundedValue(e.rotation, -180f, 180f, 0f)); put("flipped", e.flipped)
            put("opacity", boundedPosition(e.opacity)); put("locked", e.locked); put("layer_order", layer)
        }
        val result = if (e.id == 0L) writableDatabase.insertOrThrow("elements", null, values) else {
            writableDatabase.update("elements", values, "id=?", arrayOf(e.id.toString())); e.id
        }
        if (!writableDatabase.inTransaction()) cleanImages()
        return result
    }
    /** Apply dialog differences to the latest row, preserving already queued gestures/layers. */
    fun editElement(before: Element, edited: Element) {
        if (before.id == 0L) { saveElement(edited); return }
        val current = read().elements.find { it.id == before.id } ?: return
        saveElement(current.copy(
            text = if (before.text != edited.text) edited.text else current.text,
            textColor = if (before.textColor != edited.textColor) edited.textColor else current.textColor,
            backgroundColor = if (before.backgroundColor != edited.backgroundColor) edited.backgroundColor else current.backgroundColor,
            targetId = if (before.targetId != edited.targetId) edited.targetId else current.targetId,
            width = if (before.width != edited.width) edited.width else current.width,
            height = if (before.height != edited.height) edited.height else current.height,
            rotation = if (before.rotation != edited.rotation) edited.rotation else current.rotation,
            flipped = if (before.flipped != edited.flipped) edited.flipped else current.flipped,
            opacity = if (before.opacity != edited.opacity) edited.opacity else current.opacity,
            image = if (before.image != edited.image) edited.image else current.image,
            media = if (before.media != edited.media) edited.media else current.media,
            transition = if (before.transition != edited.transition) edited.transition else current.transition,
            style = if (before.style != edited.style) edited.style else current.style,
            speakerName = if (before.speakerName != edited.speakerName) edited.speakerName else current.speakerName,
            sourceName = if (before.sourceName != edited.sourceName) edited.sourceName else current.sourceName,
            resourceId = if (before.resourceId != edited.resourceId) edited.resourceId else current.resourceId,
            characterId = if (before.characterId != edited.characterId) edited.characterId else current.characterId,
            expressionId = if (before.expressionId != edited.expressionId) edited.expressionId else current.expressionId,
            presetId = if (before.presetId != edited.presetId) edited.presetId else current.presetId,
            expressionFrames = if (before.expressionFrames != edited.expressionFrames) edited.expressionFrames else current.expressionFrames,
            locked = if (before.locked != edited.locked) edited.locked else current.locked
        ))
    }
    fun toggleLock(id: Long) { writableDatabase.execSQL("UPDATE elements SET locked=1-locked WHERE id=?", arrayOf(id)) }

    // Narrow update avoids a queued drag overwriting a later property edit.
    fun moveElement(id: Long, x: Float, y: Float) {
        writableDatabase.update("elements", ContentValues().apply { put("x", boundedPosition(x)); put("y", boundedPosition(y)) }, "id=? AND locked=0", arrayOf(id.toString()))
    }
    fun resizeElement(id: Long, width: Float, height: Float, x: Float? = null, y: Float? = null) {
        writableDatabase.update("elements", ContentValues().apply {
            put("width", boundedValue(width, .05f, 1f, .3f)); put("height", boundedValue(height, .04f, 1f, .15f))
            if (x != null) put("x", boundedPosition(x))
            if (y != null) put("y", boundedPosition(y))
        }, "id=? AND locked=0", arrayOf(id.toString()))
    }
    fun layer(id: Long, front: Boolean) {
        val story = read()
        val element = story.elements.find { it.id == id } ?: return
        val ordered = story.elements.filter { it.slideId == element.slideId && it.id != id }.map { it.id }.toMutableList()
        if (front) ordered.add(id) else ordered.add(0, id)
        transaction { ordered.forEachIndexed { index, elementId ->
            update("elements", ContentValues().apply { put("layer_order", index) }, "id=?", arrayOf(elementId.toString()))
        } }
    }
    fun duplicateElement(id: Long): Long {
        val original = read().elements.first { it.id == id }
        return saveElement(original.copy(id = 0, x = boundedPosition(original.x + .04f), y = boundedPosition(original.y + .04f)))
    }
    fun reorderSlide(id: Long, delta: Int) {
        val story = read()
        val slide = story.slides.find { it.id == id } ?: return
        val ordered = story.slides.filter { it.projectId == slide.projectId }.map { it.id }.toMutableList()
        val from = ordered.indexOf(id)
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        ordered.removeAt(from); ordered.add(to, id)
        transaction { ordered.forEachIndexed { index, slideId ->
            update("slides", ContentValues().apply { put("sort_order", index) }, "id=?", arrayOf(slideId.toString()))
        } }
    }
    fun duplicateSlide(id: Long): Long {
        val story = read()
        val original = story.slides.first { it.id == id }
        var newId = 0L
        transaction {
            newId = createSlide(original.projectId, "${original.name} (copia)")
            saveSlide(original.copy(id = newId))
            story.elements.filter { it.slideId == id }.forEach { element ->
                // Preserve stable destinations, including links to the source slide itself.
                saveElement(element.copy(id = 0, slideId = newId))
            }
            val ids = story.slides.filter { it.projectId == original.projectId }.map { it.id }.toMutableList()
            ids.add(ids.indexOf(id) + 1, newId)
            ids.forEachIndexed { index, slideId -> update("slides", ContentValues().apply { put("sort_order", index) }, "id=?", arrayOf(slideId.toString())) }
        }
        return newId
    }
    fun background(slideId: Long, color: Int, image: String?) {
        val slide = read().slides.first { it.id == slideId }
        saveSlide(slide.copy(color = color, image = image))
    }
    fun editBackground(before: Slide, edited: Slide) {
        val current = read().slides.find { it.id == before.id } ?: return
        saveSlide(current.copy(
            color = if (before.color != edited.color) edited.color else current.color,
            image = if (before.image != edited.image) edited.image else current.image,
            backgroundMode = if (before.backgroundMode != edited.backgroundMode) edited.backgroundMode else current.backgroundMode,
            backgroundScale = if (before.backgroundScale != edited.backgroundScale) edited.backgroundScale else current.backgroundScale,
            backgroundX = if (before.backgroundX != edited.backgroundX) edited.backgroundX else current.backgroundX,
            backgroundY = if (before.backgroundY != edited.backgroundY) edited.backgroundY else current.backgroundY,
            media = if (before.media != edited.media) edited.media else current.media,
            audio = if (before.audio != edited.audio) edited.audio else current.audio,
            audioRevision = if (before.audioRevision != edited.audioRevision) edited.audioRevision else current.audioRevision,
            audioLoop = if (before.audioLoop != edited.audioLoop) edited.audioLoop else current.audioLoop,
            audioVolume = if (before.audioVolume != edited.audioVolume) edited.audioVolume else current.audioVolume,
            autoEnabled = if (before.autoEnabled != edited.autoEnabled) edited.autoEnabled else current.autoEnabled,
            autoSeconds = if (before.autoSeconds != edited.autoSeconds) edited.autoSeconds else current.autoSeconds,
            autoTargetId = if (before.autoTargetId != edited.autoTargetId) edited.autoTargetId else current.autoTargetId,
            transition = if (before.transition != edited.transition) edited.transition else current.transition,
            backgroundResourceId = if (before.backgroundResourceId != edited.backgroundResourceId || (before.image != edited.image && edited.image == null)) edited.backgroundResourceId.takeIf { edited.image != null } else current.backgroundResourceId,
            audioResourceId = if (before.audioResourceId != edited.audioResourceId || (before.audio != edited.audio && edited.audio == null)) edited.audioResourceId.takeIf { edited.audio != null } else current.audioResourceId,
            backgroundLocked = if (before.backgroundLocked != edited.backgroundLocked) edited.backgroundLocked else current.backgroundLocked
        ))
    }
    fun saveSlide(s: Slide) {
        if (s.autoTargetId != null) readableDatabase.rawQuery("SELECT 1 FROM slides WHERE id=? AND project_id=?",arrayOf(s.autoTargetId.toString(),s.projectId.toString())).use { require(it.moveToFirst()) { "El destino debe pertenecer al proyecto" } }
        validateLibraryReference("resources",s.backgroundResourceId,s.projectId)
        validateLibraryReference("resources",s.audioResourceId,s.projectId)
        require(s.backgroundMode in listOf("fit", "fill", "manual"))
        writableDatabase.update("slides", ContentValues().apply {
            put("background_resource_id",s.backgroundResourceId.takeIf { s.image!=null });put("audio_resource_id",s.audioResourceId.takeIf { s.audio!=null });put("settings", s.settings()); put("auto_target_id", s.autoTargetId); put("color", s.color); put("image", s.image); put("sort_order", s.order)
            put("background_mode", s.backgroundMode); put("background_scale", boundedValue(s.backgroundScale, .25f, 4f, 1f))
            put("background_x", boundedValue(s.backgroundX, -1f, 1f, 0f)); put("background_y", boundedValue(s.backgroundY, -1f, 1f, 0f))
            put("background_locked", s.backgroundLocked)
        }, "id=?", arrayOf(s.id.toString()))
        // Cleanup only after a complete duplicate transaction: shared references remain valid.
        if (!writableDatabase.inTransaction()) cleanImages()
    }
    private fun validateLibraryReference(table: String, id: Long?, projectId: Long) {
        if(id==null) return
        readableDatabase.rawQuery("SELECT 1 FROM $table WHERE id=? AND project_id=?",arrayOf(id.toString(),projectId.toString())).use { require(it.moveToFirst()) { "El recurso narrativo debe pertenecer al proyecto" } }
    }
    private fun transaction(action: SQLiteDatabase.() -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { db.action(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    fun displayName(uri: Uri): String = try {
        resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Recurso local"
    } catch (_:Exception) { "Recurso local" }

    // Private copies support transparent PNG/WebP without permanent provider permissions.
    fun importImage(uri: Uri, gif: Boolean = false): String {
        val file = File(imageDir, UUID.randomUUID().toString())
        try {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n; require(total <= 40L * 1024 * 1024) { "La imagen supera 40 MB" }
                    output.write(buffer, 0, n)
                }
            } } ?: error("No se pudo abrir la imagen")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "El archivo no es una imagen compatible" }
            if (gif) {
                val header = file.inputStream().use { input -> val bytes = ByteArray(6); input.read(bytes); String(bytes, Charsets.US_ASCII) }
                require(header == "GIF87a" || header == "GIF89a") { "El archivo no es un GIF" }
                require(options.outWidth <= 4096 && options.outHeight <= 4096 && options.outWidth.toLong() * options.outHeight <= 8_000_000) { "GIF demasiado grande (máximo 8 megapíxeles y 4096 px por lado)" }
            }
            return deduplicate(file)
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun importMedia(uri: Uri, type: String): String {
        if (type == "image" || type == "gif") return importImage(uri, type == "gif")
        require(type == "video" || type == "audio")
        val file = File(imageDir, UUID.randomUUID().toString())
        try {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count
                    require(total <= (if (type == "video") 500L else 100L) * 1024 * 1024) { "Archivo demasiado grande (video: 500 MB; audio: 100 MB)" }
                    output.write(buffer, 0, count)
                }
            } } ?: error("No se pudo abrir el archivo")
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(file.path)
                val key = if (type == "video") MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO else MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                require(metadata.extractMetadata(key) == "yes") { "El archivo no contiene $type compatible" }
            } finally { metadata.release() }
            return deduplicate(file)
        } catch (e: Exception) {
            file.delete()
            throw IllegalArgumentException("No se pudo importar el recurso: incompatible, dañado o inaccesible. Máximo: video 500 MB; audio 100 MB.", e)
        }
    }
    private fun deduplicate(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        val digest = hash.digest()
        val destination = File(imageDir, digest.joinToString("") { "%02x".format(it) })
        if (destination.exists()) {
            // A damaged shared copy must never replace the newly validated import.
            val existing = MessageDigest.getInstance("SHA-256")
            destination.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; existing.update(buffer, 0, n) }
            }
            if (!existing.digest().contentEquals(digest)) return file.path
            file.delete()
        } else check(file.renameTo(destination)) { "No se pudo guardar el recurso" }
        return destination.path
    }
    /** Called once all new references have been attached, never in the middle of imports. */
    fun cleanImages() {
        val story = read()
        val used = buildSet {
            addAll(story.resources.map { it.path })
            story.slides.forEach { s -> s.image?.let(::add); s.audio?.let(::add); addAll(s.media.frames) }
            story.elements.forEach { e -> e.image?.let(::add); addAll(e.media.frames) }
        }
        imageDir.listFiles()?.filter { it.path !in used }?.forEach { it.delete() }
    }
}

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
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 6) {
    private val imageDir = File(context.filesDir, if (databaseName == "taleframe.db") "backgrounds" else "backgrounds-$databaseName").apply { mkdirs() }
    internal val mediaDirectory: File get() = imageDir
    internal var undoMediaPaths: Set<String> = emptySet()
    val library = LibraryStore(this)
    val templates = TemplateStore(this)
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
        TemplateStore.createSchema(db)
        addNavigationColumns(db)
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
        require(oldVersion in 1..5 && newVersion == 6)
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
        if(oldVersion < 4) { LibraryStore.createSchema(db); LibraryStore.backfill(db) }
        if(oldVersion < 5) TemplateStore.createSchema(db)
        addNavigationColumns(db)
    }

    private fun addNavigationColumns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN automatic_base_navigation INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE elements ADD COLUMN base_navigation TEXT DEFAULT NULL CHECK(base_navigation IS NULL OR (kind='button' AND base_navigation IN ('previous','next')))")
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
        db.rawQuery("SELECT id,name,cover_resource_id,initial_slide_id,skip_drafts,automatic_base_navigation FROM projects ORDER BY id", null).use { c ->
            while (c.moveToNext()) projects += Project(c.getLong(0), c.getString(1),if(c.isNull(2)) null else c.getLong(2),if(c.isNull(3)) null else c.getLong(3),c.getInt(4)!=0,c.getInt(5)!=0)
        }
        db.rawQuery("SELECT id,project_id,name,color,image,sort_order,background_mode,background_scale,background_x,background_y,background_locked,settings,auto_target_id,background_resource_id,audio_resource_id,draft FROM slides ORDER BY project_id,sort_order,id", null).use { c ->
            while (c.moveToNext()) slides += Slide(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getString(4), c.getInt(5), c.getString(6), c.getFloat(7), c.getFloat(8), c.getFloat(9), c.getInt(10) != 0).withSettings(c.getString(11), if (c.isNull(12)) null else c.getLong(12)).copy(backgroundResourceId = if(c.isNull(13)) null else c.getLong(13), audioResourceId = if(c.isNull(14)) null else c.getLong(14),draft=c.getInt(15)!=0)
        }
        db.rawQuery("SELECT id,slide_id,kind,text,x,y,text_color,background_color,target_id,image,width,height,rotation,flipped,opacity,locked,layer_order,settings,resource_id,character_id,expression_id,preset_id,base_navigation FROM elements ORDER BY slide_id,layer_order,id", null).use { c ->
            while (c.moveToNext()) elements += Element(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getFloat(4), c.getFloat(5), c.getInt(6), c.getInt(7), if (c.isNull(8)) null else c.getLong(8), c.getString(9), c.getFloat(10), c.getFloat(11), c.getFloat(12), c.getInt(13) != 0, c.getFloat(14), c.getInt(15) != 0, c.getInt(16)).withSettings(c.getString(17)).copy(resourceId=if(c.isNull(18)) null else c.getLong(18),characterId=if(c.isNull(19)) null else c.getLong(19),expressionId=if(c.isNull(20)) null else c.getLong(20),presetId=if(c.isNull(21)) null else c.getLong(21),baseNavigation=c.getString(22))
        }
        return templates.fill(library.fill(Story(projects, slides, elements)))
    }

    fun createProject(name: String): Long {
        var id=0L
        transaction { id=insertOrThrow("projects", null, ContentValues().apply { put("name", name.trim()) });LibraryStore.seedNarrator(this,id) }
        return id
    }
    fun createSlide(projectId: Long, name: String, synchronizeNavigation:Boolean=true): Long {
        var id=0L
        transaction {
            id=insertOrThrow("slides",null,ContentValues().apply {
                put("project_id",projectId);put("name",name.trim())
                put("sort_order",rawQuery("SELECT COALESCE(MAX(sort_order)+1,0) FROM slides WHERE project_id=?",arrayOf(projectId.toString())).use {it.moveToFirst();it.getInt(0)})
            })
            if(synchronizeNavigation) syncBaseNavigation(projectId)
        }
        return id
    }
    fun setCover(projectId: Long, resourceId: Long?) {
        if(resourceId!=null) readableDatabase.rawQuery("SELECT 1 FROM resources WHERE id=? AND project_id=? AND type='image'",arrayOf(resourceId.toString(),projectId.toString())).use {require(it.moveToFirst()) {"La portada debe ser una imagen de este proyecto"}}
        writableDatabase.update("projects",ContentValues().apply {put("cover_resource_id",resourceId)},"id=?",arrayOf(projectId.toString()))
    }
    fun setInitialSlide(projectId: Long, slideId: Long?) {
        if(slideId!=null) readableDatabase.rawQuery("SELECT 1 FROM slides WHERE id=? AND project_id=?",arrayOf(slideId.toString(),projectId.toString())).use {require(it.moveToFirst()) {"La lámina inicial debe pertenecer al proyecto"}}
        writableDatabase.update("projects",ContentValues().apply {put("initial_slide_id",slideId)},"id=?",arrayOf(projectId.toString()))
    }
    fun setSkipDrafts(projectId:Long,value:Boolean) {writableDatabase.update("projects",ContentValues().apply {put("skip_drafts",value)},"id=?",arrayOf(projectId.toString()))}
    fun setDraft(slideId:Long,value:Boolean) {writableDatabase.update("slides",ContentValues().apply {put("draft",value)},"id=?",arrayOf(slideId.toString()))}
    fun rename(table: String, id: Long, name: String) {
        require(table in listOf("projects", "slides"))
        writableDatabase.update(table, ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id.toString()))
    }
    fun delete(table: String, id: Long) {
        require(table in listOf("projects", "slides", "elements"))
        val project = if(table=="slides") read().slides.find {it.id==id}?.projectId else null
        transaction {
            delete(table, "id=?", arrayOf(id.toString()))
            if(project!=null) syncBaseNavigation(project)
        }
        cleanImages()
    }

    fun saveElement(e: Element): Long {
        require(e.kind in listOf("text", "button", "image"))
        require(e.baseNavigation==null || (e.kind=="button" && e.baseNavigation in listOf("previous","next")))
        if (e.targetId != null) {
            readableDatabase.rawQuery("SELECT 1 FROM slides a JOIN slides b ON a.project_id=b.project_id WHERE a.id=? AND b.id=?", arrayOf(e.slideId.toString(), e.targetId.toString())).use {
                require(it.moveToFirst()) { "El destino debe pertenecer al proyecto" }
            }
        }
        require(e.kind != "image" || e.image != null || e.panel != null)
        require(e.media.type != "slideshow" || e.media.frames.isNotEmpty()) { "La secuencia necesita al menos una imagen" }
        val projectId = readableDatabase.rawQuery("SELECT project_id FROM slides WHERE id=?",arrayOf(e.slideId.toString())).use { require(it.moveToFirst()) { "La lámina ya no existe" };it.getLong(0) }
        validateLibraryReference("resources", e.resourceId, projectId)
        validateLibraryReference("characters", e.characterId, projectId)
        validateLibraryReference("presets", e.presetId, projectId)
        if(e.expressionId!=null) readableDatabase.rawQuery("SELECT 1 FROM expressions WHERE id=? AND character_id=?",arrayOf(e.expressionId.toString(),e.characterId?.toString() ?: "")).use { require(it.moveToFirst()) { "La expresión debe pertenecer al personaje" } }
        val layer = if (e.id == 0L) readableDatabase.rawQuery("SELECT COALESCE(MAX(layer_order)+1,0) FROM elements WHERE slide_id=?",arrayOf(e.slideId.toString())).use { it.moveToFirst();it.getInt(0) } else e.layer
        val values = ContentValues().apply {
            put("base_navigation",e.baseNavigation);put("resource_id",e.resourceId);put("character_id",e.characterId);put("expression_id",e.expressionId);put("preset_id",e.presetId);put("settings", e.settings()); put("slide_id", e.slideId); put("kind", e.kind); put("text", e.text)
            put("x", e.storedPosition(e.x)); put("y", e.storedPosition(e.y))
            put("text_color", e.textColor); put("background_color", e.backgroundColor); put("target_id", e.targetId)
            put("image", if (e.media.type == "slideshow") e.media.frames.first() else e.image); put("width", e.storedSize(e.width)); put("height", e.storedSize(e.height))
            put("rotation", boundedValue(e.rotation, -180f, 180f, 0f)); put("flipped", e.flipped)
            put("opacity", boundedPosition(e.opacity)); put("locked", e.locked); put("layer_order", layer)
        }
        val previousSize = if (e.id != 0L && e.baseNavigation != null) readableDatabase.rawQuery(
            "SELECT width,height FROM elements WHERE id=?",arrayOf(e.id.toString())).use {if(it.moveToFirst()) it.getFloat(0) to it.getFloat(1) else null} else null
        val width=values.getAsFloat("width"); val height=values.getAsFloat("height")
        var result=e.id
        fun write() {
            result = if (e.id == 0L) writableDatabase.insertOrThrow("elements", null, values) else {
                writableDatabase.update("elements", values, "id=?", arrayOf(e.id.toString())); e.id
            }
        }
        if(previousSize != null && previousSize != (width to height)) transaction {
            write(); syncBaseButtonSize(e,width,height)
        } else write()
        if (!writableDatabase.inTransaction()) cleanImages()
        return result
    }
    /** Apply dialog differences to the latest row, preserving already queued gestures/layers. */
    fun editElement(before: Element, edited: Element) {
        if (before.id == 0L) { saveElement(edited); return }
        val current = read().elements.find { it.id == before.id } ?: return
        saveElement(current.copy(
            freePosition = if (before.freePosition != edited.freePosition) edited.freePosition else current.freePosition,
            x = if (before.x != edited.x) edited.x else current.x,
            y = if (before.y != edited.y) edited.y else current.y,
            panel = if(before.panel != edited.panel) edited.panel else current.panel,
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
    fun moveElement(id: Long, x: Float, y: Float, freePosition: Boolean? = null) {
        val current = read().elements.find { it.id == id } ?: return
        writableDatabase.update("elements", ContentValues().apply {
            put("x", current.storedPosition(x)); put("y", current.storedPosition(y))
            if (freePosition != null && current.supportsFreePlacement) put("settings", current.copy(freePosition=freePosition).settings())
        }, "id=? AND locked=0", arrayOf(id.toString()))
    }
    fun resizeElement(id: Long, width: Float, height: Float, x: Float? = null, y: Float? = null, freePosition: Boolean? = null) {
        val current = read().elements.find { it.id == id } ?: return
        transaction {
            val newWidth=current.storedSize(width,.05f,.3f)
            val newHeight=current.storedSize(height,.04f,.15f)
            val changed=update("elements", ContentValues().apply {
                put("width", newWidth); put("height", newHeight)
                if (x != null) put("x", current.storedPosition(x))
                if (y != null) put("y", current.storedPosition(y))
                if (freePosition != null && current.supportsFreePlacement) put("settings", current.copy(freePosition=freePosition).settings())
            }, "id=? AND locked=0", arrayOf(id.toString()))
            if(changed>0 && (current.width!=newWidth || current.height!=newHeight)) syncBaseButtonSize(current,newWidth,newHeight)
        }
    }
    /** Only the tagged opposite role on this slide shares dimensions; text, targets and locks stay independent. */
    private fun SQLiteDatabase.syncBaseButtonSize(source: Element, width: Float, height: Float) {
        val opposite=when(source.baseNavigation) {"previous"->"next";"next"->"previous";else->return}
        val peer=rawQuery("SELECT id FROM elements WHERE slide_id=? AND base_navigation=? ORDER BY layer_order,id LIMIT 1",
            arrayOf(source.slideId.toString(),opposite)).use {if(it.moveToFirst()) it.getLong(0) else null} ?: return
        update("elements",ContentValues().apply {put("width",width);put("height",height)},"id=?",arrayOf(peer.toString()))
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
        return saveElement(original.copy(id = 0, baseNavigation = null, x = original.storedPosition(original.x + .04f), y = original.storedPosition(original.y + .04f)))
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
        }; syncBaseNavigation(slide.projectId) }
    }
    fun duplicateSlide(id: Long): Long {
        val story = read()
        val original = story.slides.first { it.id == id }
        var newId = 0L
        transaction {
            newId = createSlide(original.projectId, "${original.name} (copia)",synchronizeNavigation=false)
            saveSlide(original.copy(id = newId))
            story.elements.filter { it.slideId == id && (!story.projects.first {p->p.id==original.projectId}.automaticBaseNavigation || it.baseNavigation==null) }.forEach { element ->
                // Preserve stable destinations, including links to the source slide itself.
                saveElement(element.copy(id = 0, slideId = newId))
            }
            val ids = story.slides.filter { it.projectId == original.projectId }.map { it.id }.toMutableList()
            ids.add(ids.indexOf(id) + 1, newId)
            ids.forEachIndexed { index, slideId -> update("slides", ContentValues().apply { put("sort_order", index) }, "id=?", arrayOf(slideId.toString())) }
            syncBaseNavigation(original.projectId)
        }
        return newId
    }
    fun moveSlideTo(id: Long, index: Int) {
        val slides=read().slides
        val own=slides.find {it.id==id} ?: return
        val from=slides.filter {it.projectId==own.projectId}.indexOfFirst {it.id==id}
        reorderSlide(id,index-from)
    }
    /** OFF keeps existing base buttons as manual snapshots; it never rewrites their targets. */
    fun setAutomaticBaseNavigation(projectId: Long, enabled: Boolean) {
        transaction {
            update("projects",ContentValues().apply {put("automatic_base_navigation",enabled)},"id=?",arrayOf(projectId.toString()))
            if(enabled) syncBaseNavigation(projectId)
        }
    }
    fun syncBaseNavigation(projectId: Long) {transaction {syncBaseNavigationRows(projectId)}}
    private fun syncBaseNavigationRows(projectId: Long) {
        val story=read()
        if(story.projects.none {it.id==projectId && it.automaticBaseNavigation}) return
        val slides=story.slides.filter {it.projectId==projectId}
        slides.forEachIndexed {index,slide ->
            listOf("previous" to slides.getOrNull(index-1)?.id,"next" to slides.getOrNull(index+1)?.id).forEach {(role,target)->
                val existing=story.elements.filter {it.slideId==slide.id && it.baseNavigation==role}
                existing.drop(1).forEach {writableDatabase.delete("elements","id=?",arrayOf(it.id.toString()))}
                if(target==null) existing.firstOrNull()?.let {writableDatabase.delete("elements","id=?",arrayOf(it.id.toString()))}
                else if(existing.isNotEmpty()) writableDatabase.update("elements",ContentValues().apply {put("target_id",target)},"id=?",arrayOf(existing.first().id.toString()))
                else {
                    // A newly needed counterpart inherits an explicitly chosen size, never restyles old buttons.
                    val peer=story.elements.firstOrNull {it.slideId==slide.id && it.baseNavigation==(if(role=="previous") "next" else "previous")}
                    saveElement(Element(0,slide.id,"button",if(role=="previous") "← Anterior" else "Siguiente →",
                        x=if(role=="previous") .045f else .955f,y=.94f,width=peer?.width ?: .24f,height=peer?.height ?: .07f,
                        textColor=-16777216,backgroundColor=-1,targetId=target,baseNavigation=role,
                        style=VisualStyle(shape="rounded",alignment="center",textScale=.8f,backgroundOpacity=.75f)))
                }
            }
        }
    }

    fun background(slideId: Long, color: Int, image: String?) {
        val slide = read().slides.first { it.id == slideId }
        saveSlide(slide.copy(color = color, image = image))
    }
    fun editBackground(before: Slide, edited: Slide) {
        val current = read().slides.find { it.id == before.id } ?: return
        saveSlide(current.copy(
            draft = if(before.draft != edited.draft) edited.draft else current.draft,
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
            put("draft",s.draft);put("background_resource_id",s.backgroundResourceId.takeIf { s.image!=null });put("audio_resource_id",s.audioResourceId.takeIf { s.audio!=null });put("settings", s.settings()); put("auto_target_id", s.autoTargetId); put("color", s.color); put("image", s.image); put("sort_order", s.order)
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
    internal fun snapshotElements(slideId: Long): ElementSnapshot {
        val rows = mutableListOf<ContentValues>()
        readableDatabase.rawQuery("SELECT * FROM elements WHERE slide_id=? ORDER BY layer_order,id",arrayOf(slideId.toString())).use { cursor ->
            while (cursor.moveToNext()) rows += ContentValues().apply {
                cursor.columnNames.forEachIndexed { index, column ->
                    when (cursor.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> putNull(column)
                        android.database.Cursor.FIELD_TYPE_INTEGER -> put(column,cursor.getLong(index))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> put(column,cursor.getDouble(index))
                        android.database.Cursor.FIELD_TYPE_STRING -> put(column,cursor.getString(index))
                        android.database.Cursor.FIELD_TYPE_BLOB -> put(column,cursor.getBlob(index))
                    }
                }
            }
        }
        val paths = buildSet {
            rows.forEach { row ->
                row.getAsString("image")?.let(::add)
                addAll(Element(0,slideId,"image","").withSettings(row.getAsString("settings") ?: "{}").media.frames)
            }
        }
        return ElementSnapshot(slideId,rows,paths)
    }
    internal fun restoreElements(snapshot: ElementSnapshot) {
        transaction {
            rawQuery("SELECT 1 FROM slides WHERE id=?",arrayOf(snapshot.slideId.toString())).use { require(it.moveToFirst()) {"La lámina ya no existe"} }
            val currentIds = mutableSetOf<Long>()
            rawQuery("SELECT id FROM elements WHERE slide_id=?",arrayOf(snapshot.slideId.toString())).use { while(it.moveToNext()) currentIds += it.getLong(0) }
            val restoredIds = snapshot.rows.map {it.getAsLong("id")}.toSet()
            (currentIds-restoredIds).forEach {delete("elements","id=?",arrayOf(it.toString()))}
            snapshot.rows.forEach {row ->
                val id=row.getAsLong("id")
                if(id in currentIds) update("elements",row,"id=? AND slide_id=?",arrayOf(id.toString(),snapshot.slideId.toString()))
                else insertOrThrow("elements",null,row)
            }
        }
    }
    /** Called once all new references have been attached, never in the middle of imports. */
    fun cleanImages() {
        val story = read()
        val used = buildSet {
            addAll(undoMediaPaths)
            addAll(story.resources.map { it.path })
            story.templates.forEach { t -> t.slide.image?.let(::add);t.slide.audio?.let(::add);addAll(t.slide.media.frames);t.elements.forEach {e->e.image?.let(::add);addAll(e.media.frames)} }
            story.slides.forEach { s -> s.image?.let(::add); s.audio?.let(::add); addAll(s.media.frames) }
            story.elements.forEach { e -> e.image?.let(::add); addAll(e.media.frames) }
        }
        imageDir.listFiles()?.filter { it.path !in used }?.forEach { it.delete() }
    }
}

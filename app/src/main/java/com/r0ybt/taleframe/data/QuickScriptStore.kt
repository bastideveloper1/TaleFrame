package com.r0ybt.taleframe.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Uses the repository's single writer; its undo is separate from the editor history. */
class QuickScriptStore(private val repo: StoryRepository) {
    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE projects ADD COLUMN quick_script_input TEXT NOT NULL DEFAULT ''")
            db.execSQL("""CREATE TABLE quick_script_drafts(id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                original_text TEXT NOT NULL)""")
            db.execSQL("""CREATE TABLE quick_script_assignments(id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                draft_id INTEGER NOT NULL REFERENCES quick_script_drafts(id) ON DELETE CASCADE,
                slide_id INTEGER REFERENCES slides(id) ON DELETE SET NULL,
                start_offset INTEGER NOT NULL CHECK(start_offset>=0),
                end_offset INTEGER NOT NULL CHECK(end_offset>start_offset),
                dirty INTEGER NOT NULL DEFAULT 0 CHECK(dirty IN (0,1)))""")
            db.execSQL("CREATE INDEX quick_script_drafts_project ON quick_script_drafts(project_id)")
            db.execSQL("CREATE INDEX quick_script_assignments_project ON quick_script_assignments(project_id)")
            db.execSQL("CREATE INDEX quick_script_assignments_slide ON quick_script_assignments(slide_id)")
            // Even edits later reversed by editor Undo are modifications: never delete that slide here.
            db.execSQL("""CREATE TRIGGER quick_script_slide_changed AFTER UPDATE ON slides BEGIN
                UPDATE quick_script_assignments SET dirty=1 WHERE slide_id=NEW.id; END""")
            listOf("INSERT" to "NEW", "DELETE" to "OLD").forEach { (operation, row) ->
                db.execSQL("""CREATE TRIGGER quick_script_element_${operation.lowercase()} AFTER $operation ON elements BEGIN
                    UPDATE quick_script_assignments SET dirty=1 WHERE slide_id=$row.slide_id; END""")
            }
            db.execSQL("""CREATE TRIGGER quick_script_element_update AFTER UPDATE ON elements BEGIN
                UPDATE quick_script_assignments SET dirty=1 WHERE slide_id=OLD.slide_id OR slide_id=NEW.slide_id; END""")
        }
    }

    fun read(projectId: Long): QuickScriptState {
        val db = repo.readableDatabase
        val input = db.rawQuery("SELECT id,substr(quick_script_input,1,65536) FROM projects WHERE id=?", arrayOf(projectId.toString())).use {
            require(it.moveToFirst()) { "El proyecto ya no existe" }; sqliteText(db, "projects", "quick_script_input", it.getLong(0), it.getString(1)).orEmpty()
        }
        val drafts = mutableListOf<ScriptDraft>()
        db.rawQuery("SELECT id,substr(original_text,1,65536) FROM quick_script_drafts WHERE project_id=? ORDER BY id", arrayOf(projectId.toString())).use {
            while (it.moveToNext()) drafts += ScriptDraft(it.getLong(0), sqliteText(db, "quick_script_drafts", "original_text", it.getLong(0), it.getString(1)).orEmpty())
        }
        val assignments = mutableListOf<ScriptAssignment>()
        db.rawQuery("SELECT id,draft_id,slide_id,start_offset,end_offset,dirty FROM quick_script_assignments WHERE project_id=? ORDER BY id", arrayOf(projectId.toString())).use {
            while (it.moveToNext()) assignments += ScriptAssignment(it.getLong(0), it.getLong(1), if (it.isNull(2)) null else it.getLong(2), it.getInt(3), it.getInt(4), it.getInt(5) != 0)
        }
        return QuickScriptState(projectId, input, drafts, assignments)
    }

    fun saveInput(projectId: Long, text: String) {
        check(repo.writableDatabase.update("projects", ContentValues().apply { put("quick_script_input", text) }, "id=?", arrayOf(projectId.toString())) == 1)
    }

    fun addText(projectId: Long, text: String) = transaction {
        require(text.isNotEmpty()) { "Pega o escribe un texto primero" }
        insertOrThrow("quick_script_drafts", null, ContentValues().apply { put("project_id", projectId); put("original_text", text) })
        saveInput(projectId, "")
    }

    fun createSlide(projectId: Long, draftId: Long, start: Int, end: Int, insertion: ScriptInsertionPoint = ScriptInsertionPoint()): Long {
        var slideId = 0L
        transaction {
            val state = read(projectId)
            val draft = state.drafts.firstOrNull { it.id == draftId } ?: error("El texto ya no está disponible")
            require(state.isPendingRange(draftId, start, end)) { "Selecciona un fragmento pendiente válido, sin texto ya asignado" }
            val ordered = orderedSlides(projectId)
            val insertionIndex = scriptInsertionIndex(ordered.map { it.first }, insertion)
            // Default creation with navigation synchronization explicitly disabled to preserve older slides.
            val number = rawQuery("SELECT COUNT(*)+1 FROM slides WHERE project_id=?", arrayOf(projectId.toString())).use { it.moveToFirst(); it.getInt(0) }
            slideId = repo.createSlide(projectId, "Guion $number", synchronizeNavigation = false)
            repo.setNarrativeScript(slideId, draft.original.substring(start, end))
            repo.setDraft(slideId, true)
            changeOrders(projectId, scriptInsertionOrders(ordered, insertionIndex, slideId))
            insertOrThrow("quick_script_assignments", null, ContentValues().apply {
                put("project_id", projectId); put("draft_id", draftId); put("slide_id", slideId)
                put("start_offset", start); put("end_offset", end)
            })
        }
        return slideId
    }

    fun undo(projectId: Long) = transaction {
        val last = read(projectId).assignments.lastOrNull() ?: error("No hay asignaciones que deshacer")
        val id = last.slideId
        require(id != null && !last.dirty) { "No se puede deshacer con seguridad: la lámina fue modificada o eliminada. Se conservan el guion y el texto original." }
        val referenced = rawQuery("""SELECT 1 FROM elements WHERE target_id=? UNION ALL
            SELECT 1 FROM slides WHERE auto_target_id=? UNION ALL
            SELECT 1 FROM projects WHERE initial_slide_id=? LIMIT 1""", arrayOf(id.toString(), id.toString(), id.toString())).use { it.moveToFirst() }
        val hasElements = rawQuery("SELECT 1 FROM elements WHERE slide_id=? LIMIT 1", arrayOf(id.toString())).use { it.moveToFirst() }
        require(!referenced && !hasElements) { "No se puede deshacer con seguridad: la lámina tiene elementos o conexiones. Se conservan todos los datos." }
        val deletedOrder = orderedSlides(projectId).first { it.first == id }.second
        // Remove only the slide recorded by this tool; never synchronize or reorder other slides.
        check(delete("slides", "id=? AND project_id=?", arrayOf(id.toString(), projectId.toString())) == 1)
        changeOrders(projectId, orderedSlides(projectId).filter { it.second > deletedOrder }.map { it.first to it.second - 1 })
        check(delete("quick_script_assignments", "id=?", arrayOf(last.id.toString())) == 1)
    }

    private fun orderedSlides(projectId: Long): List<Pair<Long, Int>> {
        val rows = mutableListOf<Pair<Long, Int>>()
        repo.readableDatabase.rawQuery("SELECT id,sort_order FROM slides WHERE project_id=? ORDER BY sort_order,id", arrayOf(projectId.toString())).use {
            while (it.moveToNext()) rows += it.getLong(0) to it.getInt(1)
        }
        return rows
    }

    /** Only this tool's order shifts may preserve its dirty flags; content edits remain protected. */
    private fun changeOrders(projectId: Long, changes: List<Pair<Long, Int>>) {
        val db = repo.writableDatabase
        check(db.inTransaction())
        val changedIds = changes.map { it.first }.toSet()
        val intact = mutableListOf<Long>()
        db.rawQuery("SELECT id,slide_id FROM quick_script_assignments WHERE project_id=? AND dirty=0", arrayOf(projectId.toString())).use {
            while (it.moveToNext()) if (!it.isNull(1) && it.getLong(1) in changedIds) intact += it.getLong(0)
        }
        changes.forEach { (id, order) ->
            check(db.update("slides", ContentValues().apply { put("sort_order", order) }, "id=? AND project_id=?", arrayOf(id.toString(), projectId.toString())) == 1)
        }
        // The only mutations above were sort_order. Never clear a previously dirty assignment.
        intact.forEach { id -> db.update("quick_script_assignments", ContentValues().apply { put("dirty", 0) }, "id=?", arrayOf(id.toString())) }
    }

    private fun transaction(action: SQLiteDatabase.() -> Unit) {
        val db = repo.writableDatabase
        db.beginTransaction()
        try { db.action(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}

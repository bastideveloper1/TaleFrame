package com.r0ybt.taleframe.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/** Small SQLite repository. Foreign keys enforce ownership and clear deleted destinations. */
class StoryRepository(context: Context, databaseName: String = "taleframe.db") : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    private val imageDir = File(context.filesDir, if (databaseName == "taleframe.db") "backgrounds" else "backgrounds-$databaseName").apply { mkdirs() }
    private val resolver = context.contentResolver
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE slides(id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE, name TEXT NOT NULL, color INTEGER NOT NULL DEFAULT -1, image TEXT)")
        db.execSQL("CREATE TABLE elements(id INTEGER PRIMARY KEY AUTOINCREMENT, slide_id INTEGER NOT NULL REFERENCES slides(id) ON DELETE CASCADE, kind TEXT NOT NULL CHECK(kind IN ('text','button')), text TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, text_color INTEGER NOT NULL, background_color INTEGER NOT NULL, target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX slides_project ON slides(project_id)")
        db.execSQL("CREATE INDEX elements_slide ON elements(slide_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun read(): Story {
        val projects = mutableListOf<Project>(); val slides = mutableListOf<Slide>(); val elements = mutableListOf<Element>()
        readableDatabase.rawQuery("SELECT id,name FROM projects ORDER BY id", null).use { c -> while(c.moveToNext()) projects += Project(c.getLong(0), c.getString(1)) }
        readableDatabase.rawQuery("SELECT id,project_id,name,color,image FROM slides ORDER BY id", null).use { c -> while(c.moveToNext()) slides += Slide(c.getLong(0),c.getLong(1),c.getString(2),c.getInt(3),c.getString(4)) }
        readableDatabase.rawQuery("SELECT id,slide_id,kind,text,x,y,text_color,background_color,target_id FROM elements ORDER BY id", null).use { c -> while(c.moveToNext()) elements += Element(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getFloat(4),c.getFloat(5),c.getInt(6),c.getInt(7),if(c.isNull(8)) null else c.getLong(8)) }
        return Story(projects, slides, elements)
    }
    fun createProject(name: String) = writableDatabase.insertOrThrow("projects", null, ContentValues().apply { put("name", name.trim()) })
    fun createSlide(projectId: Long, name: String) = writableDatabase.insertOrThrow("slides", null, ContentValues().apply { put("project_id", projectId); put("name", name.trim()) })
    fun rename(table: String, id: Long, name: String) { require(table in listOf("projects", "slides")); writableDatabase.update(table, ContentValues().apply { put("name",name.trim()) }, "id=?", arrayOf(id.toString())) }
    fun delete(table: String, id: Long) {
        require(table in listOf("projects", "slides", "elements"))
        writableDatabase.delete(table,"id=?", arrayOf(id.toString()))
        cleanImages()
    }
    fun saveElement(e: Element) {
        if (e.targetId != null) {
            readableDatabase.rawQuery("SELECT 1 FROM slides a JOIN slides b ON a.project_id=b.project_id WHERE a.id=? AND b.id=?", arrayOf(e.slideId.toString(),e.targetId.toString())).use { require(it.moveToFirst()) { "El destino debe pertenecer al proyecto" } }
        }
        val values = ContentValues().apply { put("slide_id",e.slideId); put("kind",e.kind); put("text",e.text); put("x",boundedPosition(e.x)); put("y",boundedPosition(e.y)); put("text_color",e.textColor); put("background_color",e.backgroundColor); put("target_id",e.targetId) }
        if(e.id == 0L) writableDatabase.insertOrThrow("elements",null,values) else writableDatabase.update("elements",values,"id=?",arrayOf(e.id.toString()))
    }
    fun background(slideId: Long, color: Int, image: String?) {
        writableDatabase.update("slides",ContentValues().apply { put("color",color); put("image",image) },"id=?",arrayOf(slideId.toString()))
        cleanImages()
    }
    // Copy to private storage: no provider URI grant is needed after import or restart.
    fun importImage(uri: Uri): String {
        val file = File(imageDir, UUID.randomUUID().toString())
        try {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while(true) { val n = input.read(buffer); if(n < 0) break; total += n; require(total <= 40L * 1024 * 1024) { "La imagen supera 40 MB" }; output.write(buffer,0,n) }
            } } ?: error("No se pudo abrir la imagen")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path,options)
            require(options.outWidth > 0 && options.outHeight > 0) { "El archivo no es una imagen compatible" }
            return file.path
        } catch(e: Exception) { file.delete(); throw e }
    }
    private fun cleanImages() {
        val used = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT image FROM slides WHERE image IS NOT NULL",null).use { while(it.moveToNext()) used += it.getString(0) }
        imageDir.listFiles()?.filter { it.path !in used }?.forEach { it.delete() }
    }
}

package com.r0ybt.taleframe.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File

/** Project-scoped catalog. All calls run in the repository's existing serial IO writer. */
class LibraryStore(private val repository: StoryRepository) {
    private val db get() = repository.writableDatabase
    fun fill(story: Story): Story {
        val resources=mutableListOf<Resource>(); val characters=mutableListOf<Character>()
        val expressions=mutableListOf<Expression>(); val presets=mutableListOf<Preset>()
        db.rawQuery("SELECT id,project_id,name,type,category,path,revision FROM resources ORDER BY name COLLATE NOCASE,id",null).use { c -> while(c.moveToNext()) resources+=Resource(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6)) }
        db.rawQuery("SELECT id,project_id,name,description,portrait_id,dialog_preset_id FROM characters ORDER BY name COLLATE NOCASE,id",null).use { c -> while(c.moveToNext()) characters+=Character(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),if(c.isNull(4)) null else c.getLong(4),if(c.isNull(5)) null else c.getLong(5)) }
        db.rawQuery("SELECT id,character_id,name,resource_id,sort_order FROM expressions ORDER BY character_id,sort_order,id",null).use { c -> while(c.moveToNext()) expressions+=Expression(c.getLong(0),c.getLong(1),c.getString(2),c.getLong(3),c.getInt(4)) }
        db.rawQuery("SELECT id,project_id,name,kind,text_color,background_color,settings FROM presets ORDER BY name COLLATE NOCASE,id",null).use { c -> while(c.moveToNext()) presets+=Preset(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5)).withSettings(c.getString(6)) }
        return story.copy(resources=resources,characters=characters,expressions=expressions,presets=presets)
    }
    fun addResource(projectId: Long, name: String, type: String, category: String, path: String): Long {
        require(type in listOf("image","gif","video","audio"))
        require(category in listOf("background","image","gif","video","audio"))
        require(name.isNotBlank())
        db.rawQuery("SELECT id FROM resources WHERE project_id=? AND path=? AND type=?",arrayOf(projectId.toString(),path,type)).use { if(it.moveToFirst()) return it.getLong(0) }
        return db.insertOrThrow("resources",null,ContentValues().apply { put("project_id",projectId);put("name",name.trim());put("type",type);put("category",category);put("path",path) })
    }
    fun importResource(projectId: Long, uri: Uri, name: String, type: String, category: String): Long {
        try { return addResource(projectId,name,type,category,repository.importMedia(uri,type)) }
        catch(e:Exception) { repository.cleanImages(); throw e }
    }
    fun renameResource(id: Long, name: String) { require(name.isNotBlank()); db.update("resources",ContentValues().apply { put("name",name.trim()) },"id=?",arrayOf(id.toString())) }
    fun replaceResource(id: Long, uri: Uri) {
        val original=repository.read().resources.firstOrNull { it.id==id } ?: return
        try {
            val path=repository.importMedia(uri,original.type)
            require(repository.read().resources.none { it.id!=id && it.projectId==original.projectId && it.type==original.type && it.path==path }) { "Este archivo ya está en Biblioteca. Reutilízalo desde su recurso existente." }
            db.update("resources",ContentValues().apply { put("path",path);put("revision",original.revision+1) },"id=?",arrayOf(id.toString()))
        } finally { repository.cleanImages() }
    }
    fun deleteResource(id: Long) {
        val story=repository.read(); val resource=story.resources.find { it.id==id } ?: return
        val usage=resourceUsage(story,resource)
        require(usage.total==0) { "Este recurso está utilizado en ${usage.description}. Retira esas referencias antes de eliminarlo." }
        db.delete("resources","id=?",arrayOf(id.toString())); repository.cleanImages()
    }
    fun savePreset(p: Preset): Long {
        require(p.name.isNotBlank()); require(p.kind in listOf("dialog","narrator","button","action"))
        if(p.id!=0L) require(repository.read().presets.any { it.id==p.id && it.projectId==p.projectId && it.kind==p.kind })
        val values=ContentValues().apply { put("project_id",p.projectId);put("name",p.name.trim());put("kind",p.kind);put("text_color",p.textColor);put("background_color",p.backgroundColor);put("settings",p.settings()) }
        return if(p.id==0L) db.insertOrThrow("presets",null,values) else { db.update("presets",values,"id=?",arrayOf(p.id.toString()));p.id }
    }
    fun deletePreset(id: Long) {
        val story=repository.read(); val preset=story.presets.find { it.id==id } ?: return
        require(preset.kind!="narrator") { "El Narrador se puede editar, pero pertenece al proyecto." }
        require(story.characters.none { it.dialogPresetId==id }) { "Este estilo está asociado a un personaje. Cambia su estilo antes de eliminarlo." }
        db.delete("presets","id=?",arrayOf(id.toString())) // Old instances retain their style snapshots.
    }
    fun saveCharacter(c: Character): Long {
        require(c.name.isNotBlank())
        val story=repository.read()
        val previous=story.characters.find {it.id==c.id}
        if(previous==null || previous.name!=c.name) require(story.characters.none {
            it.id!=c.id && it.projectId==c.projectId && normalizedCharacterName(it.name)==normalizedCharacterName(c.name)
        }) { "Ya existe un personaje con este nombre." }
        if(c.portraitId!=null) require(story.resources.any { it.id==c.portraitId && it.projectId==c.projectId && it.type=="image" }) { "El portrait debe ser una imagen de este proyecto" }
        if(c.dialogPresetId!=null) require(story.presets.any { it.id==c.dialogPresetId && it.projectId==c.projectId && it.kind=="dialog" }) { "Escoge un preset de diálogo de este proyecto" }
        if(c.id!=0L) require(story.characters.any { it.id==c.id && it.projectId==c.projectId })
        var id=c.id
        db.beginTransaction()
        try {
            val preset=c.dialogPresetId ?: savePreset(Preset(0,c.projectId,"Diálogo de ${c.name}",style=VisualStyle(shape="rounded",showName=true)))
            val values=ContentValues().apply { put("project_id",c.projectId);put("name",c.name.trim());put("description",c.description.trim());put("portrait_id",c.portraitId);put("dialog_preset_id",preset) }
            if(id==0L) id=db.insertOrThrow("characters",null,values) else db.update("characters",values,"id=?",arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return id
    }
    fun deleteCharacter(id: Long) { db.delete("characters","id=?",arrayOf(id.toString())) }
    fun saveExpression(e: Expression): Long {
        require(e.name.isNotBlank()); val story=repository.read()
        val character=story.characters.first { it.id==e.characterId }
        require(story.resources.any { it.id==e.resourceId && it.projectId==character.projectId && it.type=="image" }) { "La expresión debe ser una imagen de este proyecto" }
        if(e.id!=0L) require(story.expressions.any { it.id==e.id && it.characterId==e.characterId })
        val order=if(e.id==0L) (story.expressions.filter { it.characterId==e.characterId }.maxOfOrNull { it.order } ?: -1)+1 else e.order
        val values=ContentValues().apply { put("character_id",e.characterId);put("name",e.name.trim());put("resource_id",e.resourceId);put("sort_order",order) }
        return if(e.id==0L) db.insertOrThrow("expressions",null,values) else { db.update("expressions",values,"id=?",arrayOf(e.id.toString()));e.id }
    }
    fun deleteExpression(id: Long) { db.delete("expressions","id=?",arrayOf(id.toString())) }
    fun reorderExpression(id: Long, delta: Int) {
        val story=repository.read();val expression=story.expressions.first { it.id==id }
        val ordered=story.expressions.filter { it.characterId==expression.characterId }.map { it.id }.toMutableList()
        val from=ordered.indexOf(id); val to=(from+delta).coerceIn(0,ordered.lastIndex)
        ordered.removeAt(from);ordered.add(to,id)
        db.beginTransaction()
        try { ordered.forEachIndexed { index,eid -> db.update("expressions",ContentValues().apply { put("sort_order",index) },"id=?",arrayOf(eid.toString())) };db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    fun insertResource(slideId: Long, resourceId: Long, usage: String = "element"): Long? {
        val story=repository.read();val slide=story.slides.first { it.id==slideId };val r=story.resources.first { it.id==resourceId }
        require(slide.projectId==r.projectId) { "El recurso debe pertenecer al proyecto" }
        when(usage) {
            "audio" -> { require(r.type=="audio");repository.saveSlide(slide.copy(audio=r.path,audioResourceId=r.id,audioRevision=slide.audioRevision+1));return null }
            "background" -> { require(r.type!="audio");repository.saveSlide(slide.copy(image=r.path,backgroundResourceId=r.id,media=MediaOptions(type=r.type,revision=slide.media.revision+1)));return null }
            else -> { require(r.type!="audio");return repository.saveElement(Element(0,slideId,"image","",image=r.path,width=.4f,height=.4f,resourceId=r.id,media=MediaOptions(type=r.type,revision=r.revision))) }
        }
    }
    fun insertCharacter(slideId: Long, expressionIds: List<Long>): Long {
        require(expressionIds.isNotEmpty());val story=repository.read();val slide=story.slides.first { it.id==slideId }
        val expressions=expressionIds.map { id -> story.expressions.first { it.id==id } }
        val character=story.characters.first { it.id==expressions.first().characterId }
        require(character.projectId==slide.projectId && expressions.all { it.characterId==character.id })
        val resources=expressions.map { e -> story.resources.first { it.id==e.resourceId } }
        return repository.saveElement(Element(0,slideId,"image","",image=resources.first().path,width=.4f,height=.5f,
            resourceId=resources.first().id,characterId=character.id,expressionId=expressions.first().id,sourceName="${character.name} / ${expressions.first().name}",expressionFrames=expressionIds,
            media=MediaOptions(type=if(resources.size>1) "slideshow" else "image",frames=if(resources.size>1) resources.map { it.path } else emptyList())))
    }
    fun changeExpression(elementId: Long, expressionId: Long) {
        val story=repository.read();val element=story.elements.firstOrNull { it.id==elementId } ?: return
        val e=story.expressions.first { it.id==expressionId }
        require(element.kind=="image" && element.characterId==e.characterId) { "Escoge una expresión del mismo personaje" }
        val c=story.characters.first { it.id==e.characterId };val r=story.resources.first { it.id==e.resourceId }
        repository.saveElement(element.copy(image=r.path,resourceId=r.id,expressionId=e.id,expressionFrames=listOf(e.id),sourceName="${c.name} / ${e.name}",
            media=element.media.copy(type="image",frames=emptyList(),revision=element.media.revision+1)))
    }
    fun insertDialog(slideId: Long, characterId: Long? = null, presetId: Long? = null): Long {
        val story=repository.read();val slide=story.slides.first { it.id==slideId }
        val c=characterId?.let { id -> story.characters.first { it.id==id && it.projectId==slide.projectId } }
        val p=(c?.dialogPresetId ?: presetId)?.let { id -> story.presets.first { it.id==id && it.projectId==slide.projectId && it.kind in listOf("dialog","narrator") } }
        return repository.saveElement(styledElement(slideId,p,c?.name ?: "",c?.id))
    }
    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE resources(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,type TEXT NOT NULL CHECK(type IN ('image','gif','video','audio')),category TEXT NOT NULL,path TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0,UNIQUE(project_id,path,type))""")
            db.execSQL("""CREATE TABLE presets(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,kind TEXT NOT NULL CHECK(kind IN ('dialog','narrator','button','action')),text_color INTEGER NOT NULL DEFAULT -1,background_color INTEGER NOT NULL DEFAULT -1,settings TEXT NOT NULL DEFAULT '{}')""")
            db.execSQL("CREATE UNIQUE INDEX narrator_project ON presets(project_id) WHERE kind='narrator'")
            db.execSQL("""CREATE TABLE characters(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',portrait_id INTEGER REFERENCES resources(id) ON DELETE SET NULL,dialog_preset_id INTEGER REFERENCES presets(id) ON DELETE SET NULL)""")
            db.execSQL("""CREATE TABLE expressions(id INTEGER PRIMARY KEY AUTOINCREMENT,character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE,name TEXT NOT NULL,resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,sort_order INTEGER NOT NULL DEFAULT 0)""")
            listOf("resources","presets","characters").forEach { db.execSQL("CREATE INDEX ${it}_project ON $it(project_id)") }
            db.execSQL("CREATE INDEX expressions_character ON expressions(character_id,sort_order)")
            listOf("resource_id" to "resources","character_id" to "characters","expression_id" to "expressions","preset_id" to "presets").forEach { (column,table) -> db.execSQL("ALTER TABLE elements ADD COLUMN $column INTEGER REFERENCES $table(id) ON DELETE SET NULL") }
            db.execSQL("ALTER TABLE slides ADD COLUMN background_resource_id INTEGER REFERENCES resources(id) ON DELETE SET NULL")
            db.execSQL("ALTER TABLE slides ADD COLUMN audio_resource_id INTEGER REFERENCES resources(id) ON DELETE SET NULL")
        }
        fun seedNarrator(db: SQLiteDatabase, projectId: Long) {
            val p=Preset(0,projectId,"Narrador",kind="narrator",style=VisualStyle(shape="rectangle",backgroundOpacity=.65f))
            db.insertOrThrow("presets",null,ContentValues().apply { put("project_id",projectId);put("name",p.name);put("kind",p.kind);put("text_color",p.textColor);put("background_color",p.backgroundColor);put("settings",p.settings()) })
        }
        fun backfill(db: SQLiteDatabase) {
            db.rawQuery("SELECT id FROM projects",null).use { while(it.moveToNext()) seedNarrator(db,it.getLong(0)) }
            var ordinal=0
            fun add(project:Long,path:String?,type:String,category:String) {
                if(path==null) return
                ordinal++
                val label=File(path).name.takeIf { it.length<40 } ?: "${when(type) { "gif"->"GIF";"video"->"Video";"audio"->"Audio";else->"Imagen" }} existente $ordinal"
                db.insertWithOnConflict("resources",null,ContentValues().apply { put("project_id",project);put("name",label);put("type",type);put("category",category);put("path",path) },SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.rawQuery("SELECT project_id,image,settings FROM slides",null).use { c -> while(c.moveToNext()) {
                val project=c.getLong(0);val s=Slide(0,project,"").withSettings(c.getString(2),null)
                add(project,c.getString(1),if(s.media.type=="slideshow") "image" else s.media.type,"background")
                add(project,s.audio,"audio","audio");s.media.frames.forEach { add(project,it,"image","image") }
            } }
            db.rawQuery("SELECT s.project_id,e.image,e.settings FROM elements e JOIN slides s ON s.id=e.slide_id",null).use { c -> while(c.moveToNext()) {
                val e=Element(0,0,"image","").withSettings(c.getString(2));val type=if(e.media.type=="slideshow") "image" else e.media.type
                add(c.getLong(0),c.getString(1),type,type);e.media.frames.forEach { add(c.getLong(0),it,"image","image") }
            } }
        }
    }
}

package com.r0ybt.taleframe

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CompositionPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block:(StoryRepository,String)->Unit) {
        val name="composition-${UUID.randomUUID()}.db"
        try {StoryRepository(context,name).use {block(it,name)}} finally {context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively()}
    }
    private fun image(repo:StoryRepository,project:Long):Long {
        val file=File(context.cacheDir,"${UUID.randomUUID()}.png")
        try {
            Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).also {b->b.eraseColor(0x80FF7799.toInt());file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            return repo.library.importResource(project,Uri.fromFile(file),"Retrato","image","image")
        } finally {file.delete()}
    }
    @Test fun schemaFourMigrationKeepsEveryPreviousColumnAndStableIds() {
        val name="v4-composition-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use {db->
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE slides(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,color INTEGER NOT NULL DEFAULT -1,image TEXT,sort_order INTEGER NOT NULL DEFAULT 0,background_mode TEXT NOT NULL DEFAULT 'fill',background_scale REAL NOT NULL DEFAULT 1,background_x REAL NOT NULL DEFAULT 0,background_y REAL NOT NULL DEFAULT 0,background_locked INTEGER NOT NULL DEFAULT 0,settings TEXT NOT NULL DEFAULT '{}',auto_target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL)")
            db.execSQL("CREATE TABLE elements(id INTEGER PRIMARY KEY AUTOINCREMENT,slide_id INTEGER NOT NULL REFERENCES slides(id) ON DELETE CASCADE,kind TEXT NOT NULL CHECK(kind IN ('text','button','image')),text TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,text_color INTEGER NOT NULL,background_color INTEGER NOT NULL,target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL,image TEXT,width REAL NOT NULL DEFAULT 0,height REAL NOT NULL DEFAULT 0,rotation REAL NOT NULL DEFAULT 0,flipped INTEGER NOT NULL DEFAULT 0,opacity REAL NOT NULL DEFAULT 1,locked INTEGER NOT NULL DEFAULT 0,layer_order INTEGER NOT NULL DEFAULT 0,settings TEXT NOT NULL DEFAULT '{}')")
            LibraryStore.createSchema(db)
            db.execSQL("INSERT INTO projects(id,name) VALUES(7,'Anterior')")
            db.execSQL("INSERT INTO slides(id,project_id,name,settings,auto_target_id) VALUES(11,7,'A','{\"autoEnabled\":true,\"autoSeconds\":8,\"transition\":{\"type\":\"left\",\"ms\":400}}',19),(19,7,'B','{}',NULL)")
            db.execSQL("INSERT INTO resources(id,project_id,name,type,category,path) VALUES(20,7,'Imagen','image','image','/privado/imagen.png')")
            db.execSQL("INSERT INTO presets(id,project_id,name,kind,settings) VALUES(30,7,'Voz','dialog','{\"style\":{\"shape\":\"oval\",\"showName\":true}}')")
            db.execSQL("INSERT INTO characters(id,project_id,name,portrait_id,dialog_preset_id) VALUES(40,7,'Luna',20,30)")
            db.execSQL("INSERT INTO expressions(id,character_id,name,resource_id) VALUES(50,40,'Feliz',20)")
            db.execSQL("INSERT INTO elements(id,slide_id,kind,text,x,y,text_color,background_color,target_id,image,width,height,rotation,flipped,opacity,locked,layer_order,settings,resource_id,character_id,expression_id,preset_id) VALUES(60,11,'image','',.2,.3,-1,0,19,'/privado/imagen.png',.3,.5,12,1,.4,1,3,'{\"media\":{\"type\":\"slideshow\",\"frames\":[\"/privado/imagen.png\"],\"seconds\":2},\"source\":\"Luna / Feliz\"}',20,40,50,30)")
            db.execSQL("UPDATE sqlite_sequence SET seq=900 WHERE name='elements'");db.version=4
        }
        fun rows(db:SQLiteDatabase,table:String,columns:String)=db.rawQuery("SELECT $columns FROM $table ORDER BY id",null).use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}
        val old=SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use {db->listOf("projects","slides","elements","resources","characters","expressions","presets").associateWith {table->val columns=db.rawQuery("PRAGMA table_info($table)",null).use {c->buildList {while(c.moveToNext()) add(c.getString(1))}};columns.joinToString(",") to rows(db,table,columns.joinToString(","))}}
        try {StoryRepository(context,name).use {repo->
            assertEquals(5,repo.readableDatabase.version)
            old.forEach {(table,pair)->assertEquals(table,pair.second,rows(repo.readableDatabase,table,pair.first))}
            val story=repo.read();assertFalse(story.slides.first().draft);assertFalse(story.projects.single().skipDrafts);assertNull(story.projects.single().coverResourceId)
            assertEquals(11L,initialSlide(story.projects.single(),story.slides)?.id);assertTrue(repo.saveElement(Element(0,11,"text","Nuevo"))>900)
            assertTrue(story.templates.isEmpty());repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertEquals(0,it.count)}
        }} finally {context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively()}
    }
    @Test fun compositionSnapshotSharesFilesPreservesCharactersAndClearsAllDestinations()=fixture {repo,name->
        val p=repo.createProject("P");val s=repo.createSlide(p,"Original");val destination=repo.createSlide(p,"Destino");val r=image(repo,p)
        val c=repo.library.saveCharacter(Character(0,p,"Luna",portraitId=r));val exp=repo.library.saveExpression(Expression(0,c,"Feliz",r));val e=repo.library.insertCharacter(s,listOf(exp))
        val style=VisualStyle(shape="thought",bold=true,italic=true,buttonEffect="pulse",textScale=1.2f)
        repo.saveElement(repo.read().elements.first {it.id==e}.copy(x=.7f,width=.3f,rotation=15f,flipped=true,locked=true,opacity=.4f))
        repo.saveElement(Element(0,s,"button","Seguir",targetId=destination,width=.3f,height=.2f,style=style,transition=Transition("right",600)))
        repo.saveElement(Element(0,s,"image","",width=.4f,height=.3f,panel=PanelOptions("manual",1.4f,.2f,-.1f)))
        repo.saveSlide(repo.read().slides.first {it.id==s}.copy(autoEnabled=true,autoTargetId=destination,transition=Transition("left",400)))
        val original=repo.read();val templateId=repo.templates.saveSlide(s,"Conversación")
        val target=repo.templates.createSlide(p,"Nueva",templateId)
        val story=repo.read();val placed=story.elements.filter {it.slideId==target};val before=original.elements.filter {it.slideId==s}
        assertEquals(before.map {it.copy(id=0,slideId=0,targetId=null)},placed.map {it.copy(id=0,slideId=0,targetId=null)})
        assertTrue(placed.all {it.targetId==null});assertFalse(story.slides.first {it.id==target}.autoEnabled);assertNull(story.slides.first {it.id==target}.autoTargetId)
        assertEquals(1,File(context.filesDir,"backgrounds-$name").listFiles()!!.size)
        val snapshot=repo.read();StoryRepository(context,name).use {assertEquals(snapshot,it.read())}
        repo.templates.rename(templateId,"Renombrada");repo.templates.duplicate(templateId,p);assertEquals(2,repo.read().templates.size)
        repo.delete("slides",s);repo.delete("slides",target);repo.library.deleteCharacter(c)
        try {repo.library.deleteResource(r);fail("Template resource removed")} catch(_:IllegalArgumentException) {}
        val path=repo.read().resources.single().path
        repo.read().templates.forEach {repo.templates.delete(it.id)};repo.library.deleteResource(r);assertFalse(File(path).exists())
    }
    @Test fun coversDraftsInitialEntryAndCrossProjectValidationSurviveReopenAndDeletion()=fixture {repo,name->
        val p=repo.createProject("P");val other=repo.createProject("Otro");val a=repo.createSlide(p,"A");val b=repo.createSlide(p,"B");val foreign=repo.createSlide(other,"Ajena");val r=image(repo,p)
        repo.setCover(p,r);repo.setInitialSlide(p,b);repo.setDraft(b,true);repo.setSkipDrafts(p,true)
        val saved=repo.read();assertEquals(b,initialSlide(saved.projects.first(),saved.slides)?.id);assertEquals(a,playbackStart(saved.projects.first(),saved.slides)?.id)
        StoryRepository(context,name).use {assertEquals(saved,it.read())}
        try {repo.setCover(other,r);fail("Cross-project cover")} catch(_:IllegalArgumentException) {}
        try {repo.setInitialSlide(p,foreign);fail("Cross-project entry")} catch(_:IllegalArgumentException) {}
        try {repo.library.deleteResource(r);fail("Cover resource removed")} catch(_:IllegalArgumentException) {}
        repo.setCover(p,null);repo.library.deleteResource(r)
        repo.delete("slides",b);assertNull(repo.read().projects.first().initialSlideId);assertEquals(a,initialSlide(repo.read().projects.first(),repo.read().slides)?.id)
        repo.setDraft(a,true);assertNull(playbackStart(repo.read().projects.first(),repo.read().slides))
        repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertEquals(0,it.count)}
    }
    @Test fun templateRetainsOldFileAfterCatalogReplacementAndSanitizesDeletedDefinitions()=fixture {repo,name->
        val p=repo.createProject("P");val s=repo.createSlide(p,"A");val r=image(repo,p);val c=repo.library.saveCharacter(Character(0,p,"Luna"));val exp=repo.library.saveExpression(Expression(0,c,"Feliz",r))
        repo.library.insertCharacter(s,listOf(exp));val t=repo.templates.saveSlide(s,"Antes")
        val oldPath=repo.read().resources.single().path
        // Replacing the catalog path leaves the snapshot's original bytes available.
        val file=File(context.cacheDir,"${UUID.randomUUID()}.png")
        try {
            Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888).also {b->b.eraseColor(-1);file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            repo.library.replaceResource(r,Uri.fromFile(file))
        } finally {file.delete()}
        repo.delete("slides",s);repo.library.deleteCharacter(c);repo.cleanImages();assertTrue(File(oldPath).exists())
        val applied=repo.templates.createSlide(p,"Después",t);val element=repo.read().elements.first {it.slideId==applied}
        assertEquals(oldPath,element.image);assertNull(element.characterId);assertNull(element.expressionId);assertEquals("Luna / Feliz",element.sourceName)
        assertEquals(2,File(context.filesDir,"backgrounds-$name").listFiles()!!.size)
        val foreign=repo.createProject("Otro");try {repo.templates.createSlide(foreign,"Inválida",t);fail("Cross-project template")} catch(_:NoSuchElementException) {}
        repo.delete("slides",applied);repo.templates.delete(t);assertFalse(File(oldPath).exists())
    }
    @Test fun everyIncludedLayoutAppliesAsIndependentEditableLayersAndDuplicatesSafely()=fixture {repo,_->
        val p=repo.createProject("P")
        includedTemplates().forEach {t->
            val s=repo.templates.createSlide(p,t.name,t.id);val elements=repo.read().elements.filter {it.slideId==s}
            assertEquals(t.elements.size,elements.size);assertTrue(elements.all {it.targetId==null});assertTrue(elements.any {it.panel!=null})
            val panel=elements.first {it.panel!=null};repo.moveElement(panel.id,.2f,.3f);repo.resizeElement(panel.id,.25f,.35f)
            val copy=repo.duplicateSlide(s);assertEquals(repo.read().elements.filter {it.slideId==s}.map {it.copy(id=0,slideId=0)},repo.read().elements.filter {it.slideId==copy}.map {it.copy(id=0,slideId=0)})
        }
    }
}

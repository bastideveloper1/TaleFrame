package com.r0ybt.taleframe

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EditorPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun newEmptySlideHasNoDefaultTextAndExistingTextSurvivesReopen() {
        val name="empty-slide-${UUID.randomUUID()}.db"
        val repo=StoryRepository(context,name)
        var empty=0L
        var existing=0L
        try {
            val project=repo.createProject("Proyecto")
            existing=repo.createSlide(project,"Existente")
            repo.saveElement(Element(0,existing,"text","Conservar título"))
            empty=repo.templates.createSlide(project,"Lámina nueva",null)
            assertTrue(repo.read().elements.none {it.slideId==empty})
        } finally {repo.close()}
        val reopened=StoryRepository(context,name)
        try {
            assertEquals("Lámina nueva",reopened.read().slides.single {it.id==empty}.name)
            assertTrue(reopened.read().elements.none {it.slideId==empty})
            assertEquals("Conservar título",reopened.read().elements.single {it.slideId==existing}.text)
        } finally {reopened.close();context.deleteDatabase(name)}
    }

    @Test fun migrationKeepsMvpIdsCompositionsConnectionsAndSequence() {
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE slides(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,color INTEGER NOT NULL DEFAULT -1,image TEXT)")
            db.execSQL("CREATE TABLE elements(id INTEGER PRIMARY KEY AUTOINCREMENT,slide_id INTEGER NOT NULL REFERENCES slides(id) ON DELETE CASCADE,kind TEXT NOT NULL CHECK(kind IN ('text','button')),text TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,text_color INTEGER NOT NULL,background_color INTEGER NOT NULL,target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL)")
            db.execSQL("CREATE INDEX slides_project ON slides(project_id)")
            db.execSQL("CREATE INDEX elements_slide ON elements(slide_id)")
            db.execSQL("INSERT INTO projects VALUES(7,'MVP')")
            db.execSQL("INSERT INTO slides VALUES(11,7,'Primera',-1,NULL),(19,7,'Segunda',-16777216,NULL)")
            db.execSQL("INSERT INTO elements VALUES(31,11,'text','Conservar',.35,.25,-1,-16777216,NULL),(42,11,'button','Entrar',.65,.8,-1,-16777216,19),(100,19,'text','Temporal',0,0,-1,-1,NULL)")
            db.execSQL("DELETE FROM elements WHERE id=100")
            db.version = 1
        }
        val repo = StoryRepository(context, name)
        try {
            val story = repo.read()
            assertEquals(listOf(11L,19L), story.slides.map { it.id })
            assertEquals(listOf(0,1), story.slides.map { it.order })
            assertEquals(listOf(31L,42L), story.elements.map { it.id })
            val text = story.elements.first()
            assertEquals(.35f, text.x, 0f); assertEquals(.25f, text.y, 0f)
            assertEquals(0f,text.width,0f); assertEquals(0f,text.height,0f)
            assertEquals(19L,story.elements.last().targetId)
            assertEquals("fill",story.slides.first().backgroundMode)
            assertTrue(repo.saveElement(Element(0,11,"text","Nuevo")) > 100L)
            repo.delete("slides",19)
            assertNull(repo.read().elements.first { it.id == 42L }.targetId)
        } finally { repo.close(); context.deleteDatabase(name) }
    }

    @Test fun visualPropertiesCopiesOrderAndSharedTransparentFilesSurviveReopen() {
        val name="editor-${UUID.randomUUID()}.db"
        val source=File(context.cacheDir,"${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        bitmap.setPixel(1,1,0x80FF0000.toInt())
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        var repo=StoryRepository(context,name)
        var path: String?=null
        try {
            val project=repo.createProject("Editor")
            val first=repo.createSlide(project,"Uno")
            val second=repo.createSlide(project,"Dos")
            path=repo.importImage(Uri.fromFile(source)); source.delete()
            val imported=BitmapFactory.decodeFile(path)
            assertEquals(0,android.graphics.Color.alpha(imported.getPixel(0,0)))
            assertEquals(128,android.graphics.Color.alpha(imported.getPixel(1,1))); imported.recycle()
            val slide=repo.read().slides.first()
            repo.saveSlide(slide.copy(image=path,backgroundMode="manual",backgroundScale=1.8f,backgroundX=.2f,backgroundY=-.3f,backgroundLocked=true))
            val image=repo.saveElement(Element(0,first,"image","",image=path,width=.3f,height=.5f,rotation=45f,flipped=true,opacity=.4f,locked=true))
            val text=repo.saveElement(Element(0,first,"text","Hola",x=.6f,y=.4f,width=.4f,height=.2f))
            val button=repo.saveElement(Element(0,first,"button","Ir",targetId=second))
            val self=repo.saveElement(Element(0,first,"button","Origen",targetId=first))
            repo.layer(image,true)
            assertEquals(image,repo.read().elements.filter { it.slideId==first }.last().id)
            repo.layer(button,false)
            assertEquals(button,repo.read().elements.filter { it.slideId==first }.first().id)
            val lockedBefore=repo.read().elements.first { it.id==image }
            repo.moveElement(image,.9f,.9f); repo.resizeElement(image,.8f,.8f)
            assertEquals(lockedBefore,repo.read().elements.first { it.id==image })
            repo.moveElement(text,.45f,.25f); repo.resizeElement(text,.55f,.3f)
            val copyElement=repo.duplicateElement(text)
            assertEquals(.49f,repo.read().elements.first { it.id==copyElement }.x,.0001f)
            val copy=repo.duplicateSlide(first)
            assertEquals(listOf(first,copy,second),repo.read().slides.map { it.id })
            val copies=repo.read().elements.filter { it.slideId==copy }
            assertEquals(second,copies.first { it.text=="Ir" }.targetId)
            assertEquals(first,copies.first { it.text=="Origen" }.targetId)
            assertEquals(repo.read().elements.first { it.id==image }.copy(id=copies.first { it.kind=="image" }.id,slideId=copy,layer=copies.first { it.kind=="image" }.layer),copies.first { it.kind=="image" })
            repo.reorderSlide(second,-2)
            assertEquals(second,repo.read().slides.first().id)
            assertEquals(second,repo.read().elements.first { it.id==button }.targetId)
            val snapshot=repo.read(); repo.close(); repo=StoryRepository(context,name)
            assertEquals(snapshot,repo.read())
            repo.delete("slides",first)
            assertTrue(File(requireNotNull(path)).exists()) // Duplicate still refers to the resource.
            assertNull(repo.read().elements.first { it.slideId==copy && it.text=="Origen" }.targetId)
            repo.delete("slides",copy)
            assertFalse(File(requireNotNull(path)).exists())
            assertFalse(repo.read().elements.any { it.id==self })
        } finally { repo.close(); context.deleteDatabase(name); source.delete(); path?.let { File(it).delete() } }
    }

    @Test fun stalePropertyDialogsDoNotOverwriteQueuedMovesSizesLayersOrBackground() {
        val name="queued-${UUID.randomUUID()}.db"
        val repo=StoryRepository(context,name)
        try {
            val slide=repo.createSlide(repo.createProject("Queue"),"Queue")
            val id=repo.saveElement(Element(0,slide,"text","Antes",width=.5f,height=.2f))
            val before=repo.read().elements.single()
            repo.moveElement(id,.6f,.7f);repo.resizeElement(id,.7f,.3f)
            repo.toggleLock(id)
            repo.editElement(before,before.copy(text="Después"))
            val after=repo.read().elements.single()
            assertEquals("Después",after.text);assertEquals(.6f,after.x,0f);assertEquals(.7f,after.y,0f)
            assertEquals(.7f,after.width,0f);assertEquals(.3f,after.height,0f);assertTrue(after.locked)
            val oldBackground=repo.read().slides.single()
            repo.saveSlide(oldBackground.copy(backgroundX=.25f,backgroundY=-.15f))
            repo.editBackground(oldBackground,oldBackground.copy(backgroundMode="manual",backgroundScale=2f))
            val background=repo.read().slides.single()
            assertEquals(.25f,background.backgroundX,0f);assertEquals(-.15f,background.backgroundY,0f)
            assertEquals("manual",background.backgroundMode);assertEquals(2f,background.backgroundScale,0f)
        } finally { repo.close();context.deleteDatabase(name) }
    }

    @Test fun transparentWebpIsPrivateAndSurvivesProviderRemoval() {
        val name="webp-${UUID.randomUUID()}.db"
        val source=File(context.cacheDir,"${UUID.randomUUID()}.webp")
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
        bitmap.setPixel(1,1,0x8000FF00.toInt())
        @Suppress("DEPRECATION")
        val format=if(android.os.Build.VERSION.SDK_INT>=30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
        source.outputStream().use { assertTrue(bitmap.compress(format,100,it)) };bitmap.recycle()
        val repo=StoryRepository(context,name)
        var path: String?=null
        try {
            val slide=repo.createSlide(repo.createProject("WebP"),"WebP")
            path=repo.importImage(Uri.fromFile(source));source.delete()
            repo.saveElement(Element(0,slide,"image","",image=path,width=.4f,height=.4f))
            val decoded=BitmapFactory.decodeFile(path)
            assertEquals(0,android.graphics.Color.alpha(decoded.getPixel(0,0)))
            assertEquals(128,android.graphics.Color.alpha(decoded.getPixel(1,1)));decoded.recycle()
            assertEquals(path,repo.read().elements.single().image)
        } finally { repo.close();context.deleteDatabase(name);source.delete();path?.let { File(it).delete() } }
    }

    @Test fun everyBackgroundModePersists() {
        val name="background-${UUID.randomUUID()}.db"
        var repo=StoryRepository(context,name)
        try {
            val slide=repo.createSlide(repo.createProject("Fondo"),"Fondo")
            listOf("fit","fill","manual").forEach { mode ->
                repo.saveSlide(repo.read().slides.single().copy(backgroundMode=mode,backgroundScale=2f,backgroundX=.25f,backgroundY=-.15f))
                repo.close();repo=StoryRepository(context,name)
                val restored=repo.read().slides.single()
                assertEquals(slide,restored.id);assertEquals(mode,restored.backgroundMode)
                assertEquals(2f,restored.backgroundScale,0f);assertEquals(.25f,restored.backgroundX,0f)
            }
        } finally { repo.close();context.deleteDatabase(name) }
    }
}

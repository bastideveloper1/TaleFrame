package com.r0ybt.taleframe

import android.database.sqlite.SQLiteDatabase
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
class MediaPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(asset: String): File = File(context.cacheDir, "${UUID.randomUUID()}-$asset").also { file ->
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    @Test fun actualVersionTwoMigratesKeepingImagesGeometryIdsAndConnections() {
        val name = "v2-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name); file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE slides(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,color INTEGER NOT NULL DEFAULT -1,image TEXT,sort_order INTEGER NOT NULL DEFAULT 0,background_mode TEXT NOT NULL DEFAULT 'fill',background_scale REAL NOT NULL DEFAULT 1,background_x REAL NOT NULL DEFAULT 0,background_y REAL NOT NULL DEFAULT 0,background_locked INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE elements(id INTEGER PRIMARY KEY AUTOINCREMENT,slide_id INTEGER NOT NULL REFERENCES slides(id) ON DELETE CASCADE,kind TEXT NOT NULL CHECK(kind IN ('text','button','image')),text TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,text_color INTEGER NOT NULL,background_color INTEGER NOT NULL,target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL,image TEXT,width REAL NOT NULL DEFAULT 0,height REAL NOT NULL DEFAULT 0,rotation REAL NOT NULL DEFAULT 0,flipped INTEGER NOT NULL DEFAULT 0,opacity REAL NOT NULL DEFAULT 1,locked INTEGER NOT NULL DEFAULT 0,layer_order INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX slides_project ON slides(project_id)"); db.execSQL("CREATE INDEX elements_slide ON elements(slide_id)")
            db.execSQL("INSERT INTO projects VALUES(7,'Iteración 2')")
            db.execSQL("INSERT INTO slides VALUES(11,7,'A',-1,'/old/image',2,'manual',1.8,.2,-.3,1),(19,7,'B',-1,NULL,1,'fit',1,0,0,0)")
            db.execSQL("INSERT INTO elements VALUES(42,11,'button','Entrar',.6,.8,-1,-1,19,NULL,.2,.1,20,1,.4,1,3)")
            db.execSQL("UPDATE sqlite_sequence SET seq=100 WHERE name='elements'")
            db.version = 2
        }
        StoryRepository(context,name).use { repo ->
            val story = repo.read()
            assertEquals(4,repo.readableDatabase.version)
            val s = story.slides.first { it.id == 11L }
            assertEquals("/old/image",s.image); assertEquals("manual",s.backgroundMode); assertEquals(1.8f,s.backgroundScale,0f); assertTrue(s.backgroundLocked)
            assertFalse(s.autoEnabled); assertNull(s.audio)
            val e = story.elements.single()
            assertEquals(42L,e.id); assertEquals(19L,e.targetId); assertEquals(.6f,e.x,0f); assertEquals(.4f,e.opacity,0f); assertTrue(e.locked)
            assertEquals(Transition(),e.transition); assertTrue(repo.saveElement(Element(0,11,"text","Nuevo")) > 100)
            repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use { assertEquals(0,it.count) }
        }
        context.deleteDatabase(name)
    }
    @Test fun allMediaSettingsAndSharedFilesSurviveReopenCopiesAndDeletion() {
        val name="media-${UUID.randomUUID()}.db"
        var repo=StoryRepository(context,name)
        val sources=listOf(fixture("local-animation.gif"),fixture("local-video.mp4"),fixture("local-audio.wav"))
        try {
            val project=repo.createProject("Multimedia"); val first=repo.createSlide(project,"A"); val target=repo.createSlide(project,"B")
            val gif=repo.importMedia(Uri.fromFile(sources[0]),"gif")
            val audio=repo.importMedia(Uri.fromFile(sources[2]),"audio")
            repo.saveSlide(repo.read().slides.first().copy(image=gif,media=MediaOptions("gif",loop=false),audio=audio,audioLoop=false,audioVolume=.3f,autoEnabled=true,autoSeconds=.5f,autoTargetId=target,transition=Transition("left",700)))
            val video=repo.importMedia(Uri.fromFile(sources[1]),"video")
            assertEquals(video,repo.importMedia(Uri.fromFile(sources[1]),"video"))
            repo.saveElement(Element(0,first,"image","",image=video,media=MediaOptions("video",loop=false,autoplay=false,muted=false,volume=.4f),width=.3f,height=.4f,rotation=30f,opacity=.6f))
            repo.saveElement(Element(0,first,"image","",image=gif,media=MediaOptions("slideshow",loop=false,frames=listOf(gif,gif),seconds=.2f)))
            repo.saveElement(Element(0,first,"button","Entrar",targetId=target,transition=Transition("right",400)))
            val copy=repo.duplicateSlide(first)
            val snapshot=repo.read(); repo.close(); repo=StoryRepository(context,name)
            assertEquals(snapshot,repo.read())
            sources.forEach { it.delete() }
            repo.delete("slides",first)
            assertTrue(listOf(gif,video,audio).all { File(it).exists() })
            repo.delete("slides",target)
            val duplicate=repo.read().slides.single()
            assertEquals(copy,duplicate.id); assertNull(duplicate.autoTargetId)
            assertNull(repo.read().elements.first { it.kind=="button" }.targetId)
            repo.delete("slides",copy)
            assertTrue(listOf(gif,video,audio).none { File(it).exists() })
        } finally { repo.close(); context.deleteDatabase(name); sources.forEach { it.delete() }; File(context.filesDir,"backgrounds-$name").deleteRecursively() }
    }
    @Test fun reimportingDamagedSharedCopyKeepsValidatedReplacementAndOtherReferences() {
        val name="repair-${UUID.randomUUID()}.db"
        val source=fixture("local-video.mp4")
        StoryRepository(context,name).use { repo ->
            val slide=repo.createSlide(repo.createProject("Repair"),"Repair")
            val original=repo.importMedia(Uri.fromFile(source),"video")
            repo.saveElement(Element(0,slide,"image","",image=original,media=MediaOptions("video")))
            File(original).writeText("damaged")
            val replacement=repo.importMedia(Uri.fromFile(source),"video")
            assertNotEquals(original,replacement)
            assertArrayEquals(source.readBytes(),File(replacement).readBytes())
            val before=repo.read().elements.single()
            repo.editElement(before,before.copy(image=replacement,media=before.media.copy(revision=1)))
            assertFalse(File(original).exists())
            assertTrue(File(replacement).exists())
            assertEquals(1L,repo.read().elements.single().media.revision)
            repo.delete("slides",slide)
            assertFalse(File(replacement).exists())
        }
        source.delete(); context.deleteDatabase(name); File(context.filesDir,"backgrounds-$name").deleteRecursively()
    }
    @Test fun invalidFilesAndCrossProjectTemporalTargetsAreRejectedWithoutChangingStory() {
        val name="invalid-${UUID.randomUUID()}.db"
        val bad=File(context.cacheDir,"${UUID.randomUUID()}").apply { writeText("corrupt") }
        StoryRepository(context,name).use { repo ->
            val slide=repo.createSlide(repo.createProject("A"),"A")
            val foreign=repo.createSlide(repo.createProject("B"),"B")
            val before=repo.read()
            for(type in listOf("gif","video","audio")) {
                try { repo.importMedia(Uri.fromFile(bad),type); fail("Should reject $type") } catch (_: Exception) {}
            }
            try { repo.saveSlide(before.slides.first().copy(autoEnabled=true,autoTargetId=foreign)); fail("Foreign target") } catch (_: IllegalArgumentException) {}
            assertEquals(before,repo.read())
            assertEquals(0,File(context.filesDir,"backgrounds-$name").listFiles()?.size)
            assertEquals(slide,repo.read().slides.first().id)
        }
        bad.delete(); context.deleteDatabase(name); File(context.filesDir,"backgrounds-$name").deleteRecursively()
    }
}

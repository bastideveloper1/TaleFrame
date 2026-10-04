package com.r0ybt.taleframe

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
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
class LibraryPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun image(color:Int):File = File(context.cacheDir,"${UUID.randomUUID()}.png").also { file->
        Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888).also { bitmap->bitmap.eraseColor(color);file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
    }
    private fun cleanup(name:String,sources:List<File> = emptyList()) {context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively();sources.forEach {it.delete()}}
    @Test fun schemaThreeMigrationPreservesAllExistingRowsAndImportsPrivateResourcesIntoCatalog() {
        val name="v3-library-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile?.mkdirs()
        val directory=File(context.filesDir,"backgrounds-$name").apply {mkdirs()}
        val image=image(0x80FF0000.toInt()).apply {renameTo(File(directory,"old-image.png"))}
        val oldImage=File(directory,"old-image.png")
        fun asset(name:String)=File(directory,name).also {target->InstrumentationRegistry.getInstrumentation().context.assets.open(name).use {input->target.outputStream().use {input.copyTo(it)}}}
        val video=asset("local-video.mp4");val gif=asset("local-animation.gif");val audio=asset("local-audio.wav")
        val settings="""{"media":{"type":"video","loop":false,"autoplay":true,"muted":false,"volume":0.4,"revision":2},"audio":"${audio.path}","audioVolume":0.3,"audioLoop":false,"autoEnabled":true,"autoSeconds":8.0,"transition":{"type":"left","ms":700}}"""
        val elementSettings="""{"media":{"type":"slideshow","frames":["${oldImage.path}","${gif.path}"],"seconds":0.5,"loop":false},"transition":{"type":"right","ms":400}}"""
        SQLiteDatabase.openOrCreateDatabase(file,null).use {db->
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE slides(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,color INTEGER NOT NULL DEFAULT -1,image TEXT,sort_order INTEGER NOT NULL DEFAULT 0,background_mode TEXT NOT NULL DEFAULT 'fill',background_scale REAL NOT NULL DEFAULT 1,background_x REAL NOT NULL DEFAULT 0,background_y REAL NOT NULL DEFAULT 0,background_locked INTEGER NOT NULL DEFAULT 0,settings TEXT NOT NULL DEFAULT '{}',auto_target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL)")
            db.execSQL("CREATE TABLE elements(id INTEGER PRIMARY KEY AUTOINCREMENT,slide_id INTEGER NOT NULL REFERENCES slides(id) ON DELETE CASCADE,kind TEXT NOT NULL CHECK(kind IN ('text','button','image')),text TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,text_color INTEGER NOT NULL,background_color INTEGER NOT NULL,target_id INTEGER REFERENCES slides(id) ON DELETE SET NULL,image TEXT,width REAL NOT NULL DEFAULT 0,height REAL NOT NULL DEFAULT 0,rotation REAL NOT NULL DEFAULT 0,flipped INTEGER NOT NULL DEFAULT 0,opacity REAL NOT NULL DEFAULT 1,locked INTEGER NOT NULL DEFAULT 0,layer_order INTEGER NOT NULL DEFAULT 0,settings TEXT NOT NULL DEFAULT '{}')")
            db.execSQL("CREATE INDEX slides_project ON slides(project_id)");db.execSQL("CREATE INDEX elements_slide ON elements(slide_id)")
            db.execSQL("INSERT INTO projects VALUES(7,'Iteración 3')")
            db.execSQL("INSERT INTO slides VALUES(11,7,'Video',-1,?,2,'manual',1.8,.2,-.3,1,?,19),(19,7,'Destino',-1,?,1,'fit',1,0,0,0,'{}',NULL)",arrayOf(video.path,settings,gif.path))
            db.execSQL("INSERT INTO elements VALUES(42,11,'image','',.6,.8,-1,-1,NULL,?,.2,.4,20,1,.4,1,3,?),(43,11,'button','Salir',.2,.7,-1,-1,19,NULL,.3,.1,0,0,1,0,4,'{}')",arrayOf(oldImage.path,elementSettings))
            db.execSQL("UPDATE sqlite_sequence SET seq=900 WHERE name='elements'")
            db.version=3
        }
        try {StoryRepository(context,name).use {repo->
            assertEquals(6,repo.readableDatabase.version)
            val story=repo.read();val slide=story.slides.first {it.id==11L};val element=story.elements.first {it.id==42L}
            assertEquals(settings,repo.readableDatabase.rawQuery("SELECT settings FROM slides WHERE id=11",null).use {it.moveToFirst();it.getString(0)})
            assertEquals(elementSettings,repo.readableDatabase.rawQuery("SELECT settings FROM elements WHERE id=42",null).use {it.moveToFirst();it.getString(0)})
            assertEquals(listOf(19L,11L),story.slides.map {it.id});assertEquals(19L,story.elements.last().targetId)
            assertEquals(.6f,element.x,0f);assertEquals(.4f,element.opacity,0f);assertTrue(element.locked);assertEquals(20f,element.rotation,0f)
            assertEquals("video",slide.media.type);assertEquals(audio.path,slide.audio);assertEquals(19L,slide.autoTargetId);assertEquals(Transition("left",700),slide.transition)
            assertEquals(listOf(oldImage.path,gif.path),element.media.frames);assertEquals(VisualStyle(),element.style)
            assertEquals(setOf(oldImage.path,gif.path,video.path,audio.path),story.resources.map {it.path}.toSet())
            assertEquals(1,story.presets.count {it.kind=="narrator"})
            assertTrue(repo.saveElement(Element(0,11,"text","Nuevo"))>900)
            repo.delete("slides",11);repo.delete("slides",19)
            assertTrue(listOf(oldImage,gif,video,audio).all {it.exists()}) // Catalog now owns these files.
            story.resources.forEach {repo.library.deleteResource(it.id)}
            assertTrue(listOf(oldImage,gif,video,audio).none {it.exists()})
            repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertEquals(0,it.count)}
        }} finally {cleanup(name,listOf(image))}
    }
    @Test fun catalogRetainsUnusedFilesReusesReferencesAndBlocksUnsafeDeletion() {
        val name="library-${UUID.randomUUID()}.db";val source=image(0x8000FF00.toInt())
        var repo=StoryRepository(context,name)
        try {
            val project=repo.createProject("Biblioteca");val a=repo.createSlide(project,"A");val b=repo.createSlide(project,"B")
            val id=repo.library.importResource(project,Uri.fromFile(source),"Bosque","image","background")
            assertEquals(id,repo.library.importResource(project,Uri.fromFile(source),"Otra importación","image","image"))
            val path=repo.read().resources.single().path;repo.cleanImages();assertTrue(File(path).exists())
            repo.library.insertResource(a,id,"background");repo.library.insertResource(b,id)
            val usage=resourceUsage(repo.read(),repo.read().resources.single());assertEquals(1,usage.elements);assertEquals(1,usage.backgrounds)
            try {repo.library.deleteResource(id);fail("Used resource removed")} catch(_:IllegalArgumentException) {}
            repo.library.renameResource(id,"Bosque nuevo");val snapshot=repo.read();repo.close();repo=StoryRepository(context,name)
            assertEquals(snapshot,repo.read());source.delete()
            repo.delete("slides",a);repo.delete("slides",b);assertTrue(File(path).exists())
            repo.library.deleteResource(id);assertFalse(File(path).exists())
        } finally {repo.close();cleanup(name,listOf(source))}
    }
    @Test fun expressionSwapPreservesLockedGeometryLayersAndCharacterIdentityIncludingDuplicates() {
        val name="character-${UUID.randomUUID()}.db";val sources=listOf(image(-1),image(0x80FF0000.toInt()))
        StoryRepository(context,name).use {repo->try {
            val project=repo.createProject("Personajes");val slide=repo.createSlide(project,"Escena")
            val resources=sources.mapIndexed {i,f->repo.library.importResource(project,Uri.fromFile(f),"Imagen $i","image","image")}
            val character=repo.library.saveCharacter(Character(0,project,"Guillermo","Descripción",portraitId=resources.first()))
            val normal=repo.library.saveExpression(Expression(0,character,"Normal",resources[0]));val happy=repo.library.saveExpression(Expression(0,character,"Feliz",resources[1]))
            val id=repo.library.insertCharacter(slide,listOf(normal))
            val original=repo.read().elements.single().copy(x=.7f,y=.6f,width=.3f,height=.4f,rotation=45f,flipped=true,opacity=.4f,locked=true,layer=8)
            repo.saveElement(original);repo.library.changeExpression(id,happy)
            val changed=repo.read().elements.single()
            assertEquals(original.copy(image=repo.read().resources.first {it.id==resources[1]}.path,resourceId=resources[1],expressionId=happy,expressionFrames=listOf(happy),sourceName="Guillermo / Feliz",media=original.media.copy(revision=1)),changed)
            val copy=repo.duplicateElement(id);assertEquals(character,repo.read().elements.first {it.id==copy}.characterId)
            val sequence=repo.library.insertCharacter(slide,listOf(normal,happy))
            assertEquals(listOf(normal,happy),repo.read().elements.first {it.id==sequence}.expressionFrames)
            assertEquals("slideshow",repo.read().elements.first {it.id==sequence}.media.type)
            repo.library.reorderExpression(happy,-1);assertEquals(happy,repo.read().expressions.first().id)
            repo.library.deleteExpression(happy);assertNull(repo.read().elements.first {it.id==id}.expressionId)
            assertEquals(changed.image,repo.read().elements.first {it.id==id}.image)
            repo.library.deleteCharacter(character);assertNull(repo.read().elements.first {it.id==id}.characterId)
            assertEquals(changed.style,repo.read().elements.first {it.id==id}.style)
            repo.delete("projects",project);assertTrue(repo.read().resources.isEmpty());assertTrue(repo.read().presets.isEmpty())
        } finally {repo.close();cleanup(name,sources)}}
    }
    @Test fun updatingPresetOrLibraryResourceDoesNotSilentlyChangeExistingInstances() {
        val name="snapshots-${UUID.randomUUID()}.db";val sources=listOf(image(-1),image(0xFF123456.toInt()))
        var repo=StoryRepository(context,name)
        try {
            val project=repo.createProject("Estilos");val slide=repo.createSlide(project,"A")
            val character=repo.library.saveCharacter(Character(0,project,"Elena"))
            val preset=repo.read().presets.first {it.id==repo.read().characters.single().dialogPresetId}
            val first=repo.library.insertDialog(slide,character)
            val before=repo.read().elements.single()
            repo.editElement(before,before.copy(backgroundColor=0xFFFF0000.toInt(),style=before.style.copy(shape="oval")))
            assertEquals(preset,repo.read().presets.first {it.id==preset.id})
            val editedInstance=repo.read().elements.single()
            repo.library.savePreset(preset.copy(backgroundColor=0xFF00FF00.toInt(),style=preset.style.copy(shape="circle",font="serif",textScale=1.3f,alignment="center",backgroundOpacity=.5f,borderWidth=2f)))
            assertEquals(editedInstance,repo.read().elements.first {it.id==first})
            val second=repo.library.insertDialog(slide,character)
            val newer=repo.read().elements.first {it.id==second};assertEquals("circle",newer.style.shape);assertEquals("Elena",newer.speakerName)
            val narrator=repo.read().presets.single {it.kind=="narrator"};val narration=repo.library.insertDialog(slide,presetId=narrator.id)
            assertNull(repo.read().elements.first {it.id==narration}.characterId)
            assertEquals(narrator.style,repo.read().elements.first {it.id==narration}.style)
            val resource=repo.library.importResource(project,Uri.fromFile(sources[0]),"Original","image","image")
            val image=repo.library.insertResource(slide,resource)!!;val oldPath=repo.read().elements.first {it.id==image}.image
            repo.library.replaceResource(resource,Uri.fromFile(sources[1]));val newPath=repo.read().resources.single().path
            assertNotEquals(oldPath,newPath);assertEquals(oldPath,repo.read().elements.first {it.id==image}.image)
            assertTrue(File(requireNotNull(oldPath)).exists());assertTrue(File(newPath).exists())
            val snapshot=repo.read();repo.close();repo=StoryRepository(context,name);assertEquals(snapshot,repo.read())
            try {repo.library.deletePreset(preset.id);fail("Character preset removed")} catch(_:IllegalArgumentException) {}
        } finally {repo.close();cleanup(name,sources)}
    }
    @Test fun projectsCanSharePhysicalBytesWithoutSharingTheirCatalogOrDeletionLifetime() {
        val name="shared-projects-${UUID.randomUUID()}.db";val source=image(-1)
        StoryRepository(context,name).use {repo->try {
            val a=repo.createProject("A");val b=repo.createProject("B")
            val ra=repo.library.importResource(a,Uri.fromFile(source),"A image","image","image")
            val rb=repo.library.importResource(b,Uri.fromFile(source),"B image","image","image")
            assertNotEquals(ra,rb)
            val resources=repo.read().resources
            assertEquals(resources[0].path,resources[1].path)
            val path=resources[0].path
            repo.delete("projects",a);assertTrue(File(path).exists())
            assertEquals(rb,repo.read().resources.single().id)
            repo.library.deleteResource(rb);assertFalse(File(path).exists())
        } finally {repo.close();cleanup(name,listOf(source))}}
    }
    @Test fun crossProjectResourceCharacterAndPresetReferencesAreRejected() {
        val name="isolated-${UUID.randomUUID()}.db";val source=image(-1)
        StoryRepository(context,name).use {repo->try {
            val a=repo.createProject("A");val b=repo.createProject("B");val slide=repo.createSlide(a,"A")
            val resource=repo.library.importResource(b,Uri.fromFile(source),"Foreign","image","image")
            val character=repo.library.saveCharacter(Character(0,a,"A"))
            try {repo.library.insertResource(slide,resource);fail("Foreign resource")} catch(_:IllegalArgumentException) {}
            try {repo.library.saveExpression(Expression(0,character,"Foreign",resource));fail("Foreign expression")} catch(_:IllegalArgumentException) {}
            val foreignPreset=repo.read().presets.first {it.projectId==b}
            try {repo.saveElement(Element(0,slide,"text","Foreign",presetId=foreignPreset.id));fail("Foreign preset")} catch(_:IllegalArgumentException) {}
            assertTrue(repo.read().elements.isEmpty());assertTrue(repo.read().expressions.isEmpty())
        } finally {repo.close();cleanup(name,listOf(source))}}
    }
}

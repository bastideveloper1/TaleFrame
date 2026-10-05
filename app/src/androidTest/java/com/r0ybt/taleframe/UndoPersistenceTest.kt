package com.r0ybt.taleframe

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class UndoPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test:(StoryRepository,ElementUndoHistory,Long,String)->Unit) {
        val name="undo-${UUID.randomUUID()}.db"
        val repo=StoryRepository(context,name)
        val history=ElementUndoHistory(repo)
        try {
            val slide=repo.createSlide(repo.createProject("Undo"),"A")
            test(repo,history,slide,name)
        } finally {history.beginSession(null);repo.close();context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively()}
    }
    private fun gif(repo:StoryRepository):String {
        val file=File(context.cacheDir,"${UUID.randomUUID()}.gif")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("transition-animation.gif").use {input->file.outputStream().use {input.copyTo(it)}}
            return repo.importMedia(Uri.fromFile(file),"gif")
        } finally {file.delete()}
    }

    @Test fun movementAndResizeUndoRestoreOutsideCoordinatesAndPersistAcrossReopen()=fixture {repo,history,slide,name->
        val id=repo.saveElement(Element(0,slide,"image","",image=gif(repo),media=MediaOptions("gif"),freePosition=true,
            x=-.6f,y=1.2f,width=2.5f,height=3f,rotation=18f,opacity=.7f,flipped=true))
        val before=repo.snapshotElements(slide)
        history.beginSession(slide)
        history.edit {moveElement(id,-1.25f,2.5f,true)}
        assertEquals(1,history.count);assertTrue(history.undo());assertEquals(before,repo.snapshotElements(slide))
        history.edit {resizeElement(id,4f,5f,-.6f,1.2f,true)}
        assertEquals(1,history.count);assertTrue(history.undo());assertEquals(before,repo.snapshotElements(slide))
        val restored=repo.read()
        StoryRepository(context,name).use {reopened->
            assertEquals(restored,reopened.read())
            assertEquals(0,ElementUndoHistory(reopened).count)
        }
        assertFalse(history.undo())
    }

    @Test fun textPropertiesDuplicateLayersAndLockEachUndoExactlyOneConfirmedAction()=fixture {repo,history,slide,_->
        val a=repo.saveElement(Element(0,slide,"text","Anterior",width=.5f,height=.2f))
        val b=repo.saveElement(Element(0,slide,"button","Destino",targetId=slide,width=.3f,height=.12f))
        history.beginSession(slide)
        fun check(action:StoryRepository.()->Unit) {
            val before=repo.snapshotElements(slide)
            history.edit(action);assertEquals(1,history.count)
            assertNotEquals(before,repo.snapshotElements(slide))
            assertTrue(history.undo());assertEquals(before,repo.snapshotElements(slide));assertEquals(0,history.count)
        }
        check {val before=read().elements.first {it.id==a};editElement(before,before.copy(text="Texto confirmado",backgroundColor=-65536,style=VisualStyle(bold=true)))}
        check {duplicateElement(a)}
        check {layer(a,true)}
        check {layer(b,false)}
        check {toggleLock(a)}
        repo.toggleLock(a)
        check {toggleLock(a)}
        repo.toggleLock(a)
        val original=repo.snapshotElements(slide)
        history.edit {moveElement(a,.4f,.5f)}
        val moved=repo.snapshotElements(slide)
        history.edit {val before=read().elements.first {it.id==a};editElement(before,before.copy(text="Último"))}
        assertEquals(2,history.count)
        assertTrue(history.undo());assertEquals(moved,repo.snapshotElements(slide))
        assertTrue(history.undo());assertEquals(original,repo.snapshotElements(slide))
    }

    @Test fun deletionRestoresExactIdRawSettingsLayersAndLastMultimediaReference()=fixture {repo,history,slide,_->
        val path=gif(repo)
        val id=repo.saveElement(Element(0,slide,"image","",image=path,media=MediaOptions("gif",loop=false,revision=12),
            freePosition=true,x=-1.1f,y=-.3f,width=2.4f,height=1.8f,opacity=.6f,rotation=35f,flipped=true,locked=true,
            sourceName="GIF original",style=VisualStyle(borderWidth=3f),transition=Transition("left",700)))
        repo.saveElement(Element(0,slide,"button","Otro",targetId=slide))
        // Preserve settings beyond the current typed model, not an approximate reconstructed copy.
        val settings=org.json.JSONObject(repo.read().elements.first {it.id==id}.settings()).put("legacyExtra","Conservar").toString()
        repo.writableDatabase.execSQL("UPDATE elements SET settings=?,layer_order=7 WHERE id=?",arrayOf<Any>(settings,id))
        val before=repo.snapshotElements(slide)
        val bytes=File(path).readBytes()
        history.beginSession(slide);history.edit {delete("elements",id)}
        assertTrue(repo.read().elements.none {it.id==id});assertTrue(File(path).exists());assertEquals(1,history.count)
        assertTrue(history.undo());assertEquals(before,repo.snapshotElements(slide));assertArrayEquals(bytes,File(path).readBytes())
        repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertEquals(0,it.count)}
    }

    @Test fun boundedHistoryNoOpsSessionChangesAndFailedEditKeepConsistentState()=fixture {repo,history,slide,_->
        val id=repo.saveElement(Element(0,slide,"text","A"))
        history.beginSession(slide)
        history.edit {};assertEquals(0,history.count)
        for(i in 1..32) history.edit {moveElement(id,i/100f,i/100f)}
        assertEquals(30,history.count)
        repeat(30) {assertTrue(history.undo())}
        assertEquals(.02f,repo.read().elements.single().x,.0001f)
        assertFalse(history.undo())
        history.edit {moveElement(id,.02f,.02f)};assertEquals(0,history.count)
        try {history.edit {throw IllegalArgumentException("Prueba")};fail("Expected failure")} catch(_:IllegalArgumentException) {}
        assertEquals(0,history.count)
        history.edit {moveElement(id,.6f,.7f)}
        history.beginSession(slide);assertEquals(1,history.count) // Same live session/configuration.
        val second=repo.createSlide(repo.createProject("Otro"),"B")
        history.beginSession(second);assertEquals(0,history.count);assertFalse(history.undo())
        history.beginSession(slide);assertEquals(0,history.count)
        history.edit {moveElement(id,.7f,.8f)}
        history.beginSession(null);assertEquals(0,history.count);assertNull(history.slideId)
    }

    @Test fun evictedAndClearedHistoryReleaseOnlyUnreferencedMedia()=fixture {repo,_,slide,_->
        val history=ElementUndoHistory(repo,1)
        val path=gif(repo)
        val id=repo.saveElement(Element(0,slide,"image","",image=path,media=MediaOptions("gif"),width=2f,height=2f))
        val text=repo.saveElement(Element(0,slide,"text","A"))
        history.beginSession(slide);history.edit {delete("elements",id)}
        history.edit {};assertTrue(File(path).exists());assertEquals(1,history.count)
        history.edit {val before=read().elements.first {it.id==text};editElement(before,before.copy(text="B"))}
        assertEquals(1,history.count);assertFalse(File(path).exists())
        history.beginSession(null)
        val another=gif(repo)
        val other=repo.saveElement(Element(0,slide,"image","",image=another,media=MediaOptions("gif")))
        history.beginSession(slide);history.edit {delete("elements",other)};assertTrue(File(another).exists())
        history.beginSession(null);assertFalse(File(another).exists())
        assertEquals("B",repo.read().elements.single().text)
    }

    @Test fun unrelatedLibraryAdditionsKeepHistoryButStructuralEditsInvalidateIt()=fixture {repo,history,slide,_->
        val id=repo.saveElement(Element(0,slide,"text","A"))
        val project=repo.read().slides.single().projectId
        history.beginSession(slide);history.edit {moveElement(id,.5f,.5f)}
        history.edit {library.savePreset(Preset(0,project,"Nuevo"))};assertEquals(1,history.count)
        history.edit {delete("slides",slide)};assertEquals(0,history.count);assertNull(history.slideId);assertFalse(history.undo())
    }
}

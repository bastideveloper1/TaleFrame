package com.r0ybt.taleframe

import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BaseNavigationPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test:(StoryRepository,Long,List<Long>,String)->Unit) {
        val name="base-size-${UUID.randomUUID()}.db"
        StoryRepository(context,name).use {repo->
            try {
                val p=repo.createProject("Base")
                val slides=listOf("A","B","C").map {repo.createSlide(p,it)}
                test(repo,p,slides,name)
            } finally {context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively()}
        }
    }
    private fun pair(repo:StoryRepository,slide:Long)=repo.read().elements.filter {it.slideId==slide && it.baseNavigation!=null}
    @Test fun newControlsHaveDiscreteDefaultsEqualSizesAndIndependentEditableLabels()=fixture {repo,p,slides,name->
        repo.setAutomaticBaseNavigation(p,true)
        assertEquals(4,repo.read().elements.count {it.baseNavigation!=null})
        val buttons=pair(repo,slides[1]);assertEquals(2,buttons.size)
        for(e in buttons) {
            assertEquals(.24f,e.width,0f);assertEquals(.07f,e.height,0f)
            assertEquals(-1,e.backgroundColor);assertEquals(-16777216,e.textColor)
            assertEquals(.75f,e.style.backgroundOpacity,0f);assertEquals("center",e.style.alignment)
            assertEquals("none",e.style.buttonEffect);assertEquals(0f,e.style.borderWidth,0f)
            repo.editElement(e,e.copy(text=if(e.baseNavigation=="previous") "Volver" else "Continuar"))
        }
        val before=pair(repo,slides[1]);repo.syncBaseNavigation(p);assertEquals(before,pair(repo,slides[1]))
        assertEquals(setOf("Volver","Continuar"),before.map {it.text}.toSet())
        assertEquals(slides[0],before.single {it.baseNavigation=="previous"}.targetId)
        assertEquals(slides[2],before.single {it.baseNavigation=="next"}.targetId)
        StoryRepository(context,name).use {assertEquals(repo.read(),it.read())}
    }
    @Test fun gestureAndPropertiesResizeOnlyTheOppositeRoleAndUndoRestoresBoth()=fixture {repo,p,slides,_->
        repo.setAutomaticBaseNavigation(p,true)
        val buttons=pair(repo,slides[1]);val previous=buttons.single {it.baseNavigation=="previous"};val next=buttons.single {it.baseNavigation=="next"}
        val independent=repo.saveElement(Element(0,slides[1],"button","Independiente",targetId=slides[2],width=.43f,height=.13f))
        val duplicate=repo.duplicateElement(previous.id)
        val outsiders=repo.read().elements.filter {it.slideId!=slides[1] || it.id in listOf(independent,duplicate)}
        repo.toggleLock(next.id)
        val history=ElementUndoHistory(repo);history.beginSession(slides[1])
        try {
            val before=repo.snapshotElements(slides[1])
            history.edit {resizeElement(previous.id,.3f,.1f,.2f,.8f)}
            val sized=pair(repo,slides[1]);assertTrue(sized.all {it.width==.3f && it.height==.1f})
            assertTrue(sized.single {it.id==next.id}.locked)
            assertEquals(outsiders,repo.read().elements.filter {it.slideId!=slides[1] || it.id in listOf(independent,duplicate)})
            assertEquals(1,history.count);assertTrue(history.undo());assertEquals(before,repo.snapshotElements(slides[1]))
            history.edit {val e=read().elements.first {it.id==next.id};editElement(e,e.copy(locked=false,width=.35f,height=.11f,text="Continuar"))}
            assertTrue(pair(repo,slides[1]).all {it.width==.35f && it.height==.11f})
            assertEquals(previous.text,pair(repo,slides[1]).single {it.id==previous.id}.text)
            assertEquals(1,history.count);assertTrue(history.undo());assertEquals(before,repo.snapshotElements(slides[1]))
            history.edit {resizeElement(next.id,.8f,.2f)} // A locked source cannot resize either member.
            assertEquals(0,history.count);assertEquals(before,repo.snapshotElements(slides[1]))
        } finally {history.beginSession(null)}
    }
    @Test fun legacyButtonsKeepAppearanceAndRegeneratedCounterpartInheritsChosenSize()=fixture {repo,p,slides,name->
        val old=repo.saveElement(Element(0,slides[1],"button","Viejo anterior",targetId=slides[0],baseNavigation="previous",
            width=.32f,height=.085f,textColor=-1,backgroundColor=0xFF8E435F.toInt(),style=VisualStyle(shape="rounded")))
        val next=repo.saveElement(Element(0,slides[1],"button","Viejo siguiente",targetId=slides[2],baseNavigation="next",
            width=.32f,height=.085f,textColor=-1,backgroundColor=0xFF8E435F.toInt(),style=VisualStyle(shape="rounded")))
        val original=pair(repo,slides[1]);repo.setAutomaticBaseNavigation(p,true);assertEquals(original,pair(repo,slides[1]))
        StoryRepository(context,name).use {assertEquals(original,pair(it,slides[1]))}
        repo.resizeElement(old,.4f,.1f)
        assertEquals(original.single {it.id==next}.copy(width=.4f,height=.1f),pair(repo,slides[1]).single {it.id==next})
        val kept=pair(repo,slides[1]).single {it.id==old}
        repo.delete("elements",next);repo.syncBaseNavigation(p)
        assertEquals(kept,pair(repo,slides[1]).single {it.id==old})
        val fresh=pair(repo,slides[1]).single {it.baseNavigation=="next"}
        assertEquals(.4f,fresh.width,0f);assertEquals(.1f,fresh.height,0f);assertEquals(-1,fresh.backgroundColor)
        assertEquals(slides[2],fresh.targetId)
    }
    @Test fun reopeningKeepsCustomTextSizeAndNavigationIncludingDisabledAutomaticMode()=fixture {repo,p,slides,name->
        repo.setAutomaticBaseNavigation(p,true)
        val previous=pair(repo,slides[1]).single {it.baseNavigation=="previous"}
        val next=pair(repo,slides[1]).single {it.baseNavigation=="next"}
        repo.editElement(previous,previous.copy(text="Volver",width=.28f,height=.09f))
        repo.editElement(next,next.copy(text="Continuar")) // Stale text-only dialog must preserve synchronized geometry.
        assertTrue(pair(repo,slides[1]).all {it.width==.28f && it.height==.09f})
        repo.setAutomaticBaseNavigation(p,false)
        repo.resizeElement(next.id,.27f,.08f)
        assertTrue(pair(repo,slides[1]).all {it.width==.27f && it.height==.08f})
        val expected=repo.read()
        StoryRepository(context,name).use {assertEquals(expected,it.read())}
        assertEquals(slides[0],pair(repo,slides[1]).single {it.id==previous.id}.targetId)
        assertEquals(slides[2],pair(repo,slides[1]).single {it.id==next.id}.targetId)
        assertEquals(setOf("Volver","Continuar"),pair(repo,slides[1]).map {it.text}.toSet())
    }
}

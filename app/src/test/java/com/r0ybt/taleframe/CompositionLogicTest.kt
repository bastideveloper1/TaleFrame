package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class CompositionLogicTest {
    @Test fun initialAndDraftPolicyPreservesLegacyEntryAndNeverInventsRoutes() {
        val slides=listOf(Slide(9,1,"Primera visual"),Slide(3,1,"Primera creada",draft=true),Slide(12,2,"Otro proyecto"))
        assertEquals(3L,initialSlide(Project(1,"P"),slides)?.id)
        assertEquals(9L,initialSlide(Project(1,"P",initialSlideId=9),slides)?.id)
        assertEquals(9L,playbackStart(Project(1,"P",skipDrafts=true),slides)?.id)
        assertNull(playbackStart(Project(1,"P",skipDrafts=true),slides.filter {it.id==3L}))
        assertFalse(destinationAllowed(3,slides,true));assertTrue(destinationAllowed(3,slides,false));assertFalse(destinationAllowed(99,slides,false))
    }
    @Test fun referencesIncludeCoversAndTemplateFramesEvenAfterOriginalSceneWasRemoved() {
        val resource=Resource(1,1,"A","image","image","/a")
        val template=SlideTemplate(7,1,"Conversación",Slide(0,1,"T",image="/a"),listOf(Element(0,0,"image","",image="/a",resourceId=1)))
        val story=Story(projects=listOf(Project(1,"P",coverResourceId=1)),resources=listOf(resource),templates=listOf(template))
        val usage=resourceUsage(story,resource)
        assertEquals(1,usage.covers);assertEquals(1,usage.templates);assertEquals(2,usage.total)
    }
    @Test fun includedPanelsStayIndependentWithinCanvasAndContainNoNavigation() {
        val list=includedTemplates();assertEquals(6,list.size)
        list.forEach {t->assertTrue(t.included);assertTrue(t.id<0);t.elements.forEach {e->assertNull(e.targetId);assertTrue(e.width in .05f..1f);assertTrue(e.height in .04f..1f);assertTrue(e.x in 0f..1f);assertTrue(e.y in 0f..1f)}}
        assertEquals(4,list.first {it.name=="4 paneles"}.elements.count {it.panel!=null})
    }
}

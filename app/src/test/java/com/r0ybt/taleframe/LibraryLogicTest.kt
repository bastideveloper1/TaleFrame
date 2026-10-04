package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class LibraryLogicTest {
    @Test fun referenceIndexCountsEachInstanceOnceAcrossSlideshowPathsAndMetadata() {
        val resources=listOf(Resource(1,1,"A","image","image","/a"),Resource(2,1,"B","image","image","/b"),Resource(3,2,"Other","image","image","/a"))
        val story=Story(slides=listOf(Slide(1,1,"A",image="/a",backgroundResourceId=1),Slide(2,2,"Other")),
            elements=listOf(Element(1,1,"image","",image="/a",resourceId=1,media=MediaOptions("slideshow",frames=listOf("/a","/a","/b"))),Element(2,2,"image","",image="/a",resourceId=3)),
            resources=resources,characters=listOf(Character(1,1,"Guillermo",portraitId=1)),expressions=listOf(Expression(1,1,"Normal",1)))
        val counts=resourceUsageIndex(story,1)
        assertEquals(ResourceUsage(elements=1,backgrounds=1,expressions=1,portraits=1),counts[1])
        assertEquals(ResourceUsage(elements=1),counts[2]);assertFalse(counts.containsKey(3))
    }
    @Test fun presetsCreateIndependentSnapshotsWithoutNavigationDestinations() {
        val preset=Preset(9,1,"Peligro","button",textColor=-1,backgroundColor=0xFF990000.toInt(),style=VisualStyle(shape="oval"),transition=Transition("right",700))
        val instance=styledElement(11,preset)
        val other=instance.copy(backgroundColor=-1,style=instance.style.copy(shape="rectangle"))
        assertEquals("oval",preset.style.shape);assertEquals("oval",instance.style.shape)
        assertEquals("rectangle",other.style.shape);assertNull(instance.targetId)
        assertEquals(9L,instance.presetId);assertEquals(Transition("right",700),instance.transition)
    }
    @Test fun characterDialogStoresNameAndGenericDialogHasNoCharacterIdentity() {
        val p=Preset(1,1,"Guillermo",style=VisualStyle(showName=true))
        val dialog=styledElement(2,p,"Guillermo",7)
        assertEquals("Guillermo",dialog.speakerName);assertEquals(7L,dialog.characterId);assertTrue(dialog.style.showName)
        val generic=styledElement(2,null)
        assertNull(generic.characterId);assertFalse(generic.style.showName)
    }
}

package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.normalizedCharacterName
import com.r0ybt.taleframe.ui.toolOrder
import org.junit.Assert.*
import org.junit.Test

class DogfoodingLogicTest {
    @Test fun characterNormalizationIgnoresCaseAndTrivialWhitespace() {
        assertEquals(normalizedCharacterName(" Guillermo \t Pérez "),normalizedCharacterName("guillermo pérez"))
        assertNotEquals(normalizedCharacterName("Guillermo"),normalizedCharacterName("Guillermina"))
    }
    @Test fun savedToolbarOrderDropsUnknownAndDuplicateKeysAndAddsNewTools() {
        assertEquals(listOf("image","dialog","text","panel"),toolOrder("image|obsolete|dialog|image",listOf("text","dialog","image","panel")))
        assertEquals(listOf("text","image"),toolOrder(null,listOf("text","image")))
    }
}

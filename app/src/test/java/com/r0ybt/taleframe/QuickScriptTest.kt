package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class QuickScriptTest {
    @Test fun preservesEveryCharacterIncludingEmptyParagraphsAndMixedLineBreaks() {
        listOf("", "\n", "\n\n", "  A 📝\r\n\r\nB\rC\n", "sin saltos", "A\n\n").forEach { source ->
            val paragraphs = scriptParagraphs(7, source)
            assertEquals(source, paragraphs.joinToString("") { it.text })
            paragraphs.forEach { assertEquals(it.text, source.substring(it.start, it.end)) }
            assertTrue(paragraphs.zipWithNext().all { (a,b) -> a.end == b.start })
        }
        assertEquals(listOf("A\n", "\n"), scriptParagraphs(1, "A\n\n").map { it.text })
    }

    @Test fun assignedMiddleDisappearsAndUndoRestoresItsOriginalPosition() {
        val original = "Antes\n\nMedio\r\nDespués"
        val paragraphs = scriptParagraphs(1, original)
        val assignment = ScriptAssignment(3, 1, 8, paragraphs[1].start, paragraphs[2].end, false)
        val state = QuickScriptState(1, drafts = listOf(ScriptDraft(1, original)), assignments = listOf(assignment))
        assertEquals(listOf(paragraphs[0], paragraphs[3]), state.pendingParagraphs())
        assertEquals(listOf(paragraphs[3]), scriptSelection(state.pendingParagraphs(), paragraphs[0], paragraphs[3]))
        assertEquals(paragraphs, state.copy(assignments = emptyList()).pendingParagraphs())
        assertEquals(original, state.copy(assignments = emptyList()).pendingParagraphs().joinToString("") { it.text })
    }

    @Test fun selectsConsecutiveParagraphsInEitherDirectionButNeverAcrossBatches() {
        val first = scriptParagraphs(1, "A\n\nB\nC")
        val second = scriptParagraphs(2, "Otro lote")
        assertEquals(first.take(3), scriptSelection(first, first[0], first[2]))
        assertEquals(first.take(3), scriptSelection(first, first[2], first[0]))
        assertEquals(second, scriptSelection(first + second, first[0], second[0]))
    }

    @Test fun largePasteHasNoSmallTextOrParagraphLimit() {
        val original = "texto extenso ñ 📝\r\n\n".repeat(100000)
        val paragraphs = scriptParagraphs(1, original)
        assertEquals(200000, paragraphs.size)
        assertEquals(original, paragraphs.joinToString("") { it.text })
        val assignment = ScriptAssignment(1, 1, 1, 0, paragraphs[999].end, false)
        val pending = QuickScriptState(1, drafts = listOf(ScriptDraft(1, original)), assignments = listOf(assignment)).pendingParagraphs()
        assertEquals(original.substring(assignment.end), pending.joinToString("") { it.text })
    }
    @Test fun preciseSelectionWithinParagraphPreservesPrefixSuffixAndWhitespace() {
        val original = "  Antes, una frase exacta 📝; después.  \r\n"
        val start = original.indexOf("una")
        val end = original.indexOf("; después")
        val assignment = ScriptAssignment(1, 7, 10, start, end, false)
        val state = QuickScriptState(1, drafts = listOf(ScriptDraft(7, original)), assignments = listOf(assignment))
        assertEquals("una frase exacta 📝", original.substring(start, end))
        assertEquals(listOf("  Antes, ", "; después.  \r\n"), state.pendingFragments().map { it.text })
        assertEquals(original.substring(0, start) + original.substring(end), state.pendingParagraphs().joinToString("") { it.text })
        assertFalse(state.isPendingRange(7, start, end))
        assertFalse(state.isPendingRange(7, 0, original.length))
        assertTrue(state.isPendingRange(7, 0, start))
        assertTrue(state.isPendingRange(7, end, original.length))
        assertEquals(original, state.copy(assignments = emptyList()).pendingFragments().single().text)
    }

    @Test fun preciseSelectionAcrossParagraphsPreservesMixedSeparatorsAndBlankLines() {
        val original = "  Primer párrafo\r\n\r\n  Segundo ñ 📝\n\nTercero  "
        val start = original.indexOf("párrafo")
        val end = original.indexOf(" ñ")
        val state = QuickScriptState(1, drafts = listOf(ScriptDraft(2, original)))
        assertTrue(state.isPendingRange(2, start, end))
        assertEquals("párrafo\r\n\r\n  Segundo", original.substring(start, end))
        val assigned = state.copy(assignments = listOf(ScriptAssignment(1, 2, 3, start, end, false)))
        assertEquals("  Primer ", assigned.pendingFragments().first().text)
        assertEquals(" ñ 📝\n\nTercero  ", assigned.pendingFragments().last().text)
        assertEquals(original, assigned.copy(assignments = emptyList()).pendingParagraphs().joinToString("") { it.text })
    }

    @Test fun preciseRangesRejectEmptyOutOfBoundsAndSplitSurrogatePairs() {
        val text = "ñ 📝 é\n"
        assertTrue(validScriptRange(text, 2, 4))
        assertEquals("📝", text.substring(2, 4))
        assertFalse(validScriptRange(text, 2, 3))
        assertFalse(validScriptRange(text, 3, 4))
        assertFalse(validScriptRange(text, 4, 4))
        assertFalse(validScriptRange(text, -1, 4))
        assertFalse(validScriptRange(text, 0, text.length + 1))
        assertFalse(validScriptRange(text, 4, 2))
        val state = QuickScriptState(1, drafts = listOf(ScriptDraft(2, text)))
        assertFalse(state.isPendingRange(99, 0, 1))
    }

    @Test fun successivePartialAssignmentsAndUndoRestoreExactOriginalPositions() {
        val original = "  uno dos tres  \n\nfin"
        val first = ScriptAssignment(1, 2, 3, 6, 9, false)
        val second = ScriptAssignment(2, 2, 4, 10, 14, false)
        val state = QuickScriptState(1, drafts = listOf(ScriptDraft(2, original)), assignments = listOf(first, second))
        assertEquals(listOf("  uno ", " ", "  \n\nfin"), state.pendingFragments().map { it.text })
        val afterUndo = state.copy(assignments = listOf(first))
        assertEquals(listOf("  uno ", " tres  \n\nfin"), afterUndo.pendingFragments().map { it.text })
        assertTrue(afterUndo.isPendingRange(2, second.start, second.end))
        assertFalse(afterUndo.isPendingRange(2, first.start, first.end))
        assertEquals(original, afterUndo.copy(assignments = emptyList()).pendingFragments().single().text)
        val paragraphs = afterUndo.pendingParagraphs()
        assertEquals(listOf(paragraphs[1]), scriptSelection(paragraphs, paragraphs[0], paragraphs[1]))
        assertEquals(paragraphs.drop(1), scriptSelection(paragraphs, paragraphs[1], paragraphs.last()))
    }

}

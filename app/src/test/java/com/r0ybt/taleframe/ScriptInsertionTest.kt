package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test

class ScriptInsertionTest {
    private val original = listOf(70L to 0, 20L to 1, 90L to 2)
    private fun insert(rows: List<Pair<Long, Int>>, id: Long, point: ScriptInsertionPoint): List<Pair<Long, Int>> {
        val changes = scriptInsertionOrders(rows, scriptInsertionIndex(rows.map { it.first }, point), id).toMap()
        return (rows.map { it.first to (changes[it.first] ?: it.second) } + (id to changes.getValue(id)))
            .sortedWith(compareBy<Pair<Long, Int>> { it.second }.thenBy { it.first })
    }
    @Test fun insertsAtBeginningMiddleAndEndPreservingExistingIdsAndRelativeOrder() {
        val cases = listOf(
            ScriptInsertionPoint(ScriptInsertionMode.START) to listOf(100L, 70L, 20L, 90L),
            ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = 70) to listOf(70L, 100L, 20L, 90L),
            ScriptInsertionPoint(ScriptInsertionMode.BETWEEN, afterId = 20, beforeId = 90) to listOf(70L, 20L, 100L, 90L),
            ScriptInsertionPoint() to listOf(70L, 20L, 90L, 100L)
        )
        cases.forEach { (point, expected) ->
            val after = insert(original, 100, point)
            assertEquals(expected, after.map { it.first })
            assertEquals(original.map { it.first }, after.filter { it.first != 100L }.map { it.first })
        }
        assertEquals(listOf(100L to 0), insert(emptyList(), 100, ScriptInsertionPoint(ScriptInsertionMode.START)))
    }
    @Test fun continuingAfterLastCreatedSlideNeverReversesNarrativeOrder() {
        val first = insert(original, 100, ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = 70))
        val second = insert(first, 101, ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = 100))
        val third = insert(second, 102, ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = 101))
        assertEquals(listOf(70L, 100L, 101L, 102L, 20L, 90L), third.map { it.first })
        val changedLocation = insert(third, 103, ScriptInsertionPoint(ScriptInsertionMode.START))
        assertEquals(listOf(103L, 70L, 100L, 101L, 102L, 20L, 90L), changedLocation.map { it.first })
    }
    @Test fun handlesSparseOrdersAndTiesWithoutMovingThePrefix() {
        val sparse = listOf(5L to 3, 7L to 10, 9L to 10, 12L to 40)
        val result = insert(sparse, 100, ScriptInsertionPoint(ScriptInsertionMode.BETWEEN, afterId = 7, beforeId = 9))
        assertEquals(listOf(5L, 7L, 100L, 9L, 12L), result.map { it.first })
        assertEquals(sparse.take(2), result.take(2))
        val deletedOrder = result.first { it.first == 100L }.second
        val undone = result.filter { it.first != 100L }.map { if (it.second > deletedOrder) it.first to it.second - 1 else it }
        assertEquals(sparse, undone)
    }
    @Test fun rejectsMissingAnchorsAndPairsThatAreNoLongerConsecutive() {
        listOf(ScriptInsertionPoint(ScriptInsertionMode.AFTER, afterId = 999),
            ScriptInsertionPoint(ScriptInsertionMode.BETWEEN, afterId = 70, beforeId = 90),
            ScriptInsertionPoint(ScriptInsertionMode.BETWEEN, afterId = 90, beforeId = null)).forEach { point ->
            try { scriptInsertionIndex(original.map { it.first }, point); fail("Invalid location accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}

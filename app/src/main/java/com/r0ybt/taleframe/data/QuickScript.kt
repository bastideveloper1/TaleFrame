package com.r0ybt.taleframe.data

/** Offsets are UTF-16 positions in immutable source text, including the original line separators. */
data class ScriptParagraph(val draftId: Long, val start: Int, val end: Int, val text: String)
data class ScriptAssignment(val id: Long, val draftId: Long, val slideId: Long?, val start: Int, val end: Int, val dirty: Boolean)
data class ScriptDraft(val id: Long, val original: String)
data class ScriptFragment(val draftId: Long, val start: Int, val end: Int, val text: String)
data class QuickScriptState(
    val projectId: Long,
    val input: String = "",
    val drafts: List<ScriptDraft> = emptyList(),
    val assignments: List<ScriptAssignment> = emptyList()
) {
    fun pendingFragments(): List<ScriptFragment> {
        val byDraft = assignments.groupBy { it.draftId }
        return drafts.flatMap { draft ->
            val result = mutableListOf<ScriptFragment>()
            var start = 0
            byDraft[draft.id].orEmpty().sortedBy { it.start }.forEach { assignment ->
                if (start < assignment.start) result += ScriptFragment(draft.id, start, assignment.start, draft.original.substring(start, assignment.start))
                start = assignment.end
            }
            if (start < draft.original.length) result += ScriptFragment(draft.id, start, draft.original.length, draft.original.substring(start))
            result
        }
    }

    fun pendingParagraphs(): List<ScriptParagraph> = pendingFragments().flatMap { fragment ->
        scriptParagraphs(fragment.draftId, fragment.text).map {
            it.copy(start = fragment.start + it.start, end = fragment.start + it.end)
        }
    }

    fun isPendingRange(draftId: Long, start: Int, end: Int): Boolean {
        val draft = drafts.firstOrNull { it.id == draftId } ?: return false
        return validScriptRange(draft.original, start, end) && assignments.none {
            it.draftId == draftId && start < it.end && end > it.start
        }
    }

}

fun scriptParagraphs(draftId: Long, text: String): List<ScriptParagraph> {
    val result = ArrayList<ScriptParagraph>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val char = text[i++]
        if (char == '\r' || char == '\n') {
            if (char == '\r' && i < text.length && text[i] == '\n') i++
            result += ScriptParagraph(draftId, start, i, text.substring(start, i))
            start = i
        }
    }
    if (start < text.length) result += ScriptParagraph(draftId, start, text.length, text.substring(start))
    return result
}

/** A selection cannot cross an already assigned interval or another pasted batch. */
fun scriptSelection(paragraphs: List<ScriptParagraph>, anchor: ScriptParagraph, end: ScriptParagraph): List<ScriptParagraph> {
    if (anchor.draftId != end.draftId) return listOf(end)
    val selected = paragraphs.filter { it.draftId == anchor.draftId && it.start >= minOf(anchor.start, end.start) && it.end <= maxOf(anchor.end, end.end) }
    return if (selected.zipWithNext().all { (a, b) -> a.end == b.start }) selected else listOf(end)
}

/** Native selection offsets and persisted offsets both use UTF-16; never split a surrogate pair. */
fun validScriptRange(text: String, start: Int, end: Int): Boolean {
    fun boundary(index: Int): Boolean = index in 0..text.length &&
        !(index > 0 && index < text.length && text[index - 1].isHighSurrogate() && text[index].isLowSurrogate())
    return start < end && boundary(start) && boundary(end)
}

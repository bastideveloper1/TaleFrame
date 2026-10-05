package com.r0ybt.taleframe.data

import android.content.ContentValues

/** Exact SQLite rows; no normalization, new IDs or reconstruction through creation APIs. */
internal data class ElementSnapshot(val slideId: Long, val rows: List<ContentValues>, val mediaPaths: Set<String>)

/** Owned by the existing single disk writer. Only metadata is kept, never decoded media.
 * At most 30 confirmed element actions per editor session; no redo or on-disk history.
 */
internal class ElementUndoHistory(private val repository: StoryRepository, private val limit: Int = 30) {
    init { require(limit > 0) }
    private val entries = ArrayDeque<ElementSnapshot>()
    var slideId: Long? = null; private set
    val count: Int get() = entries.size
    var revision: Long = 0; private set

    fun beginSession(id: Long?) {
        if (id == slideId) return
        entries.clear(); slideId = id
        protectMedia()
        repository.cleanImages()
    }

    fun edit(action: StoryRepository.() -> Unit) {
        val id = slideId
        if (id == null) { repository.action(); return }
        val before = repository.read()
        val snapshot = repository.snapshotElements(id)
        // Protect the last reference BEFORE delete/replace can run the normal media cleaner.
        protectMedia(snapshot)
        try {
            repository.action()
            val after = repository.read()
            if (contextChanged(before, after, id)) {
                entries.clear()
                if (after.slides.none { it.id == id }) slideId = null
            } else if (snapshot.rows != repository.snapshotElements(id).rows) {
                entries.addLast(snapshot)
                if (entries.size > limit) entries.removeFirst()
            }
        } finally {
            protectMedia()
            repository.cleanImages()
        }
    }

    fun undo(): Boolean {
        val snapshot = entries.lastOrNull() ?: return false
        // Pop only after the transaction succeeds, so a failed restore keeps its history/files.
        repository.restoreElements(snapshot)
        entries.removeLast(); revision++
        protectMedia()
        repository.cleanImages()
        return true
    }

    private fun protectMedia(pending: ElementSnapshot? = null) {
        repository.undoMediaPaths = buildSet {
            entries.forEach { addAll(it.mediaPaths) }
            pending?.let { addAll(it.mediaPaths) }
        }
    }

    /** Structural edits and changed/deleted library definitions form a history boundary.
     * Adding resources/presets/templates is safe: undo changes only the element instance.
     */
    private fun contextChanged(before: Story, after: Story, id: Long): Boolean =
        before.projects != after.projects || before.slides != after.slides ||
            before.elements.filter { it.slideId != id } != after.elements.filter { it.slideId != id } ||
            !after.resources.containsAll(before.resources) || !after.characters.containsAll(before.characters) ||
            !after.expressions.containsAll(before.expressions) || !after.presets.containsAll(before.presets) ||
            !after.templates.containsAll(before.templates)
}

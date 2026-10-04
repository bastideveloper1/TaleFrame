package com.r0ybt.taleframe.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.r0ybt.taleframe.data.Story
import com.r0ybt.taleframe.data.StoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoryRepository(application)
    private val mutableStory = MutableStateFlow(Story())
    val story = mutableStory.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val edits = Channel<StoryRepository.() -> Unit>(Channel.UNLIMITED)

    init {
        // One disk writer off the UI thread; publish UI state on Main. Closing the Activity drains already
        // confirmed edits before closing SQLite, instead of cancelling an in-flight write.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    val loaded = repository.read()
                    withContext(Dispatchers.Main) { mutableStory.value = loaded; mutableReady.value = true }
                } catch (e: Exception) { withContext(Dispatchers.Main) { mutableError.value = e.message ?: "No se pudieron leer los proyectos" } }
                for (edit in edits) {
                    try {
                        repository.edit()
                        val loaded = repository.read()
                        withContext(Dispatchers.Main) { mutableStory.value = loaded }
                    } catch (e: Exception) { withContext(Dispatchers.Main) { mutableError.value = e.message ?: "No se pudo guardar el cambio" } }
                }
            } finally { repository.close() }
        }
    }
    fun edit(action: StoryRepository.() -> Unit) { edits.trySend(action) }
    fun clearError() { mutableError.value = null }
    override fun onCleared() { edits.close() }
}

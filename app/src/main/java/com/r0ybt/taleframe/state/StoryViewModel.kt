package com.r0ybt.taleframe.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.r0ybt.taleframe.data.Story
import com.r0ybt.taleframe.data.StoryRepository
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
    // Serialize edits: a drag or image import can never overwrite a later edit.
    private val edits = Channel<StoryRepository.() -> Unit>(Channel.UNLIMITED)
    init {
        viewModelScope.launch {
            try { mutableStory.value = withContext(Dispatchers.IO) { repository.read() }; mutableReady.value = true }
            catch(e: Exception) { mutableError.value = e.message ?: "No se pudieron leer los proyectos" }
            for(edit in edits) {
                try { mutableStory.value = withContext(Dispatchers.IO) { repository.edit(); repository.read() } }
                catch(e: Exception) { mutableError.value = e.message ?: "No se pudo guardar el cambio" }
            }
        }
    }
    fun edit(action: StoryRepository.() -> Unit) { edits.trySend(action) }
    fun clearError() { mutableError.value = null }
    override fun onCleared() { edits.close(); repository.close() }
}

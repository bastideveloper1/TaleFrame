package com.r0ybt.taleframe.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.r0ybt.taleframe.data.Story
import com.r0ybt.taleframe.data.ProjectBackup
import android.net.Uri
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

    data class BackupState(val progress: String? = null, val copyName: String? = null, val exported: Uri? = null, val imported: Long? = null)
    private val mutableBackup = MutableStateFlow(BackupState())
    val backup = mutableBackup.asStateFlow()
    private val backupStore = ProjectBackup(application, repository)
    private var pendingImport: ProjectBackup.Prepared? = null
    private val resolver = application.contentResolver

    fun exportProject(id: Long, uri: Uri) {
        mutableBackup.value = BackupState(progress = "Preparando proyecto…")
        edit {
            try {
                resolver.openOutputStream(uri, "wt")?.use { backupStore.export(id, it) { phase -> mutableBackup.value = BackupState(progress = phase) } }
                    ?: error("No se pudo abrir el destino")
                mutableBackup.value = BackupState(exported = uri)
            } catch (e: Exception) {
                try { android.provider.DocumentsContract.deleteDocument(resolver, uri) } catch (_: Exception) { }
                mutableBackup.value = BackupState()
                throw IllegalStateException("No se pudo exportar el proyecto. Comprueba el espacio disponible y el destino.", e)
            }
        }
    }
    fun importProject(uri: Uri) {
        mutableBackup.value = BackupState(progress = "Validando archivo…")
        edit {
            try {
                pendingImport?.close(); pendingImport = null
                val prepared = resolver.openInputStream(uri)?.let { backupStore.prepare(it) { phase -> mutableBackup.value = BackupState(progress = phase) } }
                    ?: error("No se pudo abrir el archivo")
                pendingImport = prepared
                val names = read().projects.map { it.name }
                if (prepared.name in names) mutableBackup.value = BackupState(copyName = ProjectBackup.copyName(prepared.name, names))
                else finishImport(prepared.name)
            } catch (e: Exception) {
                pendingImport?.close(); pendingImport = null
                mutableBackup.value = BackupState()
                throw if (e is ProjectBackup.InvalidPackage) e else IllegalStateException("No se pudo importar el proyecto.", e)
            }
        }
    }
    fun confirmImportCopy() {
        val name = mutableBackup.value.copyName ?: return
        mutableBackup.value = BackupState(progress = "Importando proyecto…")
        edit { finishImport(name) }
    }
    private fun finishImport(name: String) {
        val prepared = pendingImport ?: return
        try {
            val existing = repository.read().projects.map { it.name }
            val finalName = if (name in existing) ProjectBackup.copyName(prepared.name, existing) else name
            val id = backupStore.restore(prepared, finalName) { phase -> mutableBackup.value = BackupState(progress = phase) }
            mutableBackup.value = BackupState(imported = id)
        } catch (e: Exception) { mutableBackup.value = BackupState(); throw IllegalStateException("No se pudo importar el proyecto.", e) }
        finally { pendingImport = null; prepared.close() }
    }
    fun dismissBackup() {
        mutableBackup.value = BackupState()
        edit { pendingImport?.close(); pendingImport = null }
    }

    init {
        // One disk writer off the UI thread; publish UI state on Main. Closing the Activity drains already
        // confirmed edits before closing SQLite, instead of cancelling an in-flight write.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    // A killed process can leave a validation directory; no operation is running at startup.
                    application.cacheDir.listFiles()?.filter { it.name.startsWith("backup-") }?.forEach { it.deleteRecursively() }
                    backupStore.recoverInterruptedImport()
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
            } finally { pendingImport?.close(); repository.close() }
        }
    }
    fun edit(action: StoryRepository.() -> Unit) { edits.trySend(action) }
    fun clearError() { mutableError.value = null }
    override fun onCleared() { edits.close() }
}

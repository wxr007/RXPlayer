package com.rxplayer.app.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.model.VideoFolder
import com.rxplayer.app.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VideoRepository
) : ViewModel() {

    private val _folders = MutableStateFlow<List<VideoFolder>>(emptyList())
    val folders: StateFlow<List<VideoFolder>> = _folders

    private val _scanProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val scanProgress: StateFlow<Map<String, Float>> = _scanProgress

    private var synced = false

    init {
        observeDb()
        backgroundSync()
    }

    private fun observeDb() {
        viewModelScope.launch {
            repository.observeFolders()
                .catch { emit(emptyList()) }
                .collect { list ->
                    _folders.value = list
                }
        }
    }

    private fun backgroundSync() {
        if (synced) return
        synced = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.syncFolders()
            }
        }
    }

    fun addFolder(uri: Uri) {
        val uriString = uri.toString()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val prefs = context.getSharedPreferences("saf_folders", Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("uris", emptySet()) ?: emptySet()
        prefs.edit().putStringSet("uris", existing + uriString).apply()

        viewModelScope.launch {
            val name = safFolderDisplayName(uriString)
            val placeholder = VideoFolder(
                name = name,
                path = uriString,
                videoCount = 0,
                coverPaths = emptyList(),
                addedAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) {
                repository.insertFolder(placeholder)
            }

            _scanProgress.value = _scanProgress.value + (uriString to 0f)
            withContext(Dispatchers.IO) {
                repository.scanSafFolderWithProgress(uriString) { pct ->
                    _scanProgress.value = _scanProgress.value + (uriString to pct)
                }
            }
            _scanProgress.value = _scanProgress.value - uriString
        }
    }

    fun deleteFolder(folderPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteFolder(folderPath)
            }
            if (folderPath.startsWith("content://")) {
                val prefs = context.getSharedPreferences("saf_folders", Context.MODE_PRIVATE)
                val existing = prefs.getStringSet("uris", emptySet()) ?: emptySet()
                prefs.edit().putStringSet("uris", existing - folderPath).apply()
            }
        }
    }

    private fun safFolderDisplayName(safUri: String): String {
        val lastSegment = Uri.parse(safUri).lastPathSegment ?: return safUri.substringAfterLast("/")
        val path = lastSegment.substringAfter(":")
        return path.substringAfterLast("/").ifEmpty { path }
    }
}

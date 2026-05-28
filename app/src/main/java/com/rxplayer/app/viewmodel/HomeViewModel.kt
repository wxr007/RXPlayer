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
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val prefs = context.getSharedPreferences("saf_folders", Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("uris", emptySet()) ?: emptySet()
        prefs.edit().putStringSet("uris", existing + uri.toString()).apply()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.syncFolders()
            }
        }
    }
}

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

    init {
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            val mediaStoreFolders = withContext(Dispatchers.IO) {
                repository.getVideoFolders().toMutableList()
            }
            val safUris = getSavedSafUris()
            for (uriString in safUris) {
                val scanned = withContext(Dispatchers.IO) {
                    repository.scanSafFolder(uriString)
                }
                mediaStoreFolders.add(
                    scanned ?: VideoFolder(
                        name = Uri.parse(uriString).lastPathSegment ?: uriString.substringAfterLast("/"),
                        path = uriString,
                        videoCount = 0,
                        coverPaths = emptyList(),
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
            _folders.value = mediaStoreFolders
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
        loadFolders()
    }

    private fun getSavedSafUris(): List<String> {
        val prefs = context.getSharedPreferences("saf_folders", Context.MODE_PRIVATE)
        return prefs.getStringSet("uris", emptySet())?.toList() ?: emptyList()
    }
}

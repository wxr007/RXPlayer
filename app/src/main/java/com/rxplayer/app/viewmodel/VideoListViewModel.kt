package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    private val _displayMode = MutableStateFlow(false)
    val displayMode: StateFlow<Boolean> = _displayMode

    private val _gridColumns = MutableStateFlow(2)
    val gridColumns: StateFlow<Int> = _gridColumns

    private var synced = false
    private var currentFolderPath = ""

    fun loadVideos(folderPath: String) {
        currentFolderPath = folderPath
        observeDb(folderPath)
        backgroundSync(folderPath)
        loadDisplayMode(folderPath)
    }

    fun toggleDisplayMode() {
        val newMode = !_displayMode.value
        _displayMode.value = newMode
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setDisplayMode(currentFolderPath, if (newMode) 1 else 0)
            }
        }
    }

    fun toggleGridColumns() {
        _gridColumns.value = if (_gridColumns.value == 2) 4 else 2
    }

    private fun loadDisplayMode(folderPath: String) {
        viewModelScope.launch {
            val mode = withContext(Dispatchers.IO) {
                repository.getDisplayMode(folderPath)
            }
            _displayMode.value = mode == 1
        }
    }

    private fun observeDb(folderPath: String) {
        viewModelScope.launch {
            repository.observeVideosInFolder(folderPath)
                .catch { emit(emptyList()) }
                .collect { list ->
                    _videos.value = list
                }
        }
    }

    private fun backgroundSync(folderPath: String) {
        if (synced) return
        synced = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.syncFolderFromMediaStore(folderPath)
            }
        }
    }
}

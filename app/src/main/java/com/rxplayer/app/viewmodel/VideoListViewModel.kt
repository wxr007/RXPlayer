package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.data.repository.VideoRepository
import com.rxplayer.app.data.settings.SettingsManager
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
    private val repository: VideoRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    private val _displayMode = MutableStateFlow(false)
    val displayMode: StateFlow<Boolean> = _displayMode

    private val _gridColumns = MutableStateFlow(3)
    val gridColumns: StateFlow<Int> = _gridColumns

    private val _sortBy = MutableStateFlow("date")
    val sortBy: StateFlow<String> = _sortBy

    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending

    private val _thumbnailOrientation = MutableStateFlow(false)
    val thumbnailOrientation: StateFlow<Boolean> = _thumbnailOrientation

    private val _autoFullscreen = MutableStateFlow(false)
    val autoFullscreen: StateFlow<Boolean> = _autoFullscreen

    private val _playbackMode = MutableStateFlow(0)
    val playbackMode: StateFlow<Int> = _playbackMode

    val resolutionDisplay: StateFlow<String> = settingsManager.resolutionDisplay

    private var synced = false
    private var currentFolderPath = ""

    fun loadVideos(folderPath: String) {
        currentFolderPath = folderPath
        observeDb(folderPath)
        backgroundSync(folderPath)
        loadDisplayMode(folderPath)
        loadGridColumns(folderPath)
        loadSortSettings(folderPath)
        loadThumbnailOrientation(folderPath)
        loadAutoFullscreen(folderPath)
        loadPlaybackMode(folderPath)
    }

    fun setDisplayMode(mode: Boolean) {
        _displayMode.value = mode
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setDisplayMode(currentFolderPath, if (mode) 1 else 0)
            }
        }
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setGridColumns(currentFolderPath, columns)
            }
        }
    }

    fun setSort(sortBy: String, ascending: Boolean) {
        _sortBy.value = sortBy
        _sortAscending.value = ascending
        applySort()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setSort(currentFolderPath, sortBy, if (ascending) 1 else 0)
            }
        }
    }

    private fun applySort() {
        val sorted = when (_sortBy.value) {
            "date" -> _videos.value.sortedBy { it.addedAt }
            "duration" -> _videos.value.sortedBy { it.duration }
            "size" -> _videos.value.sortedBy { it.fileSize }
            else -> _videos.value.sortedBy { it.fileName.lowercase() }
        }
        _videos.value = if (_sortAscending.value) sorted else sorted.reversed()
    }

    private fun loadDisplayMode(folderPath: String) {
        viewModelScope.launch {
            val mode = withContext(Dispatchers.IO) {
                repository.getDisplayMode(folderPath)
            }
            _displayMode.value = mode == 1
        }
    }

    private fun loadGridColumns(folderPath: String) {
        viewModelScope.launch {
            val columns = withContext(Dispatchers.IO) {
                repository.getGridColumns(folderPath)
            }
            _gridColumns.value = columns
        }
    }

    fun setThumbnailOrientation(portrait: Boolean) {
        _thumbnailOrientation.value = portrait
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setThumbnailOrientation(currentFolderPath, if (portrait) 1 else 0)
            }
        }
    }

    private fun loadThumbnailOrientation(folderPath: String) {
        viewModelScope.launch {
            val orientation = withContext(Dispatchers.IO) {
                repository.getThumbnailOrientation(folderPath)
            }
            _thumbnailOrientation.value = orientation == 1
        }
    }

    fun setAutoFullscreen(enabled: Boolean) {
        _autoFullscreen.value = enabled
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setAutoFullscreen(currentFolderPath, if (enabled) 1 else 0)
            }
        }
    }

    fun setPlaybackMode(mode: Int) {
        _playbackMode.value = mode
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setPlaybackMode(currentFolderPath, mode)
            }
        }
    }

    private fun loadAutoFullscreen(folderPath: String) {
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                repository.getAutoFullscreen(folderPath)
            }
            _autoFullscreen.value = enabled == 1
        }
    }

    private fun loadPlaybackMode(folderPath: String) {
        viewModelScope.launch {
            val mode = withContext(Dispatchers.IO) {
                repository.getPlaybackMode(folderPath)
            }
            _playbackMode.value = mode
        }
    }

    private fun loadSortSettings(folderPath: String) {
        viewModelScope.launch {
            val (sortBy, ascending) = withContext(Dispatchers.IO) {
                repository.getSortSettings(folderPath)
            }
            _sortBy.value = sortBy
            _sortAscending.value = ascending == 1
            applySort()
        }
    }

    private fun observeDb(folderPath: String) {
        viewModelScope.launch {
            repository.observeVideosInFolder(folderPath)
                .catch { emit(emptyList()) }
                .collect { list ->
                    _videos.value = list
                    applySort()
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

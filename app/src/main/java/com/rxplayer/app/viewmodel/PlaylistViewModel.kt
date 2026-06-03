package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.db.PlaylistDao
import com.rxplayer.app.data.db.PlaylistEntity
import com.rxplayer.app.data.db.PlaylistVideoEntity
import com.rxplayer.app.data.db.PlaylistVideoJoined
import com.rxplayer.app.data.db.PlaylistWithCount
import com.rxplayer.app.data.db.toVideo
import com.rxplayer.app.data.model.Video
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
class PlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistWithCount>>(emptyList())
    val playlists: StateFlow<List<PlaylistWithCount>> = _playlists

    private val _playlistVideos = MutableStateFlow<List<Video>>(emptyList())
    val playlistVideos: StateFlow<List<Video>> = _playlistVideos

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
    val privacyMask: StateFlow<Boolean> = settingsManager.privacyMask

    private var currentPlaylistId = 0L

    init {
        observePlaylists()
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylistsWithCount()
                .catch { emit(emptyList()) }
                .collect { _playlists.value = it }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(name = name, createdAt = System.currentTimeMillis())
                )
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.clearPlaylist(playlistId)
                playlistDao.deletePlaylistById(playlistId)
            }
        }
    }

    fun observePlaylistVideos(playlistId: Long) {
        currentPlaylistId = playlistId
        loadDisplayMode(playlistId)
        loadGridColumns(playlistId)
        loadSortSettings(playlistId)
        loadThumbnailOrientation(playlistId)
        loadAutoFullscreen(playlistId)
        loadPlaybackMode(playlistId)
        viewModelScope.launch {
            playlistDao.getVideosInPlaylist(playlistId)
                .catch { emit(emptyList()) }
                .collect { joined ->
                    _playlistVideos.value = joined.map { it.toVideo() }
                    applySort()
                }
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: Video) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.addVideoToPlaylist(
                    PlaylistVideoEntity(
                        playlistId = playlistId,
                        filePath = video.filePath,
                        addedAt = System.currentTimeMillis()
                    )
                )
                val paths = playlistDao.getCoverPaths(playlistId)
                val list = paths.split("\n").filter { it.isNotEmpty() }
                if (list.size < 4 && !list.contains(video.filePath)) {
                    val updated = (list + video.filePath).joinToString("\n")
                    playlistDao.updateCoverPaths(playlistId, updated)
                }
            }
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, filePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.removeVideoFromPlaylist(playlistId, filePath)
                val paths = playlistDao.getCoverPaths(playlistId)
                val list = paths.split("\n").filter { it.isNotEmpty() }
                val updated = list.filter { it != filePath }.joinToString("\n")
                playlistDao.updateCoverPaths(playlistId, updated)
            }
        }
    }

    fun setDisplayMode(mode: Boolean) {
        _displayMode.value = mode
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.updateDisplayMode(currentPlaylistId, if (mode) 1 else 0)
            }
        }
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.updateGridColumns(currentPlaylistId, columns)
            }
        }
    }

    fun setSort(sortBy: String, ascending: Boolean) {
        _sortBy.value = sortBy
        _sortAscending.value = ascending
        applySort()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.updateSort(currentPlaylistId, sortBy, if (ascending) 1 else 0)
            }
        }
    }

    fun setThumbnailOrientation(portrait: Boolean) {
        _thumbnailOrientation.value = portrait
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.updateThumbnailOrientation(currentPlaylistId, if (portrait) 1 else 0)
            }
        }
    }

    fun setAutoFullscreen(enabled: Boolean) {
        _autoFullscreen.value = enabled
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.updateAutoFullscreen(currentPlaylistId, if (enabled) 1 else 0)
            }
        }
    }

    fun setPlaybackMode(mode: Int) {
        _playbackMode.value = mode
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.updatePlaybackMode(currentPlaylistId, mode)
            }
        }
    }

    private fun applySort() {
        val sorted = when (_sortBy.value) {
            "date" -> _playlistVideos.value.sortedBy { it.addedAt }
            "duration" -> _playlistVideos.value.sortedBy { it.duration }
            "size" -> _playlistVideos.value.sortedBy { it.fileSize }
            else -> _playlistVideos.value.sortedBy { it.fileName.lowercase() }
        }
        _playlistVideos.value = if (_sortAscending.value) sorted else sorted.reversed()
    }

    private fun loadDisplayMode(playlistId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { playlistDao.getPlaylistById(playlistId) }
            _displayMode.value = entity?.displayMode == 1
        }
    }

    private fun loadGridColumns(playlistId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { playlistDao.getPlaylistById(playlistId) }
            _gridColumns.value = entity?.gridColumns ?: 3
        }
    }

    private fun loadSortSettings(playlistId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { playlistDao.getPlaylistById(playlistId) }
            _sortBy.value = entity?.sortBy ?: "date"
            _sortAscending.value = entity?.sortAscending == 1
            applySort()
        }
    }

    private fun loadThumbnailOrientation(playlistId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { playlistDao.getPlaylistById(playlistId) }
            _thumbnailOrientation.value = entity?.thumbnailOrientation == 1
        }
    }

    private fun loadAutoFullscreen(playlistId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { playlistDao.getPlaylistById(playlistId) }
            _autoFullscreen.value = entity?.autoFullscreen == 1
        }
    }

    private fun loadPlaybackMode(playlistId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { playlistDao.getPlaylistById(playlistId) }
            _playbackMode.value = entity?.playbackMode ?: 0
        }
    }

    fun togglePrivacyMask() {
        settingsManager.setPrivacyMask(!settingsManager.privacyMask.value)
    }
}



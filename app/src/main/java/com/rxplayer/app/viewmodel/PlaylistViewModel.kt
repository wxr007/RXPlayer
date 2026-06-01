package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.db.PlaylistDao
import com.rxplayer.app.data.db.PlaylistEntity
import com.rxplayer.app.data.db.PlaylistVideoEntity
import com.rxplayer.app.data.db.PlaylistWithCount
import com.rxplayer.app.data.model.Video
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
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistWithCount>>(emptyList())
    val playlists: StateFlow<List<PlaylistWithCount>> = _playlists

    private val _playlistVideos = MutableStateFlow<List<Video>>(emptyList())
    val playlistVideos: StateFlow<List<Video>> = _playlistVideos

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
        viewModelScope.launch {
            playlistDao.getVideosInPlaylist(playlistId)
                .catch { emit(emptyList()) }
                .collect { entities ->
                    _playlistVideos.value = entities.map { it.toVideo() }
                }
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: PlaylistVideoEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.addVideoToPlaylist(video)
            }
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, filePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.removeVideoFromPlaylist(playlistId, filePath)
            }
        }
    }
}

private fun PlaylistVideoEntity.toVideo() = Video(
    filePath = filePath,
    folderPath = "",
    fileName = videoName,
    duration = duration,
    fileSize = 0L,
    resolution = resolution,
    mimeType = "",
    addedAt = addedAt
)

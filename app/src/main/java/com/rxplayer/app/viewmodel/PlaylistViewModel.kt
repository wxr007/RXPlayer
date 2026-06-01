package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.db.PlaylistDao
import com.rxplayer.app.data.db.PlaylistEntity
import com.rxplayer.app.data.db.PlaylistVideoEntity
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

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists

    private val _playlistVideos = MutableStateFlow<List<PlaylistVideoEntity>>(emptyList())
    val playlistVideos: StateFlow<List<PlaylistVideoEntity>> = _playlistVideos

    init {
        observePlaylists()
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists()
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
                .collect { _playlistVideos.value = it }
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

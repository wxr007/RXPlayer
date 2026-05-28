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

    private var synced = false

    fun loadVideos(folderPath: String) {
        observeDb(folderPath)
        backgroundSync(folderPath)
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

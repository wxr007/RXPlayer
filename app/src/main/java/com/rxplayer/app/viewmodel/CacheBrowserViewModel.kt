package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.DownloadManager
import com.rxplayer.app.data.db.StreamDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CachedStreamInfo(
    val streamId: Long,
    val name: String,
    val url: String,
    val bytesDownloaded: Long
)

@HiltViewModel
class CacheBrowserViewModel @Inject constructor(
    private val streamDao: StreamDao,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _cachedStreams = MutableStateFlow<List<CachedStreamInfo>>(emptyList())
    val cachedStreams: StateFlow<List<CachedStreamInfo>> = _cachedStreams

    init {
        observeCachedStreams()
    }

    private fun observeCachedStreams() {
        viewModelScope.launch {
            streamDao.getAllStreams()
                .catch { emit(emptyList()) }
                .collect { entities ->
                    val cached = entities.filter { it.cachedPath.isNotEmpty() }
                    _cachedStreams.value = cached.map { entity ->
                        val bytes = try {
                            val download = downloadManager.getDownloadIndex().getDownload(entity.id.toString())
                            download?.bytesDownloaded ?: 0L
                        } catch (_: Exception) { 0L }
                        CachedStreamInfo(entity.id, entity.name, entity.url, bytes)
                    }
                }
        }
    }

    fun removeCachedStream(streamId: Long) {
        viewModelScope.launch {
            try {
                downloadManager.removeDownload(streamId.toString())
            } catch (_: Exception) {}
            withContext(Dispatchers.IO) {
                streamDao.updateCachedPath(streamId, "")
            }
        }
    }
}

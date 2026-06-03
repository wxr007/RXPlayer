package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.db.StreamDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class CachedStreamInfo(
    val streamId: Long,
    val name: String,
    val url: String,
    val bytesDownloaded: Long
)

@HiltViewModel
class CacheBrowserViewModel @Inject constructor(
    private val streamDao: StreamDao
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
                    _cachedStreams.value = withContext(Dispatchers.IO) {
                        cached.map { entity ->
                            val bytes = if (entity.cachedPath.toLongOrNull() == null) {
                                val file = File(entity.cachedPath)
                                if (file.exists()) file.length() else 0L
                            } else {
                                0L
                            }
                            CachedStreamInfo(entity.id, entity.name, entity.url, bytes)
                        }
                    }
                }
        }
    }

    fun removeCachedStream(streamId: Long) {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            }
            if (entity != null && entity.cachedPath.isNotEmpty() && entity.cachedPath.toLongOrNull() == null) {
                val file = File(entity.cachedPath)
                if (file.exists()) file.delete()
            }
            withContext(Dispatchers.IO) {
                streamDao.updateCachedPath(streamId, "")
            }
        }
    }
}

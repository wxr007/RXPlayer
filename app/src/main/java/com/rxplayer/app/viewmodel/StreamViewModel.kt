package com.rxplayer.app.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.rxplayer.app.data.db.StreamDao
import com.rxplayer.app.data.db.StreamEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

const val CACHE_PREFIX_DM = "dl:"

data class StreamItem(
    val id: Long,
    val name: String,
    val url: String,
    val addedAt: Long,
    val cachedPath: String,
    val coverPath: String
)

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamDao: StreamDao,
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _streams = MutableStateFlow<List<StreamItem>>(emptyList())
    val streams: StateFlow<List<StreamItem>> = _streams

    private val _cachingIds = MutableStateFlow<Set<Long>>(emptySet())
    val cachingIds: StateFlow<Set<Long>> = _cachingIds

    private val _cachingProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val cachingProgress: StateFlow<Map<Long, Int>> = _cachingProgress

    private val _cacheError = MutableStateFlow<String?>(null)
    val cacheError: StateFlow<String?> = _cacheError

    private val _dmCompletedIds = MutableStateFlow<Set<Long>>(emptySet())
    val dmCompletedIds: StateFlow<Set<Long>> = _dmCompletedIds

    private val downloadJobs = mutableMapOf<Long, Job>()

    init {
        observeStreams()
    }

    fun isDmCompleted(streamId: Long): Boolean = streamId in _dmCompletedIds.value

    private fun observeStreams() {
        viewModelScope.launch {
            streamDao.getAllStreams()
                .catch { emit(emptyList()) }
                .collect { entities ->
                    _streams.value = entities.map { it.toItem() }
                    refreshDmCompletedState(entities)
                }
        }
    }

    fun addStream(name: String, url: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.insertStream(
                    StreamEntity(
                        name = name,
                        url = url,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun updateCoverPath(streamId: Long, coverPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.updateCoverPath(streamId, coverPath)
            }
        }
    }

    fun renameStream(streamId: Long, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.updateName(streamId, newName)
            }
        }
    }

    fun deleteStream(streamId: Long) {
        downloadJobs[streamId]?.cancel()
        downloadJobs.remove(streamId)
        viewModelScope.launch {
            clearStreamCache(streamId)
            withContext(Dispatchers.IO) {
                streamDao.deleteStreamById(streamId)
            }
        }
    }

    private fun getDmDownload(streamId: Long): Download? {
        return try {
            downloadManager.getDownloadIndex().getDownload(streamId.toString())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun observeDmProgress(streamId: Long, streamUrl: String) {
        _cachingProgress.value = _cachingProgress.value + (streamId to 0)
        try {
            while (true) {
                val dl = withContext(Dispatchers.IO) { getDmDownload(streamId) }
                if (dl == null) { delay(500); continue }
                when (dl.state) {
                    Download.STATE_COMPLETED -> {
                        _cachingProgress.value = _cachingProgress.value + (streamId to 100)
                        _dmCompletedIds.value = _dmCompletedIds.value + streamId
                        return
                    }
                    Download.STATE_FAILED -> {
                        throw Exception("下载失败: ${failureReasonString(dl.failureReason)}")
                    }
                    Download.STATE_REMOVING -> {
                        throw Exception("下载被取消")
                    }
                    else -> {
                        val pct = dl.percentDownloaded.toInt()
                        if (pct > 0) {
                            _cachingProgress.value = _cachingProgress.value + (streamId to pct)
                        }
                    }
                }
                delay(1000)
            }
        } catch (e: Exception) {
            Log.e("RXPlayer", "Download failed for $streamId", e)
            downloadManager.removeDownload(streamId.toString())
            withContext(Dispatchers.IO) { streamDao.updateCachedPath(streamId, "") }
            _cacheError.value = e.message ?: "下载失败"
        }
    }

    private fun refreshDmCompletedState(entities: List<StreamEntity>) {
        viewModelScope.launch {
            val completed = entities
                .filter { it.cachedPath.startsWith(CACHE_PREFIX_DM) }
                .mapNotNull { entity ->
                    val dl = withContext(Dispatchers.IO) { getDmDownload(entity.id) }
                    if (dl != null && dl.state == Download.STATE_COMPLETED) entity.id else null
                }
                .toSet()
            _dmCompletedIds.value = completed
        }
    }

    fun cacheStream(streamId: Long) {
        if (downloadJobs.containsKey(streamId)) return
        val stream = _streams.value.find { it.id == streamId } ?: return
        if (isFileCached(stream.cachedPath)) {
            val f = File(stream.cachedPath)
            if (f.exists() && f.length() > 0L) return
        }

        val job = viewModelScope.launch {
            _cachingIds.value = _cachingIds.value + streamId
            _cachingProgress.value = _cachingProgress.value + (streamId to 0)

            try {
                // Check for existing DM download (in progress or completed)
                val existingDl = withContext(Dispatchers.IO) { getDmDownload(streamId) }
                if (existingDl != null) {
                    if (existingDl.state == Download.STATE_COMPLETED) {
                        withContext(Dispatchers.IO) {
                            streamDao.updateCachedPath(streamId, "$CACHE_PREFIX_DM$streamId")
                        }
                        _cachingProgress.value = _cachingProgress.value + (streamId to 100)
                        return@launch
                    }
                    // In progress — observe and update UI
                    withContext(Dispatchers.IO) {
                        streamDao.updateCachedPath(streamId, "$CACHE_PREFIX_DM$streamId")
                    }
                    observeDmProgress(streamId, stream.url)
                    return@launch
                }

                val request = DownloadRequest.Builder(streamId.toString(), Uri.parse(stream.url))
                    .build()
                downloadManager.addDownload(request)

                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, "$CACHE_PREFIX_DM$streamId")
                }

                observeDmProgress(streamId, stream.url)
            } catch (e: Exception) {
                Log.e("RXPlayer", "Download failed for $streamId", e)
                downloadManager.removeDownload(streamId.toString())
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, "")
                }
                _cacheError.value = e.message ?: "下载失败"
            } finally {
                _cachingIds.value = _cachingIds.value - streamId
                _cachingProgress.value = _cachingProgress.value - streamId
                downloadJobs.remove(streamId)
            }
        }
        downloadJobs[streamId] = job
    }

    fun removeCachedStream(streamId: Long) {
        viewModelScope.launch {
            clearStreamCache(streamId)
            _dmCompletedIds.value = _dmCompletedIds.value - streamId
            withContext(Dispatchers.IO) {
                streamDao.updateCachedPath(streamId, "")
            }
        }
    }

    fun clearCacheError() {
        _cacheError.value = null
    }

    private suspend fun clearStreamCache(streamId: Long) {
        val stream = _streams.value.find { it.id == streamId } ?: return
        if (isFileCached(stream.cachedPath)) {
            val file = File(stream.cachedPath)
            if (file.exists()) file.delete()
        }
        if (isDmCached(stream.cachedPath)) {
            downloadManager.removeDownload(streamId.toString())
        }
    }

    companion object {
        fun isDmCached(cachedPath: String): Boolean =
            cachedPath.startsWith(CACHE_PREFIX_DM)

        fun isFileCached(cachedPath: String): Boolean =
            cachedPath.isNotEmpty() && !isDmCached(cachedPath)
    }

    private fun failureReasonString(reason: Int): String = when (reason) {
        Download.FAILURE_REASON_UNKNOWN -> "未知错误"
        else -> "错误码 $reason"
    }
}

private fun StreamEntity.toItem() = StreamItem(
    id = id,
    name = name,
    url = url,
    addedAt = addedAt,
    cachedPath = cachedPath,
    coverPath = coverPath
)

private fun StreamItem.toEntity() = StreamEntity(
    id = id,
    name = name,
    url = url,
    addedAt = addedAt,
    cachedPath = cachedPath,
    coverPath = coverPath
)

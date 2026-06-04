package com.rxplayer.app.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.rxplayer.app.data.db.StreamDao
import com.rxplayer.app.data.db.StreamEntity
import com.rxplayer.app.media.ExportState
import com.rxplayer.app.media.StreamExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class StreamItem(
    val id: Long,
    val name: String,
    val url: String,
    val addedAt: Long,
    val cachedPath: String,
    val coverPath: String,
    val resolution: String = "",
    val codec: String = "",
    val frameRate: String = "",
    val durationMs: Long = 0L
)

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamDao: StreamDao,
    private val downloadManager: DownloadManager,
    private val exportManager: StreamExportManager
) : ViewModel() {

    private val _streams = MutableStateFlow<List<StreamItem>>(emptyList())
    val streams: StateFlow<List<StreamItem>> = _streams

    private val _cachingIds = MutableStateFlow<Set<Long>>(emptySet())
    val cachingIds: StateFlow<Set<Long>> = _cachingIds

    private val _cachingProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val cachingProgress: StateFlow<Map<Long, Int>> = _cachingProgress

    init {
        observeStreams()
        pollDownloadStates()
    }

    private fun observeStreams() {
        viewModelScope.launch {
            streamDao.getAllStreams()
                .catch { emit(emptyList()) }
                .collect { entities ->
                    _streams.value = entities.map { it.toItem() }
                }
        }
    }

    private fun pollDownloadStates() {
        viewModelScope.launch {
            while (true) {
                val currentStreams = _streams.value
                val streamIdSet = currentStreams.map { it.id.toString() }.toSet()
                if (streamIdSet.isNotEmpty()) {
                    val cachingSet = mutableSetOf<Long>()
                    val progressMap = mutableMapOf<Long, Int>()
                    val completed = mutableListOf<Long>()
                    val cursor = try {
                        downloadManager.getDownloadIndex().getDownloads()
                    } catch (_: Exception) { null }
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            val download = cursor.download
                            if (download.request.id in streamIdSet) {
                                val sid = download.request.id.toLongOrNull()
                                if (sid != null) {
                                    when (download.state) {
                                        Download.STATE_DOWNLOADING -> {
                                            cachingSet.add(sid)
                                            progressMap[sid] = download.percentDownloaded.toInt()
                                        }
                                        Download.STATE_COMPLETED -> {
                                            completed.add(sid)
                                        }
                                    }
                                }
                            }
                        }
                        cursor.close()
                    }
                    _cachingIds.value = cachingSet
                    _cachingProgress.value = progressMap
                    if (completed.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            completed.forEach { sid ->
                                streamDao.updateCachedPath(sid, sid.toString())
                            }
                        }
                    }
                }
                delay(1000)
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.deleteStreamById(streamId)
            }
        }
    }

    fun updateVideoInfo(streamId: Long, resolution: String, codec: String, frameRate: String, durationMs: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.updateVideoInfo(streamId, resolution, codec, frameRate, durationMs)
            }
        }
    }

    val exportState: StateFlow<ExportState> = exportManager.state

    fun exportStream(streamId: Long, outputUri: Uri) {
        val stream = _streams.value.find { it.id == streamId } ?: return
        viewModelScope.launch {
            exportManager.export(stream.url, outputUri)
        }
    }

    fun resetExportState() {
        exportManager.resetState()
    }

    fun cacheStream(streamId: Long) {
        val stream = _streams.value.find { it.id == streamId } ?: return
        if (stream.cachedPath.isNotEmpty() && File(stream.cachedPath).exists()) return

        viewModelScope.launch {
            val existing = try {
                downloadManager.getDownloadIndex().getDownload(streamId.toString())
            } catch (_: Exception) { null }
            if (existing != null && existing.state == Download.STATE_COMPLETED) return@launch
            if (existing != null && existing.state == Download.STATE_DOWNLOADING) return@launch

            val request = DownloadRequest.Builder(streamId.toString(), Uri.parse(stream.url)).build()
            try {
                downloadManager.addDownload(request)
            } catch (e: Exception) {
                Log.e("RXPlayer", "StreamViewModel addDownload failed for $streamId", e)
            }
            _cachingIds.value = _cachingIds.value + streamId
        }
    }
}

private fun StreamEntity.toItem() = StreamItem(
    id = id,
    name = name,
    url = url,
    addedAt = addedAt,
    cachedPath = cachedPath,
    coverPath = coverPath,
    resolution = resolution,
    codec = codec,
    frameRate = frameRate,
    durationMs = durationMs
)

private fun StreamItem.toEntity() = StreamEntity(
    id = id,
    name = name,
    url = url,
    addedAt = addedAt,
    cachedPath = cachedPath,
    coverPath = coverPath,
    resolution = resolution,
    codec = codec,
    frameRate = frameRate,
    durationMs = durationMs
)

package com.rxplayer.app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.db.StreamDao
import com.rxplayer.app.data.db.StreamEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _streams = MutableStateFlow<List<StreamItem>>(emptyList())
    val streams: StateFlow<List<StreamItem>> = _streams

    private val _cachingIds = MutableStateFlow<Set<Long>>(emptySet())
    val cachingIds: StateFlow<Set<Long>> = _cachingIds

    private val _cachingProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val cachingProgress: StateFlow<Map<Long, Int>> = _cachingProgress

    private val downloadJobs = mutableMapOf<Long, Job>()

    init {
        observeStreams()
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
            deleteCachedFile(streamId)
            withContext(Dispatchers.IO) {
                streamDao.deleteStreamById(streamId)
            }
        }
    }

    fun cacheStream(streamId: Long) {
        if (downloadJobs.containsKey(streamId)) return
        val stream = _streams.value.find { it.id == streamId } ?: return
        if (stream.cachedPath.isNotEmpty() && File(stream.cachedPath).exists()) return

        val job = viewModelScope.launch {
            _cachingIds.value = _cachingIds.value + streamId
            _cachingProgress.value = _cachingProgress.value + (streamId to 0)

            try {
                val cachedPath = withContext(Dispatchers.IO) {
                    downloadToFile(stream, streamId) { progress ->
                        _cachingProgress.value = _cachingProgress.value + (streamId to progress)
                    }
                }
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, cachedPath)
                }
            } catch (e: Exception) {
                Log.e("RXPlayer", "Download failed for $streamId", e)
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
            deleteCachedFile(streamId)
            withContext(Dispatchers.IO) {
                streamDao.updateCachedPath(streamId, "")
            }
        }
    }

    private fun deleteCachedFile(streamId: Long) {
        val stream = _streams.value.find { it.id == streamId } ?: return
        if (stream.cachedPath.isNotEmpty()) {
            val file = File(stream.cachedPath)
            if (file.exists()) file.delete()
        }
    }

    private fun downloadToFile(
        stream: StreamItem,
        streamId: Long,
        onProgress: (Int) -> Unit
    ): String {
        val cacheDir = File(context.cacheDir, "stream_cache")
        cacheDir.mkdirs()

        val safeName = stream.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)
        val rawExt = stream.url.substringAfterLast(".").substringBefore("?").substringBefore("#").take(8)
        val ext = rawExt.ifBlank { "mp4" }
        val file = File(cacheDir, "${streamId}_${safeName}.${ext}")

        val url = URL(stream.url)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.connect()

        val contentLength = connection.contentLengthLong
        connection.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        onProgress((totalRead * 100 / contentLength).toInt())
                    }
                }
            }
        }
        return file.absolutePath
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

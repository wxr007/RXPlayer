package com.rxplayer.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rxplayer.app.data.db.StreamDao
import com.rxplayer.app.data.db.StreamEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    @ApplicationContext private val context: Context,
    private val streamDao: StreamDao
) : ViewModel() {

    private val _streams = MutableStateFlow<List<StreamItem>>(emptyList())
    val streams: StateFlow<List<StreamItem>> = _streams

    private val _cachingIds = MutableStateFlow<Set<Long>>(emptySet())
    val cachingIds: StateFlow<Set<Long>> = _cachingIds

    private val _cachingProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val cachingProgress: StateFlow<Map<Long, Int>> = _cachingProgress

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

    fun deleteStream(streamId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.deleteStreamById(streamId)
            }
        }
    }

    fun cacheStream(streamId: Long) {
        val stream = _streams.value.find { it.id == streamId } ?: return
        if (stream.cachedPath.isNotEmpty() && File(stream.cachedPath).exists()) return

        viewModelScope.launch {
            _cachingIds.value = _cachingIds.value + streamId
            _cachingProgress.value = _cachingProgress.value + (streamId to 0)
            try {
                val cacheDir = File(context.cacheDir, "stream_cache").also { it.mkdirs() }
                val ext = stream.url.substringAfterLast(".").substringBefore("?").take(10)
                    .ifEmpty { "mp4" }
                val targetFile = File(cacheDir, "${streamId}_${stream.name.hashCode().toUInt()}.$ext")

                withContext(Dispatchers.IO) {
                    val connection = URL(stream.url).openConnection()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()
                    val totalBytes = connection.contentLengthLong
                    val input = connection.getInputStream()
                    val output = FileOutputStream(targetFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val pct = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            _cachingProgress.value = _cachingProgress.value + (streamId to pct)
                        }
                    }
                    output.close()
                    input.close()
                }

                streamDao.updateCachedPath(streamId, targetFile.absolutePath)
            } catch (_: Exception) {
            } finally {
                _cachingIds.value = _cachingIds.value - streamId
                _cachingProgress.value = _cachingProgress.value - streamId
            }
        }
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

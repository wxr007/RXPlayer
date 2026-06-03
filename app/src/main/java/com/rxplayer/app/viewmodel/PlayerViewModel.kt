package com.rxplayer.app.viewmodel

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.rxplayer.app.data.db.PlaylistDao
import com.rxplayer.app.data.db.StreamDao
import com.rxplayer.app.data.db.toVideo
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.data.repository.VideoRepository
import com.rxplayer.app.data.settings.SettingsManager
import com.rxplayer.app.media.SceneAnalyzer
import com.rxplayer.app.media.SceneData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val sceneAnalyzer: SceneAnalyzer,
    private val settingsManager: SettingsManager,
    private val repository: VideoRepository,
    private val playlistDao: PlaylistDao,
    private val streamDao: StreamDao,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val videoPath: String = decodeVideoPath(savedStateHandle.get<String>("videoPath") ?: "")
    private val folderPath: String = decodeFolderPath(savedStateHandle.get<String>("folderPath") ?: "")
    private val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 0L
    private val streamId: Long = savedStateHandle.get<Long>("streamId") ?: 0L

    fun getCacheDataSourceFactory(): CacheDataSource.Factory = cacheDataSourceFactory

    private fun decodeVideoPath(encoded: String): String {
        return try {
            String(Base64.decode(encoded, Base64.URL_SAFE))
        } catch (_: Exception) {
            encoded
        }
    }

    private fun decodeFolderPath(encoded: String): String {
        return try {
            String(Base64.decode(encoded, Base64.URL_SAFE))
        } catch (_: Exception) {
            encoded
        }
    }

    private val _scenes = MutableStateFlow<List<SceneData>>(emptyList())
    val scenes: StateFlow<List<SceneData>> = _scenes

    val analyzingProgress: StateFlow<Float?> = sceneAnalyzer.analyzingProgress
    val isAnalyzing: StateFlow<Boolean> = sceneAnalyzer.isAnalyzing
    val autoPlay: StateFlow<Boolean> = settingsManager.autoPlay
    val analysisMode: StateFlow<String> = settingsManager.analysisMode
    val analysisInterval: StateFlow<Int> = settingsManager.analysisInterval
    val seekStep: StateFlow<Int> = settingsManager.seekStep

    private val _cacheProgress = MutableStateFlow(-1)
    val cacheProgress: StateFlow<Int> = _cacheProgress

    private val _isCached = MutableStateFlow(false)
    val isCached: StateFlow<Boolean> = _isCached

    init {
        if (streamId > 0L) {
            checkCached()
        }
        if (videoPath.isNotEmpty()) {
            observeScenes()
        }
        if (folderPath.isNotEmpty()) {
            loadFolderVideos()
        } else if (playlistId > 0) {
            loadPlaylistVideos()
        }
    }

    private fun checkDownloadManagerCached(): Boolean {
        return try {
            val download = downloadManager.getDownloadIndex().getDownload(streamId.toString())
            download != null && download.state == Download.STATE_COMPLETED
        } catch (_: Exception) {
            false
        }
    }

    private fun checkCached() {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            }
            if (entity != null && entity.cachedPath.isNotEmpty()) {
                if (entity.cachedPath.toLongOrNull() == null) {
                    if (File(entity.cachedPath).exists()) {
                        _isCached.value = true
                        return@launch
                    }
                }
            }
            if (checkDownloadManagerCached()) {
                _isCached.value = true
            }
        }
    }

    fun cacheCurrentStream() {
        if (streamId <= 0L) return
        if (_cacheProgress.value >= 0) return
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            } ?: return@launch
            if (entity.cachedPath.isNotEmpty() && entity.cachedPath.toLongOrNull() == null && File(entity.cachedPath).exists()) {
                _isCached.value = true; return@launch
            }
            if (checkDownloadManagerCached()) {
                _isCached.value = true; return@launch
            }

            _cacheProgress.value = 0
            try {
                val cachedPath = withContext(Dispatchers.IO) {
                    downloadToFile(entity.name, entity.url, streamId) { progress ->
                        if (_cacheProgress.value >= 0) {
                            _cacheProgress.value = progress
                        }
                    }
                }
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, cachedPath)
                }
                _isCached.value = true
            } catch (e: Exception) {
                Log.e("RXPlayer", "Download failed for stream $streamId", e)
            } finally {
                _cacheProgress.value = -1
            }
        }
    }

    fun removeCachedStream() {
        if (streamId <= 0L) return
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            }
            if (entity != null) {
                if (entity.cachedPath.isNotEmpty() && entity.cachedPath.toLongOrNull() == null) {
                    val file = File(entity.cachedPath)
                    if (file.exists()) file.delete()
                }
                try {
                    downloadManager.removeDownload(streamId.toString())
                } catch (_: Exception) {}
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, "")
                }
            }
            _isCached.value = false
        }
    }

    private fun downloadToFile(
        name: String,
        url: String,
        streamId: Long,
        onProgress: (Int) -> Unit
    ): String {
        val cacheDir = File(context.cacheDir, "stream_cache")
        cacheDir.mkdirs()

        val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)
        val rawExt = url.substringAfterLast(".").substringBefore("?").substringBefore("#").take(8)
        val ext = rawExt.ifBlank { "mp4" }
        val file = File(cacheDir, "${streamId}_${safeName}.${ext}")

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()

        val contentLength = conn.contentLengthLong
        conn.inputStream.use { input ->
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

    private val _folderVideos = MutableStateFlow<List<Video>>(emptyList())
    val folderVideos: StateFlow<List<Video>> = _folderVideos

    fun getCurrentVideoIndex(): Int {
        return _folderVideos.value.indexOfFirst { it.filePath == videoPath }
    }

    fun clearAnalysis() {
        sceneAnalyzer.clearAnalysis(videoPath)
    }

    fun triggerAnalysis() {
        sceneAnalyzer.analyzeVideo(videoPath, force = true)
    }

    fun updateStreamCover(streamId: Long, coverPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.updateCoverPath(streamId, coverPath)
            }
        }
    }

    fun analyzeWithMode(mode: String, interval: Int) {
        settingsManager.setAnalysisMode(mode)
        settingsManager.setAnalysisInterval(interval)
        sceneAnalyzer.analyzeVideo(videoPath, force = true)
    }

    private fun loadFolderVideos() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.syncFolderFromMediaStore(folderPath)
                }
                val (sortBy, sortAscending) = withContext(Dispatchers.IO) {
                    repository.getSortSettings(folderPath)
                }
                val snapshot = withContext(Dispatchers.IO) {
                    repository.getVideosInFolderSnapshot(folderPath)
                }
                val sorted = when (sortBy) {
                    "date" -> snapshot.sortedBy { it.addedAt }
                    "duration" -> snapshot.sortedBy { it.duration }
                    "size" -> snapshot.sortedBy { it.fileSize }
                    else -> snapshot.sortedBy { it.fileName.lowercase() }
                }
                _folderVideos.value = if (sortAscending == 1) sorted else sorted.reversed()
            } catch (e: Exception) {
                Log.e("RXPlayer", "Failed to load folder videos", e)
            }
        }
    }

    private fun loadPlaylistVideos() {
        viewModelScope.launch {
            try {
                val entity = withContext(Dispatchers.IO) {
                    playlistDao.getPlaylistById(playlistId)
                }
                val joined = withContext(Dispatchers.IO) {
                    playlistDao.getVideosInPlaylistSnapshot(playlistId)
                }
                val videos = joined.map { it.toVideo() }
                val sortBy = entity?.sortBy ?: "date"
                val sortAscending = entity?.sortAscending ?: 0
                val sorted = when (sortBy) {
                    "date" -> videos.sortedBy { it.addedAt }
                    "duration" -> videos.sortedBy { it.duration }
                    "size" -> videos.sortedBy { it.fileSize }
                    else -> videos.sortedBy { it.fileName.lowercase() }
                }
                _folderVideos.value = if (sortAscending == 1) sorted else sorted.reversed()
            } catch (e: Exception) {
                Log.e("RXPlayer", "Failed to load playlist videos", e)
            }
        }
    }

    private fun observeScenes() {
        viewModelScope.launch {
            sceneAnalyzer.observeScenes(videoPath).collect { sceneList ->
                _scenes.value = sceneList
            }
        }
    }
}

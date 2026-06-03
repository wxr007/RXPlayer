package com.rxplayer.app.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    private val _cacheError = MutableStateFlow<String?>(null)
    val cacheError: StateFlow<String?> = _cacheError

    fun clearCacheError() { _cacheError.value = null }

    suspend fun resolveStreamUri(fallbackPath: String): Uri {
        if (streamId <= 0L) {
            return if (fallbackPath.startsWith("/") || fallbackPath.startsWith("file://"))
                Uri.fromFile(File(fallbackPath)) else Uri.parse(fallbackPath)
        }
        val entity = withContext(Dispatchers.IO) { streamDao.getStreamById(streamId) }
            ?: return if (fallbackPath.startsWith("/") || fallbackPath.startsWith("file://"))
                Uri.fromFile(File(fallbackPath)) else Uri.parse(fallbackPath)

        if (entity.cachedPath.isNotEmpty()) {
            if (entity.cachedPath.toLongOrNull() != null) {
                return Uri.parse(entity.url)
            }
            val f = File(entity.cachedPath)
            if (f.exists() && f.length() > 0L) {
                return Uri.fromFile(f)
            }
        }
        return Uri.parse(entity.url)
    }

    private var pollJob: kotlinx.coroutines.Job? = null

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            if (download.request.id != streamId.toString()) return
            when (download.state) {
                Download.STATE_DOWNLOADING -> {
                    _cacheProgress.value = download.percentDownloaded.toInt()
                }
                Download.STATE_COMPLETED -> {
                    onDownloadCompleted()
                }
                Download.STATE_FAILED -> {
                    _cacheProgress.value = -1
                    Log.e("RXPlayer", "Download failed for stream $streamId", finalException)
                }
                Download.STATE_REMOVING -> {
                    _cacheProgress.value = -1
                    _isCached.value = false
                }
            }
        }
    }

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
        downloadManager.addListener(downloadListener)
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        downloadManager.removeListener(downloadListener)
    }

    private fun queryDownload(): Download? {
        return try {
            downloadManager.getDownloadIndex().getDownload(streamId.toString())
        } catch (_: Exception) {
            null
        }
    }

    private fun onDownloadCompleted() {
        _cacheProgress.value = -1
        _isCached.value = true
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            }
            if (entity != null && entity.cachedPath.isEmpty()) {
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, streamId.toString())
                }
            }
        }
    }

    private fun pollDownloadProgress() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val download = queryDownload()
                if (download != null) {
                    when (download.state) {
                        Download.STATE_DOWNLOADING -> {
                            _cacheProgress.value = download.percentDownloaded.toInt()
                        }
                        Download.STATE_COMPLETED -> {
                            onDownloadCompleted()
                            return@launch
                        }
                        Download.STATE_FAILED -> {
                            _cacheProgress.value = -1
                            return@launch
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun checkCached() {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            }
            if (entity != null) {
                if (entity.cachedPath.isNotEmpty()) {
                    if (entity.cachedPath.toLongOrNull() != null) {
                        val download = queryDownload()
                        if (download != null) {
                            when (download.state) {
                                Download.STATE_COMPLETED -> { _isCached.value = true; return@launch }
                                Download.STATE_DOWNLOADING -> { _cacheProgress.value = download.percentDownloaded.toInt(); pollDownloadProgress(); return@launch }
                            }
                        }
                    } else if (File(entity.cachedPath).exists()) {
                        _isCached.value = true
                        return@launch
                    }
                }
            }
            val download = queryDownload()
            if (download != null) {
                when (download.state) {
                    Download.STATE_COMPLETED -> _isCached.value = true
                    Download.STATE_DOWNLOADING -> {
                        _cacheProgress.value = download.percentDownloaded.toInt()
                        pollDownloadProgress()
                    }
                }
            }
        }
    }

    fun cacheCurrentStream() {
        if (streamId <= 0L) return
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            } ?: return@launch
            if (entity.cachedPath.isNotEmpty()) {
                if (entity.cachedPath.toLongOrNull() != null) {
                    val existing = queryDownload()
                    if (existing != null) {
                        when (existing.state) {
                            Download.STATE_COMPLETED -> { _isCached.value = true; return@launch }
                            Download.STATE_DOWNLOADING -> { _cacheProgress.value = existing.percentDownloaded.toInt(); pollDownloadProgress(); return@launch }
                        }
                    }
                } else if (File(entity.cachedPath).exists()) {
                    _isCached.value = true
                    return@launch
                }
            }
            val existing = queryDownload()
            if (existing != null && existing.state == Download.STATE_COMPLETED) {
                _isCached.value = true
                return@launch
            }
            if (existing != null && existing.state == Download.STATE_DOWNLOADING) {
                _cacheProgress.value = existing.percentDownloaded.toInt()
                pollDownloadProgress()
                return@launch
            }

            val request = DownloadRequest.Builder(streamId.toString(), Uri.parse(entity.url)).build()
            try {
                downloadManager.addDownload(request)
            } catch (e: Exception) {
                Log.e("RXPlayer", "addDownload failed for stream $streamId", e)
                return@launch
            }
            _cacheProgress.value = 0
            pollDownloadProgress()
        }
    }

    fun removeCachedStream() {
        if (streamId <= 0L) return
        viewModelScope.launch {
            downloadManager.removeDownload(streamId.toString())
            _isCached.value = false
        }
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

    fun updateStreamVideoInfo(resolution: String, codec: String, frameRate: String, durationMs: Long) {
        if (streamId <= 0L) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamDao.updateVideoInfo(streamId, resolution, codec, frameRate, durationMs)
            }
        }
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

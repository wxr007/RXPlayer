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
            if (entity.cachedPath.startsWith("dl:")) {
                return Uri.parse(entity.url)
            }
            val ext = entity.cachedPath.substringAfterLast(".").lowercase()
            if (ext in setOf("m3u8", "mpd")) {
                return Uri.parse(entity.url)
            }
            val f = File(entity.cachedPath)
            if (f.exists() && f.length() > 0L) {
                return Uri.fromFile(f)
            }
        }
        return Uri.parse(entity.url)
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
                if (entity.cachedPath.startsWith("dl:")) {
                    if (checkDownloadManagerCached()) {
                        _isCached.value = true
                    }
                    return@launch
                }
                val f = File(entity.cachedPath)
                if (f.exists() && f.length() > 0L) {
                    _isCached.value = true
                    return@launch
                }
                if (f.exists()) f.delete()
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
            if (entity.cachedPath.isNotEmpty()) {
                if (entity.cachedPath.startsWith("dl:")) {
                    if (checkDownloadManagerCached()) {
                        _isCached.value = true; return@launch
                    }
                } else {
                    val f = File(entity.cachedPath)
                    if (f.exists() && f.length() > 0L) {
                        _isCached.value = true; return@launch
                    }
                    if (f.exists()) f.delete()
                }
            }
            if (checkDownloadManagerCached()) {
                _isCached.value = true; return@launch
            }

            _cacheProgress.value = 0
            try {
                val request = DownloadRequest.Builder(streamId.toString(), Uri.parse(entity.url))
                    .build()
                downloadManager.addDownload(request)
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, "${CACHE_PREFIX_DM}$streamId")
                }

                while (true) {
                    val dl = withContext(Dispatchers.IO) {
                        downloadManager.getDownloadIndex().getDownload(streamId.toString())
                    }
                    if (dl == null) { delay(500); continue }
                    when (dl.state) {
                        Download.STATE_COMPLETED -> {
                            _cacheProgress.value = 100
                            _isCached.value = true
                            break
                        }
                        Download.STATE_FAILED -> {
                            throw Exception("下载失败: ${failureReasonString(dl.failureReason)}")
                        }
                        Download.STATE_REMOVING -> {
                            throw Exception("下载被取消")
                        }
                        else -> {
                            val pct = dl.percentDownloaded.toInt()
                            if (pct > 0 && _cacheProgress.value >= 0) {
                                _cacheProgress.value = pct
                            }
                        }
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e("RXPlayer", "Download failed for stream $streamId", e)
                downloadManager.removeDownload(streamId.toString())
                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, "")
                }
                _cacheError.value = e.message ?: "下载失败"
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
                if (entity.cachedPath.isNotEmpty() && !entity.cachedPath.startsWith("dl:")) {
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

    private fun failureReasonString(reason: Int): String = when (reason) {
        Download.FAILURE_REASON_UNKNOWN -> "未知错误"
        else -> "错误码 $reason"
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

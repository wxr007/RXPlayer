package com.rxplayer.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Base64
import android.util.Log
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
    private val streamDao: StreamDao
) : ViewModel() {

    private val videoPath: String = decodeVideoPath(savedStateHandle.get<String>("videoPath") ?: "")
    private val folderPath: String = decodeFolderPath(savedStateHandle.get<String>("folderPath") ?: "")
    private val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 0L
    private val streamId: Long = savedStateHandle.get<Long>("streamId") ?: 0L

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
    }

    private fun checkCached() {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            }
            _isCached.value = entity != null && entity.cachedPath.isNotEmpty() && File(entity.cachedPath).exists()
        }
    }

    fun cacheCurrentStream() {
        if (streamId <= 0L) return
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                streamDao.getStreamById(streamId)
            } ?: return@launch
            if (entity.cachedPath.isNotEmpty() && File(entity.cachedPath).exists()) {
                _isCached.value = true
                return@launch
            }
            _cacheProgress.value = 0
            try {
                val cacheDir = File(context.cacheDir, "stream_cache").also { it.mkdirs() }
                val ext = entity.url.substringAfterLast(".").substringBefore("?").take(10)
                    .ifEmpty { "mp4" }
                val targetFile = File(cacheDir, "${streamId}_${entity.name.hashCode().toUInt()}.$ext")

                withContext(Dispatchers.IO) {
                    val connection = URL(entity.url).openConnection()
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
                            _cacheProgress.value = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                        }
                    }
                    output.close()
                    input.close()
                }

                withContext(Dispatchers.IO) {
                    streamDao.updateCachedPath(streamId, targetFile.absolutePath)
                }
                _isCached.value = true
            } catch (_: Exception) {
            } finally {
                _cacheProgress.value = -1
            }
        }
    }

    private val _folderVideos = MutableStateFlow<List<Video>>(emptyList())
    val folderVideos: StateFlow<List<Video>> = _folderVideos

    fun getCurrentVideoIndex(): Int {
        return _folderVideos.value.indexOfFirst { it.filePath == videoPath }
    }

    init {
        if (videoPath.isNotEmpty()) {
            observeScenes()
        }
        if (folderPath.isNotEmpty()) {
            loadFolderVideos()
        } else if (playlistId > 0) {
            loadPlaylistVideos()
        }
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

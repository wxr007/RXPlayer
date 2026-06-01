package com.rxplayer.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import android.util.Log
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.data.repository.VideoRepository
import com.rxplayer.app.data.settings.SettingsManager
import com.rxplayer.app.media.SceneAnalyzer
import com.rxplayer.app.media.SceneData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sceneAnalyzer: SceneAnalyzer,
    private val settingsManager: SettingsManager,
    private val repository: VideoRepository
) : ViewModel() {

    private val videoPath: String = decodeVideoPath(savedStateHandle.get<String>("videoPath") ?: "")
    private val folderPath: String = decodeFolderPath(savedStateHandle.get<String>("folderPath") ?: "")

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
    val seekStep: StateFlow<Int> = settingsManager.seekStep

    private val _folderVideos = MutableStateFlow<List<Video>>(emptyList())
    val folderVideos: StateFlow<List<Video>> = _folderVideos

    fun getCurrentVideoIndex(): Int {
        return _folderVideos.value.indexOfFirst { it.filePath == videoPath }
    }

    init {
        Log.d("RXPlayer", "PlayerViewModel init, videoPath=$videoPath")
        if (videoPath.isNotEmpty()) {
            observeScenes()
        }
        if (folderPath.isNotEmpty()) {
            loadFolderVideos()
        }
    }

    fun triggerAnalysis() {
        Log.d("RXPlayer", "PlayerViewModel.triggerAnalysis called, videoPath=$videoPath")
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

    private fun observeScenes() {
        viewModelScope.launch {
            sceneAnalyzer.observeScenes(videoPath).collect { sceneList ->
                _scenes.value = sceneList
            }
        }
    }
}

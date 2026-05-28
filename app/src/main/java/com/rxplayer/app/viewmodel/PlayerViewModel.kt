package com.rxplayer.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import android.util.Log
import com.rxplayer.app.media.SceneAnalyzer
import com.rxplayer.app.media.SceneData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sceneAnalyzer: SceneAnalyzer
) : ViewModel() {

    private val videoPath: String = decodeVideoPath(savedStateHandle.get<String>("videoPath") ?: "")

    private fun decodeVideoPath(encoded: String): String {
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

    init {
        Log.d("RXPlayer", "PlayerViewModel init, videoPath=$videoPath")
        if (videoPath.isNotEmpty()) {
            observeScenes()
            sceneAnalyzer.analyzeVideo(videoPath)
        }
    }

    fun triggerAnalysis() {
        Log.d("RXPlayer", "PlayerViewModel.triggerAnalysis called, videoPath=$videoPath")
        sceneAnalyzer.analyzeVideo(videoPath, force = true)
    }

    private fun observeScenes() {
        viewModelScope.launch {
            sceneAnalyzer.observeScenes(videoPath).collect { sceneList ->
                _scenes.value = sceneList
            }
        }
    }
}

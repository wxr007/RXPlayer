package com.rxplayer.app.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rxplayer.app.data.db.ScenePointDao
import com.rxplayer.app.data.db.ScenePointEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scenePointDao: ScenePointDao,
    private val settingsManager: com.rxplayer.app.data.settings.SettingsManager
) {
    private val detector = SceneDetector(context)
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _analyzingProgress = MutableStateFlow<Float?>(null)
    val analyzingProgress: StateFlow<Float?> = _analyzingProgress

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    fun observeScenes(videoPath: String): Flow<List<SceneData>> {
        return scenePointDao.getScenesForVideo(videoPath).map { entities ->
            entities.map { entity ->
                SceneData(
                    timestampMs = entity.timestampMs,
                    thumbnailPath = entity.thumbnailPath,
                    sceneIndex = entity.sceneIndex
                )
            }
        }
    }

    fun analyzeVideo(videoPath: String, force: Boolean = false) {
        Log.d("RXPlayer", "analyzeVideo called, force=$force, videoPath=$videoPath, isAnalyzing=${_isAnalyzing.value}")
        if (_isAnalyzing.value) {
            Log.d("RXPlayer", "analyzeVideo: already analyzing, skipping")
            return
        }
        scope.launch {
            if (!force) {
                val count = scenePointDao.getSceneCount(videoPath)
                Log.d("RXPlayer", "analyzeVideo: existing scene count=$count")
                if (count > 0) return@launch
            }

            Log.d("RXPlayer", "analyzeVideo: starting analysis")
            _isAnalyzing.value = true
            scenePointDao.deleteScenesForVideo(videoPath)
            detector.clearCache(videoPath)
            _analyzingProgress.value = 0f

            val uri = Uri.parse(videoPath)
            Log.d("RXPlayer", "analyzeVideo: uri=$uri, scheme=${uri.scheme}, path=${uri.path}")
            val analysisMode = settingsManager.analysisMode.value
            val analysisInterval = settingsManager.analysisInterval.value
            val scenes = detector.detectScenes(
                uri = uri,
                mode = analysisMode,
                intervalSec = analysisInterval,
                onProgress = { progress ->
                    _analyzingProgress.value = progress
                }
            )

            Log.d("RXPlayer", "analyzeVideo: detected ${scenes.size} scenes")
            val entities = scenes.map { scene ->
                ScenePointEntity(
                    videoPath = videoPath,
                    timestampMs = scene.timestampMs,
                    thumbnailPath = scene.thumbnailPath,
                    sceneIndex = scene.sceneIndex
                )
            }
            if (entities.isNotEmpty()) {
                scenePointDao.insertAll(entities)
            }
            _analyzingProgress.value = null
            _isAnalyzing.value = false
            Log.d("RXPlayer", "analyzeVideo: done")
        }
    }
}

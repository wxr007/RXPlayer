package com.rxplayer.app.media

import android.content.Context
import android.net.Uri
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
    private val scenePointDao: ScenePointDao
) {
    private val detector = SceneDetector(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _analyzingProgress = MutableStateFlow<Float?>(null)
    val analyzingProgress: StateFlow<Float?> = _analyzingProgress

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

    fun analyzeVideo(videoPath: String) {
        scope.launch {
            val count = scenePointDao.getSceneCount(videoPath)
            if (count > 0) return@launch

            scenePointDao.deleteScenesForVideo(videoPath)
            _analyzingProgress.value = 0f

            val uri = Uri.parse(videoPath)
            val scenes = detector.detectScenes(
                uri = uri,
                onProgress = { progress ->
                    _analyzingProgress.value = progress
                }
            )

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
        }
    }
}

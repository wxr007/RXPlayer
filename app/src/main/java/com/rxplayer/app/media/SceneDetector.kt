package com.rxplayer.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class SceneDetector(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "scene_thumbnails")

    suspend fun detectScenes(
        uri: Uri,
        intervalMs: Long = 500L,
        threshold: Float = 0.35f,
        thumbnailWidth: Int = 120,
        thumbnailHeight: Int = 68,
        onProgress: ((Float) -> Unit)? = null
    ): List<SceneData> = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: run {
            retriever.release()
            return@withContext emptyList()
        }

        if (durationMs <= 0) {
            retriever.release()
            return@withContext emptyList()
        }

        val scenes = mutableListOf<SceneData>()
        var prevPixels: IntArray? = null
        var sceneIndex = 0
        val compareSize = 16
        var currentMs = 0L

        while (currentMs < durationMs) {
            val frame = retriever.getFrameAtTime(
                currentMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            if (frame != null) {
                val small = Bitmap.createScaledBitmap(frame, compareSize, compareSize, true)
                val pixels = IntArray(compareSize * compareSize)
                small.getPixels(pixels, 0, compareSize, 0, 0, compareSize, compareSize)
                small.recycle()

                if (prevPixels != null) {
                    val diff = compareFrames(pixels, prevPixels, compareSize)
                    if (diff > threshold) {
                        val thumbFile = File(cacheDir, "scene_${sceneIndex}_${currentMs}.jpg")
                        saveThumbnail(frame, thumbFile, thumbnailWidth, thumbnailHeight)
                        scenes.add(SceneData(currentMs, thumbFile.absolutePath, sceneIndex))
                        sceneIndex++
                    }
                }

                prevPixels = pixels
                frame.recycle()
            }

            currentMs += intervalMs
            onProgress?.invoke(currentMs.toFloat() / durationMs)
        }

        retriever.release()
        scenes
    }

    private fun compareFrames(pixels1: IntArray, pixels2: IntArray, size: Int): Float {
        var totalDiff = 0f
        for (i in pixels1.indices) {
            val r1 = (pixels1[i] shr 16) and 0xFF
            val g1 = (pixels1[i] shr 8) and 0xFF
            val b1 = pixels1[i] and 0xFF
            val r2 = (pixels2[i] shr 16) and 0xFF
            val g2 = (pixels2[i] shr 8) and 0xFF
            val b2 = pixels2[i] and 0xFF
            totalDiff += abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
        }
        return totalDiff / (size * size * 3 * 255f)
    }

    private fun saveThumbnail(bitmap: Bitmap, file: File, width: Int, height: Int) {
        val thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true)
        FileOutputStream(file).use { out ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 70, out)
        }
        thumbnail.recycle()
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
            else "%02d:%02d".format(m, s)
        }
    }
}

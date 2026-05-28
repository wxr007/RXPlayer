package com.rxplayer.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ThumbnailCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "video_thumbnails")

    init {
        cacheDir.mkdirs()
    }

    suspend fun getThumbnail(
        videoPath: String,
        maxWidth: Int = 240
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = thumbnailFile(videoPath)
        if (file.exists()) {
            return@withContext BitmapFactory.decodeFile(file.absolutePath)
        }

        if (!fileExists(videoPath)) {
            return@withContext null
        }

        val bitmap = decodeWithRetriever(videoPath, maxWidth)

        if (bitmap != null) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
        bitmap
    }

    private fun fileExists(videoPath: String): Boolean {
        return try {
            if (videoPath.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")?.use { true } ?: false
            } else {
                File(videoPath).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeWithRetriever(videoPath: String, maxWidth: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(videoPath))
            val frame = retriever.frameAtTime ?: return null
            val (newWidth, newHeight) = scaleSize(frame.width, frame.height, maxWidth)
            val scaled = Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
            frame.recycle()
            scaled
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun scaleSize(origWidth: Int, origHeight: Int, maxWidth: Int): Pair<Int, Int> {
        if (origWidth <= maxWidth) return origWidth to origHeight
        val ratio = maxWidth.toFloat() / origWidth
        return maxWidth to (origHeight * ratio).toInt()
    }

    fun getCachedPath(videoPath: String): String {
        return thumbnailFile(videoPath).absolutePath
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun thumbnailFile(videoPath: String): File {
        return File(cacheDir, "${videoPath.hashCode()}.jpg")
    }

}

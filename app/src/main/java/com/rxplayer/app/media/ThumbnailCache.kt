package com.rxplayer.app.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
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
        videoId: Long,
        videoPath: String,
        maxWidth: Int = 240
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = thumbnailFile(videoId)
        if (file.exists()) {
            return@withContext BitmapFactory.decodeFile(file.absolutePath)
        }

        val bitmap = loadSystemThumbnail(videoId)
            ?: decodeWithRetriever(videoPath, maxWidth)

        if (bitmap != null) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
        bitmap
    }

    private fun loadSystemThumbnail(videoId: Long): Bitmap? {
        return try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
            )
            context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWithRetriever(videoPath: String, maxWidth: Int): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(videoPath))
            val frame = retriever.frameAtTime ?: return null
            retriever.release()

            val (newWidth, newHeight) = scaleSize(frame.width, frame.height, maxWidth)
            val scaled = Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
            frame.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleSize(origWidth: Int, origHeight: Int, maxWidth: Int): Pair<Int, Int> {
        if (origWidth <= maxWidth) return origWidth to origHeight
        val ratio = maxWidth.toFloat() / origWidth
        return maxWidth to (origHeight * ratio).toInt()
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun thumbnailFile(videoId: Long): File {
        return File(cacheDir, "${videoId}.jpg")
    }

}

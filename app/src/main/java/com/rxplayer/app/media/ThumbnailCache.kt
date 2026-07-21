package com.rxplayer.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ThumbnailCache(private val context: Context) {

    private val cacheDir = File(context.filesDir, "video_thumbnails")

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

        var bitmap = decodeWithRetriever(videoPath, maxWidth)
        if (bitmap == null) {
            bitmap = decodeWithMediaCodec(videoPath, maxWidth)
        }

        if (bitmap != null) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
        bitmap
    }

    private fun fileExists(videoPath: String): Boolean {
        return try {
            when {
                videoPath.startsWith("content://") -> {
                    context.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")?.use { true } ?: false
                }
                videoPath.startsWith("http://") || videoPath.startsWith("https://") -> true
                else -> File(videoPath).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeWithRetriever(videoPath: String, maxWidth: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            setDataSourceUri(retriever, videoPath)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) * 1000L
            val midUs = if (durationUs > 0) durationUs / 2 else 1_000_000L
            val frame = retriever.getFrameAtTime(midUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(midUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) return null
            val (newWidth, newHeight) = scaleSize(frame.width, frame.height, maxWidth)
            val scaled = Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
            if (scaled !== frame) frame.recycle()
            return scaled
        } catch (_: Exception) {
            return null
        } finally {
            retriever.release()
        }
    }

    private fun decodeWithMediaCodec(videoPath: String, maxWidth: Int, positionUs: Long = -1L): Bitmap? {
        val extractor = MediaExtractor()
        try {
            if (videoPath.startsWith("content://")) {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")
                pfd?.use { extractor.setDataSource(it.fileDescriptor) }
            } else {
                extractor.setDataSource(videoPath)
            }
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) return null

            extractor.selectTrack(trackIndex)
            if (positionUs >= 0L) {
                extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = createSoftwareDecoder(mime) ?: MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var bitmap: Bitmap? = null
            var inputDone = false
            var timeoutCount = 0
            var bestTimeUs = 0L

            while (timeoutCount < 100) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000L)
                    if (inputIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        timeoutCount++
                        Thread.sleep(100)
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                    else -> {
                        if (outputIndex >= 0) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                                val image = codec.getOutputImage(outputIndex)
                                if (image != null) {
                                    if (positionUs < 0L || bitmap == null ||
                                        kotlin.math.abs(bufferInfo.presentationTimeUs - positionUs) <
                                        kotlin.math.abs(bestTimeUs - positionUs)
                                    ) {
                                        bitmap?.recycle()
                                        bitmap = imageToBitmap(image)
                                        bestTimeUs = bufferInfo.presentationTimeUs
                                    }
                                    image.close()
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }

                if (inputDone && bitmap != null) break
            }

            codec.stop()
            codec.release()

            if (bitmap != null && maxWidth in 1 until bitmap.width) {
                val (nw, nh) = scaleSize(bitmap.width, bitmap.height, maxWidth)
                bitmap = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
            }
            return bitmap
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    private fun createSoftwareDecoder(mime: String): MediaCodec? {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (info in codecInfos) {
            if (!info.isEncoder && info.supportedTypes?.contains(mime) == true) {
                if (info.isSoftwareDecoder()) {
                    return MediaCodec.createByCodecName(info.name)
                }
            }
        }
        return null
    }

    private fun MediaCodecInfo.isSoftwareDecoder(): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            isSoftwareOnly
        } else {
            name.startsWith("OMX.google.", ignoreCase = true) ||
            name.startsWith("c2.google.", ignoreCase = true)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) {
            val w = image.width
            val h = image.height
            val buf = image.planes[0].buffer
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            return bmp
        }
        val planes = image.planes
        val width = image.width
        val height = image.height
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yPixelStride = yPlane.pixelStride
        val yRowStride = yPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val vRowStride = vPlane.rowStride

        val pixels = IntArray(width * height)
        val uvWidth = width / 2
        val uvHeight = height / 2

        for (j in 0 until height) {
            for (i in 0 until width) {
                val yPos = j * yRowStride + i * yPixelStride
                val uvPos = (j / 2) * uRowStride + (i / 2) * uPixelStride
                val y = yBuf.get(yPos).toInt() and 0xFF
                val u = uBuf.get(uvPos).toInt() and 0xFF
                val v = vBuf.get(uvPos).toInt() and 0xFF
                val r = (y + 1.402f * (v - 128)).toInt().coerceIn(0, 255)
                val g = (y - 0.344f * (u - 128) - 0.714f * (v - 128)).toInt().coerceIn(0, 255)
                val b = (y + 1.772f * (u - 128)).toInt().coerceIn(0, 255)
                pixels[j * width + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun scaleSize(origWidth: Int, origHeight: Int, maxWidth: Int): Pair<Int, Int> {
        if (origWidth <= maxWidth) return origWidth to origHeight
        val ratio = maxWidth.toFloat() / origWidth
        return maxWidth to (origHeight * ratio).toInt()
    }

    private fun setDataSourceUri(retriever: MediaMetadataRetriever, videoPath: String) {
        if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
            retriever.setDataSource(videoPath, emptyMap())
        } else {
            retriever.setDataSource(context, Uri.parse(videoPath))
        }
    }

    suspend fun saveFrameAt(videoPath: String, positionMs: Long, maxWidth: Int = 240): Boolean = withContext(Dispatchers.IO) {
        if (!fileExists(videoPath)) return@withContext false
        var bitmap: Bitmap? = null
        val retriever = MediaMetadataRetriever()
        try {
            setDataSourceUri(retriever, videoPath)
            bitmap = retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Exception) {
        } finally {
            retriever.release()
        }
        if (bitmap == null) {
            bitmap = decodeWithMediaCodec(videoPath, maxWidth, positionMs * 1000L)
        }
        if (bitmap == null) return@withContext false
        val (newWidth, newHeight) = scaleSize(bitmap.width, bitmap.height, maxWidth)
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        val file = thumbnailFile(videoPath)
        try {
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        } catch (_: Exception) {
            scaled.recycle()
            return@withContext false
        }
        scaled.recycle()
        true
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

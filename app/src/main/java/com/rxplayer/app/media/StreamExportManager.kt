package com.rxplayer.app.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportState {
    object Idle : ExportState()
    data class Preparing(val message: String) : ExportState()
    data class Exporting(val percent: Int) : ExportState()
    data class Completed(val uri: Uri) : ExportState()
    data class Error(val message: String) : ExportState()
}

@Singleton
class StreamExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheDataSourceFactory: CacheDataSource.Factory
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val segmentUrlsDir: File get() = File(context.cacheDir, "segment_urls").also { it.mkdirs() }

    fun resetState() {
        _state.value = ExportState.Idle
    }

    suspend fun export(url: String, outputUri: Uri, streamId: Long = -1) {
        _state.value = ExportState.Preparing("正在准备导出...")
        try {
            val type = detectType(url)
            when (type) {
                StreamType.HLS -> exportHls(url, outputUri, streamId)
                StreamType.PROGRESSIVE -> exportProgressive(url, outputUri)
                StreamType.DASH -> _state.value = ExportState.Error("暂不支持DASH导出")
            }
        } catch (e: Exception) {
            _state.value = ExportState.Error("导出失败: ${e.message ?: "未知错误"}")
        }
    }

    suspend fun saveSegmentUrlsForStream(streamUrl: String, streamId: Long) = withContext(Dispatchers.IO) {
        try {
            val urls = resolveSegmentUrls(streamUrl)
            if (urls.isNotEmpty()) {
                val file = File(segmentUrlsDir, streamId.toString())
                file.writeText(urls.joinToString("\n"))
            }
        } catch (_: Exception) {
            // Silently fail — export will fall back to playlist fetching
        }
    }

    fun deleteSegmentUrlsForStream(streamId: Long) {
        File(segmentUrlsDir, streamId.toString()).delete()
    }

    private fun loadSegmentUrls(streamId: Long): List<String>? {
        val file = File(segmentUrlsDir, streamId.toString())
        if (!file.exists()) return null
        return file.readLines().filter { it.isNotBlank() }
    }

    @WorkerThread
    private fun resolveSegmentUrls(streamUrl: String): List<String> {
        var effectiveBaseUrl = streamUrl
        val playlistBytes = fetchPlaylistBytes(effectiveBaseUrl)
        val playlistContent = playlistBytes.decodeToString()
        val parser = HlsPlaylistParser()
        val playlist = parser.parse(Uri.parse(effectiveBaseUrl), ByteArrayInputStream(playlistBytes))

        val mediaPlaylist: HlsMediaPlaylist
        if (playlist is HlsMediaPlaylist) {
            mediaPlaylist = playlist
        } else if (playlistContent.contains("#EXT-X-STREAM-INF")) {
            val lines = playlistContent.lines()
            var medialUrl: String? = null
            var i = 0
            while (i < lines.size && medialUrl == null) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                            medialUrl = nextLine
                            break
                        }
                        j++
                    }
                }
                i++
            }
            val absoluteMediaUrl = resolveUrl(effectiveBaseUrl, medialUrl ?: return emptyList())
            effectiveBaseUrl = absoluteMediaUrl
            val mediaBytes = fetchPlaylistBytes(effectiveBaseUrl)
            val resolved = parser.parse(Uri.parse(effectiveBaseUrl), ByteArrayInputStream(mediaBytes))
            mediaPlaylist = resolved as? HlsMediaPlaylist ?: return emptyList()
        } else return emptyList()

        return mediaPlaylist.segments.map { resolveUrl(effectiveBaseUrl, it.url) }
    }

    private enum class StreamType { HLS, DASH, PROGRESSIVE }

    private fun detectType(url: String): StreamType = when {
        url.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
        url.contains(".mpd", ignoreCase = true) -> StreamType.DASH
        else -> StreamType.PROGRESSIVE
    }

    @WorkerThread
    private suspend fun exportProgressive(url: String, outputUri: Uri) = withContext(Dispatchers.IO) {
        _state.value = ExportState.Preparing("正在读取数据...")
        val dataSource = cacheDataSourceFactory.createDataSource()
        val dataSpec = DataSpec(Uri.parse(url))
        dataSource.open(dataSpec)

        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (dataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != C.RESULT_END_OF_INPUT) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        } ?: throw IllegalStateException("无法打开输出文件")
        dataSource.close()
        _state.value = ExportState.Completed(outputUri)
    }

    @WorkerThread
    private suspend fun exportHls(baseUrl: String, outputUri: Uri, streamId: Long = -1) = withContext(Dispatchers.IO) {
        val segmentUrls: List<String>

        if (streamId >= 0) {
            val saved = loadSegmentUrls(streamId)
            if (saved != null && saved.isNotEmpty()) {
                segmentUrls = saved
            } else {
                _state.value = ExportState.Preparing("正在解析播放列表...")
                segmentUrls = try {
                    resolveSegmentUrls(baseUrl)
                } catch (e: Exception) {
                    _state.value = ExportState.Error("解析播放列表失败(离线？): ${e.message ?: "未知错误"}")
                    return@withContext
                }
            }
        } else {
            _state.value = ExportState.Preparing("正在解析播放列表...")
            segmentUrls = try {
                resolveSegmentUrls(baseUrl)
            } catch (e: Exception) {
                _state.value = ExportState.Error("解析播放列表失败: ${e.message ?: "未知错误"}")
                return@withContext
            }
        }

        if (segmentUrls.isEmpty()) {
            _state.value = ExportState.Error("无法获取分片列表")
            return@withContext
        }

        val exportDir = File(context.cacheDir, "export_hls_${System.currentTimeMillis()}")
        exportDir.mkdirs()
        val segmentFiles = mutableListOf<File>()

        try {
            for ((i, segmentUrl) in segmentUrls.withIndex()) {
                _state.value = ExportState.Preparing("正在处理分片 ${i + 1}/${segmentUrls.size}...")
                val tempFile = File(exportDir, "seg_$i.ts")
                downloadSegmentToFile(segmentUrl, tempFile)
                segmentFiles.add(tempFile)
                _state.value = ExportState.Exporting((i + 1) * 90 / segmentUrls.size)
            }

            _state.value = ExportState.Preparing("正在合成MP4...")
            val tempMp4 = File(exportDir, "output.mp4")
            remuxTsToMp4(segmentFiles, tempMp4.absolutePath)

            context.contentResolver.openOutputStream(outputUri)?.use { os ->
                tempMp4.inputStream().use { input -> input.copyTo(os) }
                os.flush()
            } ?: throw IllegalStateException("无法打开输出文件")

            _state.value = ExportState.Completed(outputUri)
        } finally {
            exportDir.deleteRecursively()
        }
    }

    private fun resolveUrl(baseUrl: String, url: String): String {
        if (url.startsWith("http")) {
            return Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString()
        }
        val baseUri: android.net.Uri = Uri.parse(baseUrl)
        val resolved = baseUri.buildUpon().encodedPath(
            (baseUri.encodedPath?.let { p ->
                (if (p.endsWith("/")) p else p.substringBeforeLast("/") + "/")
            } ?: "/") + url
        ).clearQuery().fragment(null).build()
        return resolved.toString()
    }

    @WorkerThread
    private fun downloadSegmentToFile(url: String, target: File) {
        val dataSource = cacheDataSourceFactory.createDataSource()
        try {
            val uri = android.net.Uri.parse(url)
            val dataSpec = DataSpec(uri)
            dataSource.open(dataSpec)
            target.outputStream().use { os ->
                val buf = ByteArray(8192)
                var br: Int
                while (dataSource.read(buf, 0, buf.size).also { br = it } != C.RESULT_END_OF_INPUT) {
                    os.write(buf, 0, br)
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @WorkerThread
    private fun fetchPlaylistBytes(url: String): ByteArray {
        val dataSource = cacheDataSourceFactory.createDataSource()
        try {
            val uri = android.net.Uri.parse(url)
            val dataSpec = DataSpec(uri)
            dataSource.open(dataSpec)
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var br: Int
            while (dataSource.read(buf, 0, buf.size).also { br = it } != C.RESULT_END_OF_INPUT) {
                baos.write(buf, 0, br)
            }
            return baos.toByteArray()
        } finally {
            dataSource.close()
        }
    }

    @WorkerThread
    private fun remuxTsToMp4(segmentFiles: List<File>, outputPath: String) {
        val muxer = MediaMuxer(outputPath, 0)
        val trackMap = mutableMapOf<Int, Int>()
        var firstSegment = true
        var muxerStarted = false
        var cumulativeDurationUs = 0L

        try {
            for (segmentFile in segmentFiles) {
                if (!segmentFile.exists() || segmentFile.length() == 0L) continue

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segmentFile.absolutePath)
                    if (extractor.trackCount == 0) continue

                    if (firstSegment) {
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            trackMap[i] = muxer.addTrack(format)
                        }
                        muxer.start()
                        muxerStarted = true
                        firstSegment = false
                    } else if (!muxerStarted) {
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            trackMap[i] = muxer.addTrack(format)
                        }
                        muxer.start()
                        muxerStarted = true
                    }

                    for (i in 0 until extractor.trackCount) {
                        extractor.selectTrack(i)
                    }

                    val bufferInfo = MediaCodec.BufferInfo()
                    val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                    var segFirstPts = -1L
                    var segLastPts = -1L

                    while (true) {
                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val flags = extractor.sampleFlags
                        if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            extractor.advance()
                            continue
                        }

                        val pts = extractor.sampleTime
                        if (segFirstPts < 0) segFirstPts = pts
                        segLastPts = pts

                        val adjustedPts = pts - segFirstPts + cumulativeDurationUs
                        val trackIdx = extractor.sampleTrackIndex
                        val muxerIdx = trackMap[trackIdx]
                        if (muxerIdx == null) {
                            extractor.advance()
                            continue
                        }

                        bufferInfo.set(0, sampleSize, adjustedPts, flags)
                        muxer.writeSampleData(muxerIdx, buffer, bufferInfo)

                        extractor.advance()
                    }

                    if (segFirstPts >= 0 && segLastPts > segFirstPts) {
                        cumulativeDurationUs += segLastPts - segFirstPts
                    }
                } catch (_: Exception) {
                    // Skip invalid segment
                } finally {
                    extractor.release()
                }
            }
        } finally {
            try {
                if (muxerStarted) muxer.stop()
            } catch (_: Exception) { }
            try {
                muxer.release()
            } catch (_: Exception) { }
        }
    }
}

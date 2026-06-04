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

    fun resetState() {
        _state.value = ExportState.Idle
    }

    suspend fun export(url: String, outputUri: Uri) {
        _state.value = ExportState.Preparing("正在准备导出...")
        try {
            val type = detectType(url)
            when (type) {
                StreamType.HLS -> exportHls(url, outputUri)
                StreamType.PROGRESSIVE -> exportProgressive(url, outputUri)
                StreamType.DASH -> _state.value = ExportState.Error("暂不支持DASH导出")
            }
        } catch (e: Exception) {
            _state.value = ExportState.Error("导出失败: ${e.message ?: "未知错误"}")
        }
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
    private suspend fun exportHls(url: String, outputUri: Uri) = withContext(Dispatchers.IO) {
        _state.value = ExportState.Preparing("正在解析播放列表...")

        val playlistBytes = fetchBytes(url)
        val parser = HlsPlaylistParser()
        val playlist = parser.parse(Uri.parse(url), ByteArrayInputStream(playlistBytes))

        val mediaPlaylist: HlsMediaPlaylist =
            if (playlist is HlsMediaPlaylist) {
                playlist
            } else {
                _state.value = ExportState.Preparing("正在解析主播放列表...")
                val variantUrl = resolveFirstVariantUrl(url, playlistBytes)
                if (variantUrl == null) {
                    _state.value = ExportState.Error("主播放列表中没有可用的变体流")
                    return@withContext
                }
                _state.value = ExportState.Preparing("正在解析媒体播放列表...")
                val variantBytes = fetchBytes(variantUrl)
                val resolved = HlsPlaylistParser().parse(
                    Uri.parse(variantUrl), ByteArrayInputStream(variantBytes)
                )
                resolved as? HlsMediaPlaylist ?: run {
                    _state.value = ExportState.Error("无法解析媒体播放列表")
                    return@withContext
                }
            }

        val segments = mediaPlaylist.segments
        if (segments.isEmpty()) {
            _state.value = ExportState.Error("播放列表中没有分片")
            return@withContext
        }

        val exportDir = File(context.cacheDir, "export_hls_${System.currentTimeMillis()}")
        exportDir.mkdirs()
        val segmentFiles = mutableListOf<File>()

        try {
            for ((i, segment) in segments.withIndex()) {
                _state.value = ExportState.Preparing("正在下载分片 ${i + 1}/${segments.size}...")
                val segmentUrl = segment.url.toString()
                val tempFile = File(exportDir, "seg_$i.ts")
                downloadToFile(segmentUrl, tempFile)
                segmentFiles.add(tempFile)
                _state.value = ExportState.Exporting((i + 1) * 90 / segments.size)
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

    @WorkerThread
    private fun downloadToFile(url: String, target: File) {
        val dataSource = cacheDataSourceFactory.createDataSource()
        try {
            val dataSpec = DataSpec(Uri.parse(url))
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
    private fun fetchBytes(url: String): ByteArray {
        val dataSource = cacheDataSourceFactory.createDataSource()
        try {
            val dataSpec = DataSpec(Uri.parse(url))
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

    private fun resolveFirstVariantUrl(playlistUrl: String, playlistBytes: ByteArray): String? {
        val text = playlistBytes.decodeToString()
        val lines = text.lines()
        var inStreamInf = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                inStreamInf = true
                continue
            }
            if (inStreamInf && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                return if (trimmed.startsWith("http")) trimmed
                else playlistUrl.substringBeforeLast("/") + "/" + trimmed
            }
        }
        return null
    }

    @WorkerThread
    private fun remuxTsToMp4(segmentFiles: List<File>, outputPath: String) {
        val muxer = MediaMuxer(outputPath, 0) // MUXER_OUTPUT_MPEG4
        val trackMap = mutableMapOf<Int, Int>()
        var firstSegment = true
        var baseTimeUs = 0L

        try {
            for (segmentFile in segmentFiles) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segmentFile.absolutePath)

                    if (firstSegment) {
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            trackMap[i] = muxer.addTrack(format)
                        }
                        muxer.start()
                        firstSegment = false
                    }

                    for (i in 0 until extractor.trackCount) {
                        extractor.selectTrack(i)
                    }

                    val bufferInfo = MediaCodec.BufferInfo()
                    val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                    var segmentMaxPts = 0L

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
                        val trackIdx = extractor.sampleTrackIndex
                        val muxerIdx = trackMap[trackIdx]
                        if (muxerIdx == null) {
                            extractor.advance()
                            continue
                        }

                        bufferInfo.set(0, sampleSize, pts + baseTimeUs, flags)
                        muxer.writeSampleData(muxerIdx, buffer, bufferInfo)
                        segmentMaxPts = maxOf(segmentMaxPts, pts)

                        extractor.advance()
                    }

                    if (segmentMaxPts > 0) {
                        baseTimeUs += segmentMaxPts
                    }
                } finally {
                    extractor.release()
                }
            }
        } finally {
            muxer.stop()
            muxer.release()
        }
    }
}

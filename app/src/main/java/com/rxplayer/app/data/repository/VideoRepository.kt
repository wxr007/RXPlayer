package com.rxplayer.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Video.Media
import androidx.documentfile.provider.DocumentFile
import com.rxplayer.app.data.db.FolderDao
import com.rxplayer.app.data.db.VideoDao
import com.rxplayer.app.data.db.VideoEntity
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.data.model.VideoFolder
import com.rxplayer.app.media.ThumbnailCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val folderDao: FolderDao
) {
    private val thumbnailCache = ThumbnailCache(context)

    fun observeFolders(): Flow<List<VideoFolder>> {
        return folderDao.getAllFolders().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun syncFolders() {
        val modeMap = folderDao.getAllFoldersSnapshot().associate { it.path to it.displayMode }
        val orientationMap = folderDao.getAllFoldersSnapshot().associate { it.path to it.thumbnailOrientation }
        val autoFullscreenMap = folderDao.getAllFoldersSnapshot().associate { it.path to it.autoFullscreen }
        val playbackModeMap = folderDao.getAllFoldersSnapshot().associate { it.path to it.playbackMode }
        val mediaStoreFolders = getVideoFolders()
        val safUris = getSavedSafUris()
        val safFolders = safUris.mapNotNull { scanSafFolder(it) }
        val allFolders = (mediaStoreFolders + safFolders).map { it.toEntity() }
        folderDao.insertAll(allFolders)
        modeMap.forEach { (path, mode) ->
            if (mode != 0) folderDao.updateDisplayMode(path, mode)
        }
        orientationMap.forEach { (path, orientation) ->
            if (orientation != 0) folderDao.updateThumbnailOrientation(path, orientation)
        }
        autoFullscreenMap.forEach { (path, enabled) ->
            if (enabled != 0) folderDao.updateAutoFullscreen(path, enabled)
        }
        playbackModeMap.forEach { (path, mode) ->
            if (mode != 0) folderDao.updatePlaybackMode(path, mode)
        }
    }

    suspend fun insertFolder(folder: VideoFolder) {
        folderDao.insertAll(listOf(folder.toEntity()))
    }

    suspend fun deleteFolder(folderPath: String) {
        folderDao.deleteByPath(folderPath)
    }

    suspend fun getDisplayMode(folderPath: String): Int {
        return folderDao.getByPath(folderPath)?.displayMode ?: 0
    }

    suspend fun setDisplayMode(folderPath: String, mode: Int) {
        folderDao.updateDisplayMode(folderPath, mode)
    }

    suspend fun getGridColumns(folderPath: String): Int {
        return folderDao.getByPath(folderPath)?.gridColumns ?: 3
    }

    suspend fun setGridColumns(folderPath: String, columns: Int) {
        folderDao.updateGridColumns(folderPath, columns)
    }

    suspend fun getSortSettings(folderPath: String): Pair<String, Int> {
        val entity = folderDao.getByPath(folderPath)
        return Pair(entity?.sortBy ?: "date", entity?.sortAscending ?: 0)
    }

    suspend fun setSort(folderPath: String, sortBy: String, ascending: Int) {
        folderDao.updateSort(folderPath, sortBy, ascending)
    }

    suspend fun getThumbnailOrientation(folderPath: String): Int {
        return folderDao.getByPath(folderPath)?.thumbnailOrientation ?: 0
    }

    suspend fun setThumbnailOrientation(folderPath: String, orientation: Int) {
        folderDao.updateThumbnailOrientation(folderPath, orientation)
    }

    suspend fun getAutoFullscreen(folderPath: String): Int {
        return folderDao.getByPath(folderPath)?.autoFullscreen ?: 0
    }

    suspend fun setAutoFullscreen(folderPath: String, enabled: Int) {
        folderDao.updateAutoFullscreen(folderPath, enabled)
    }

    suspend fun getPlaybackMode(folderPath: String): Int {
        return folderDao.getByPath(folderPath)?.playbackMode ?: 0
    }

    suspend fun setPlaybackMode(folderPath: String, mode: Int) {
        folderDao.updatePlaybackMode(folderPath, mode)
    }

    suspend fun scanSafFolderWithProgress(
        safUri: String,
        onProgress: (Float) -> Unit
    ): VideoFolder? {
        val uri = Uri.parse(safUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null
        val videoFiles = mutableListOf<DocumentFile>()
        scanVideoFiles(root, videoFiles)
        val total = videoFiles.size.coerceAtLeast(1)

        val coverPaths = mutableListOf<String>()
        val videoEntities = mutableListOf<VideoEntity>()
        videoFiles.forEachIndexed { index, file ->
            val videoPath = file.uri.toString()
            if (coverPaths.size < 4) {
                withContext(Dispatchers.IO) {
                    thumbnailCache.getThumbnail(videoPath)
                }
                coverPaths.add(thumbnailCache.getCachedPath(videoPath))
            }
            videoEntities.add(
                VideoEntity(
                    id = index.toLong(),
                    folderPath = safUri,
                    fileName = file.name ?: "unknown",
                    filePath = videoPath,
                    duration = 0L,
                    fileSize = file.length() ?: 0L,
                    resolution = "",
                    mimeType = file.type ?: "video/mp4",
                    addedAt = file.lastModified()
                )
            )
            onProgress((index + 1).toFloat() / total * 0.9f)
        }

        val name = root.name ?: safFolderDisplayName(safUri)
        onProgress(0.92f)
        withContext(Dispatchers.IO) {
            videoDao.replaceFolder(safUri, videoEntities)
        }
        val folder = VideoFolder(
            name = name,
            path = safUri,
            videoCount = videoFiles.size,
            coverPaths = coverPaths,
            addedAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) {
            folderDao.insertAll(listOf(folder.toEntity()))
        }
        onProgress(1f)
        return folder
    }

    private suspend fun getVideoFolders(): List<VideoFolder> {
        val folders = mutableMapOf<String, MutableList<String>>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(Media.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataIndex)
                val parent = path.substringBeforeLast("/")
                folders.getOrPut(parent) { mutableListOf() }.add(path)
            }
        }
        return folders.map { (path, files) ->
            val coverPaths = files.take(4).map { videoPath ->
                thumbnailCache.getThumbnail(videoPath)
                thumbnailCache.getCachedPath(videoPath)
            }
            VideoFolder(
                name = path.substringAfterLast("/"),
                path = path,
                videoCount = files.size,
                coverPaths = coverPaths,
                addedAt = System.currentTimeMillis()
            )
        }
    }

    private fun getSavedSafUris(): List<String> {
        val prefs = context.getSharedPreferences("saf_folders", Context.MODE_PRIVATE)
        return prefs.getStringSet("uris", emptySet())?.toList() ?: emptyList()
    }

    fun observeVideosInFolder(folderPath: String): Flow<List<Video>> {
        return videoDao.getVideosInFolder(folderPath).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun getVideosInFolderSnapshot(folderPath: String): List<Video> {
        return videoDao.getVideosInFolderSnapshot(folderPath).map { it.toModel() }
    }

    suspend fun syncFolderFromMediaStore(folderPath: String) {
        val videos = if (folderPath.startsWith("content://")) {
            querySafFolder(folderPath)
        } else {
            queryMediaStore(folderPath)
        }
        val entities = videos.map { it.toEntity(folderPath) }
        videoDao.replaceFolder(folderPath, entities)
        // Pre-generate thumbnails so UI loads from cache immediately
        videos.take(4).forEach { video ->
            thumbnailCache.getThumbnail(video.filePath)
        }
        val coverPaths = videos.take(4).map { thumbnailCache.getCachedPath(it.filePath) }
        folderDao.updateCoverPaths(folderPath, coverPaths.joinToString("\n"))
    }

    private suspend fun scanSafFolder(safUri: String): VideoFolder? {
        val uri = Uri.parse(safUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null
        val videoFiles = mutableListOf<DocumentFile>()
        scanVideoFiles(root, videoFiles)
        val name = root.name ?: safFolderDisplayName(safUri)
        val coverPaths = videoFiles.take(4).map { file ->
            val videoPath = file.uri.toString()
            thumbnailCache.getThumbnail(videoPath)
            thumbnailCache.getCachedPath(videoPath)
        }
        return VideoFolder(
            name = name,
            path = safUri,
            videoCount = videoFiles.size,
            coverPaths = coverPaths,
            addedAt = System.currentTimeMillis()
        )
    }

    private fun querySafFolder(safUri: String): List<Video> {
        val uri = Uri.parse(safUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        val videoFiles = mutableListOf<DocumentFile>()
        scanVideoFiles(root, videoFiles)
        return videoFiles.mapIndexed { index, file ->
            val (duration, width, height) = getVideoInfo(file.uri)
            Video(
                id = index.toLong(),
                folderPath = safUri,
                fileName = file.name ?: "unknown",
                filePath = file.uri.toString(),
                duration = duration,
                fileSize = file.length(),
                resolution = if (width > 0 && height > 0) "${width}x${height}" else "",
                mimeType = file.type ?: "video/mp4",
                addedAt = file.lastModified()
            )
        }
    }

    private fun getVideoInfo(uri: Uri): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            Triple(duration, width, height)
        } catch (_: Exception) {
            Triple(0L, 0, 0)
        } finally {
            retriever.release()
        }
    }

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts"
    )

    private fun scanVideoFiles(dir: DocumentFile, result: MutableList<DocumentFile>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanVideoFiles(file, result)
            } else if (file.isFile) {
                val mimeType = file.type
                val ext = file.name?.substringAfterLast('.', "")?.lowercase()
                if (mimeType?.startsWith("video/") == true || ext in videoExtensions) {
                    result.add(file)
                }
            }
        }
    }

    private fun queryMediaStore(folderPath: String): List<Video> {
        val videos = mutableListOf<Video>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            Media._ID, Media.DATA, Media.DISPLAY_NAME,
            Media.DURATION, Media.SIZE, Media.MIME_TYPE, Media.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(Media.WIDTH)
            projection.add(Media.HEIGHT)
        }
        val escapedPath = folderPath.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%")
        val selection = "${Media.DATA} LIKE ? ESCAPE '\\'"
        val selectionArgs = arrayOf("$escapedPath/%")
        context.contentResolver.query(uri, projection.toTypedArray(), selection, selectionArgs, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Media._ID)
            val dataIdx = cursor.getColumnIndexOrThrow(Media.DATA)
            val nameIdx = cursor.getColumnIndexOrThrow(Media.DISPLAY_NAME)
            val durIdx = cursor.getColumnIndexOrThrow(Media.DURATION)
            val sizeIdx = cursor.getColumnIndexOrThrow(Media.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(Media.MIME_TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)
            val widthIdx = cursor.getColumnIndex(Media.WIDTH)
            val heightIdx = cursor.getColumnIndex(Media.HEIGHT)
            while (cursor.moveToNext()) {
                val w = if (widthIdx >= 0) cursor.getInt(widthIdx) else 0
                val h = if (heightIdx >= 0) cursor.getInt(heightIdx) else 0
                videos.add(
                    Video(
                        id = cursor.getLong(idIdx),
                        folderPath = folderPath,
                        fileName = cursor.getString(nameIdx),
                        filePath = cursor.getString(dataIdx),
                        duration = cursor.getLong(durIdx),
                        fileSize = cursor.getLong(sizeIdx),
                        resolution = if (w > 0 && h > 0) "${w}x${h}" else "",
                        mimeType = cursor.getString(mimeIdx),
                        addedAt = cursor.getLong(dateIdx) * 1000
                    )
                )
            }
        }
        return videos
    }
}

private fun safFolderDisplayName(safUri: String): String {
    val lastSegment = Uri.parse(safUri).lastPathSegment ?: return safUri.substringAfterLast("/")
    val path = lastSegment.substringAfter(":")
    return path.substringAfterLast("/").ifEmpty { path }
}

private fun Video.toEntity(folderPath: String) = VideoEntity(
    id = id,
    folderPath = folderPath,
    fileName = fileName,
    filePath = filePath,
    duration = duration,
    fileSize = fileSize,
    resolution = resolution,
    mimeType = mimeType,
    addedAt = addedAt
)

private fun VideoEntity.toModel() = Video(
    id = id,
    folderPath = folderPath,
    fileName = fileName,
    filePath = filePath,
    duration = duration,
    fileSize = fileSize,
    resolution = resolution,
    mimeType = mimeType,
    addedAt = addedAt
)

private fun VideoFolder.toEntity() = com.rxplayer.app.data.db.FolderEntity(
    path = path,
    name = name,
    videoCount = videoCount,
    coverPaths = coverPaths.joinToString("\n"),
    addedAt = addedAt,
    displayMode = displayMode,
    gridColumns = gridColumns,
    sortBy = sortBy,
    sortAscending = sortAscending,
    thumbnailOrientation = thumbnailOrientation,
    autoFullscreen = autoFullscreen,
    playbackMode = playbackMode
)

private fun com.rxplayer.app.data.db.FolderEntity.toModel() = VideoFolder(
    name = name,
    path = path,
    videoCount = videoCount,
    coverPaths = coverPaths.split("\n").filter { it.isNotEmpty() },
    addedAt = addedAt,
    displayMode = displayMode,
    gridColumns = gridColumns,
    sortBy = sortBy,
    sortAscending = sortAscending,
    thumbnailOrientation = thumbnailOrientation,
    autoFullscreen = autoFullscreen,
    playbackMode = playbackMode
)

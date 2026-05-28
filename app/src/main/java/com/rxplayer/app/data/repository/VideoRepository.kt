package com.rxplayer.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Video.Media
import androidx.documentfile.provider.DocumentFile
import com.rxplayer.app.data.db.VideoDao
import com.rxplayer.app.data.db.VideoEntity
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.data.model.VideoFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao
) {
    fun getVideoFolders(): List<VideoFolder> {
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
            VideoFolder(
                name = path.substringAfterLast("/"),
                path = path,
                videoCount = files.size,
                coverPaths = files.take(4),
                addedAt = System.currentTimeMillis()
            )
        }
    }

    fun observeVideosInFolder(folderPath: String): Flow<List<Video>> {
        return videoDao.getVideosInFolder(folderPath).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun syncFolderFromMediaStore(folderPath: String) {
        val videos = if (folderPath.startsWith("content://")) {
            querySafFolder(folderPath)
        } else {
            queryMediaStore(folderPath)
        }
        val entities = videos.map { it.toEntity(folderPath) }
        videoDao.replaceFolder(folderPath, entities)
    }

    fun scanSafFolder(safUri: String): VideoFolder? {
        val uri = Uri.parse(safUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null
        val videoFiles = mutableListOf<DocumentFile>()
        scanVideoFiles(root, videoFiles)
        val name = uri.lastPathSegment ?: root.name ?: safUri.substringAfterLast("/")
        return VideoFolder(
            name = name,
            path = safUri,
            videoCount = videoFiles.size,
            coverPaths = videoFiles.take(4).map { it.uri.toString() },
            addedAt = System.currentTimeMillis()
        )
    }

    private fun querySafFolder(safUri: String): List<Video> {
        val uri = Uri.parse(safUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        val videoFiles = mutableListOf<DocumentFile>()
        scanVideoFiles(root, videoFiles)
        return videoFiles.mapIndexed { index, file ->
            Video(
                id = index.toLong(),
                folderPath = safUri,
                fileName = file.name ?: "unknown",
                filePath = file.uri.toString(),
                duration = getDuration(file.uri),
                fileSize = file.length(),
                resolution = "",
                mimeType = file.type ?: "video/mp4",
                addedAt = file.lastModified()
            )
        }
    }

    private fun getDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts"
    )

    private fun scanVideoFiles(dir: DocumentFile, result: MutableList<DocumentFile>) {
        val files = dir.listFiles()
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
        val projection = arrayOf(
            Media._ID, Media.DATA, Media.DISPLAY_NAME,
            Media.DURATION, Media.SIZE, Media.MIME_TYPE, Media.DATE_ADDED
        )
        val selection = "${Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Media._ID)
            val dataIdx = cursor.getColumnIndexOrThrow(Media.DATA)
            val nameIdx = cursor.getColumnIndexOrThrow(Media.DISPLAY_NAME)
            val durIdx = cursor.getColumnIndexOrThrow(Media.DURATION)
            val sizeIdx = cursor.getColumnIndexOrThrow(Media.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(Media.MIME_TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataIdx)
                videos.add(
                    Video(
                        id = cursor.getLong(idIdx),
                        folderPath = folderPath,
                        fileName = cursor.getString(nameIdx),
                        filePath = filePath,
                        duration = cursor.getLong(durIdx),
                        fileSize = cursor.getLong(sizeIdx),
                        resolution = "",
                        mimeType = cursor.getString(mimeIdx),
                        addedAt = cursor.getLong(dateIdx) * 1000
                    )
                )
            }
        }
        return videos
    }
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

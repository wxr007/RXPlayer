package com.rxplayer.app.data.model

import android.net.Uri

sealed class MediaCollection {
    abstract val id: String
    abstract val name: String
    abstract val videoCount: Int
    abstract val coverPaths: List<String>
    abstract val subtitle: String

    data class Folder(
        val folder: VideoFolder
    ) : MediaCollection() {
        override val id get() = "folder_${folder.path}"
        override val name get() = folder.name
        override val videoCount get() = folder.videoCount
        override val coverPaths get() = folder.coverPaths
        override val subtitle get() = formatFolderPathForDisplay(folder.path)
    }

    data class Playlist(
        val playlistId: Long,
        override val name: String,
        override val videoCount: Int,
        override val coverPaths: List<String>,
        val createdAt: Long
    ) : MediaCollection() {
        override val id get() = "playlist_$playlistId"
        override val subtitle get() = formatDateForDisplay(createdAt)
    }
}

fun formatFolderPathForDisplay(path: String): String {
    if (path.startsWith("content://")) {
        val lastSegment = Uri.parse(path).lastPathSegment ?: return path
        val decoded = lastSegment.substringAfter(":")
        return Uri.decode(decoded)
    }
    return path
}

fun formatDateForDisplay(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

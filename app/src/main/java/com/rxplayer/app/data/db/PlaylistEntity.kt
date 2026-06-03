package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val displayMode: Int = 1,
    val gridColumns: Int = 3,
    val sortBy: String = "date",
    val sortAscending: Int = 0,
    val thumbnailOrientation: Int = 0,
    val autoFullscreen: Int = 0,
    val playbackMode: Int = 0,
    val coverPaths: String = "",
    val privacyMask: Int = 0
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "filePath"]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val filePath: String,
    val addedAt: Long
)

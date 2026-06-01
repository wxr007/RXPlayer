package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "filePath"]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val filePath: String,
    val videoName: String,
    val duration: Long,
    val resolution: String,
    val addedAt: Long
)

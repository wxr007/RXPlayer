package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: Long,
    val folderPath: String,
    val fileName: String,
    val filePath: String,
    val duration: Long,
    val fileSize: Long,
    val resolution: String,
    val mimeType: String,
    val addedAt: Long
)

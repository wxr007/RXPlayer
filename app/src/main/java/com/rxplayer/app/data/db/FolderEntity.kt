package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    val videoCount: Int,
    val coverPaths: String,
    val addedAt: Long
)

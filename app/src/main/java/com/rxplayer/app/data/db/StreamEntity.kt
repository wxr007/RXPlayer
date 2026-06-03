package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streams")
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val addedAt: Long,
    val cachedPath: String = "",
    val coverPath: String = "",
    val resolution: String = "",
    val codec: String = "",
    val frameRate: String = "",
    val durationMs: Long = 0L
)

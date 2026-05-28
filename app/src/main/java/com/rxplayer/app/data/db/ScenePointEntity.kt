package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scene_points")
data class ScenePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoPath: String,
    val timestampMs: Long,
    val thumbnailPath: String,
    val sceneIndex: Int
)

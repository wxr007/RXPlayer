package com.rxplayer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val id: Long = 0,
    val videoId: Long,
    val position: Long,
    val lastPlayedAt: Long,
    val progressPercent: Int
)

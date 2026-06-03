package com.rxplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDao {
    @Query("SELECT * FROM streams ORDER BY addedAt DESC")
    fun getAllStreams(): Flow<List<StreamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: StreamEntity): Long

    @Query("DELETE FROM streams WHERE id = :streamId")
    suspend fun deleteStreamById(streamId: Long)

    @Query("UPDATE streams SET coverPath = :coverPath WHERE id = :streamId")
    suspend fun updateCoverPath(streamId: Long, coverPath: String)

    @Query("UPDATE streams SET cachedPath = :cachedPath WHERE id = :streamId")
    suspend fun updateCachedPath(streamId: Long, cachedPath: String)

    @Query("SELECT * FROM streams WHERE id = :streamId")
    suspend fun getStreamById(streamId: Long): StreamEntity?

    @Query("UPDATE streams SET name = :name WHERE id = :streamId")
    suspend fun updateName(streamId: Long, name: String)

    @Query("UPDATE streams SET resolution = :resolution, codec = :codec, frameRate = :frameRate, durationMs = :durationMs WHERE id = :streamId")
    suspend fun updateVideoInfo(streamId: Long, resolution: String, codec: String, frameRate: String, durationMs: Long)
}

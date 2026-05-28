package com.rxplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenePointDao {
    @Query("SELECT * FROM scene_points WHERE videoPath = :videoPath ORDER BY timestampMs ASC")
    fun getScenesForVideo(videoPath: String): Flow<List<ScenePointEntity>>

    @Query("SELECT COUNT(*) FROM scene_points WHERE videoPath = :videoPath")
    suspend fun getSceneCount(videoPath: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scenes: List<ScenePointEntity>)

    @Query("DELETE FROM scene_points WHERE videoPath = :videoPath")
    suspend fun deleteScenesForVideo(videoPath: String)
}

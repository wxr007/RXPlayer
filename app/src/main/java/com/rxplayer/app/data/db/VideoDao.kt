package com.rxplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos WHERE folderPath = :folderPath ORDER BY fileName ASC")
    fun getVideosInFolder(folderPath: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE folderPath = :folderPath")
    suspend fun getVideosInFolderSnapshot(folderPath: String): List<VideoEntity>

    @Query("SELECT COUNT(*) FROM videos WHERE folderPath = :folderPath")
    suspend fun countInFolder(folderPath: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE folderPath = :folderPath")
    suspend fun deleteFolder(folderPath: String)

    @Transaction
    suspend fun replaceFolder(folderPath: String, videos: List<VideoEntity>) {
        deleteFolder(folderPath)
        insertAll(videos)
    }
}

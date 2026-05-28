package com.rxplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY addedAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE `path` = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

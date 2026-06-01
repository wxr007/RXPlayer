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

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersSnapshot(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE `path` = :path LIMIT 1")
    suspend fun getByPath(path: String): FolderEntity?

    @Query("UPDATE folders SET displayMode = :mode WHERE `path` = :path")
    suspend fun updateDisplayMode(path: String, mode: Int)

    @Query("UPDATE folders SET gridColumns = :columns WHERE `path` = :path")
    suspend fun updateGridColumns(path: String, columns: Int)

    @Query("UPDATE folders SET sortBy = :sortBy, sortAscending = :ascending WHERE `path` = :path")
    suspend fun updateSort(path: String, sortBy: String, ascending: Int)

    @Query("UPDATE folders SET thumbnailOrientation = :orientation WHERE `path` = :path")
    suspend fun updateThumbnailOrientation(path: String, orientation: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE `path` = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

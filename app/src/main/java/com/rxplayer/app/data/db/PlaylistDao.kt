package com.rxplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val videoCount: Int,
    val coverPaths: String
)

data class PlaylistVideoJoined(
    val filePath: String,
    val addedAt: Long,
    val fileName: String,
    val duration: Long,
    val fileSize: Long,
    val resolution: String,
    val mimeType: String
)

@Dao
interface PlaylistDao {
    @Query("""
        SELECT p.id, p.name, p.createdAt,
        (SELECT COUNT(*) FROM playlist_videos pv WHERE pv.playlistId = p.id) AS videoCount,
        p.coverPaths
        FROM playlists p ORDER BY p.createdAt DESC
    """)
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("""
        SELECT pv.filePath, pv.addedAt,
        COALESCE(MIN(v.fileName), '') AS fileName,
        COALESCE(MIN(v.duration), 0) AS duration,
        COALESCE(MIN(v.fileSize), 0) AS fileSize,
        COALESCE(MIN(v.resolution), '') AS resolution,
        COALESCE(MIN(v.mimeType), '') AS mimeType
        FROM playlist_videos pv
        LEFT JOIN videos v ON pv.filePath = v.filePath
        WHERE pv.playlistId = :playlistId
        GROUP BY pv.filePath
        ORDER BY pv.addedAt ASC
    """)
    fun getVideosInPlaylist(playlistId: Long): Flow<List<PlaylistVideoJoined>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addVideoToPlaylist(video: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND filePath = :filePath")
    suspend fun removeVideoFromPlaylist(playlistId: Long, filePath: String)

    @Query("UPDATE playlists SET displayMode = :mode WHERE id = :playlistId")
    suspend fun updateDisplayMode(playlistId: Long, mode: Int)

    @Query("UPDATE playlists SET gridColumns = :columns WHERE id = :playlistId")
    suspend fun updateGridColumns(playlistId: Long, columns: Int)

    @Query("UPDATE playlists SET sortBy = :sortBy, sortAscending = :ascending WHERE id = :playlistId")
    suspend fun updateSort(playlistId: Long, sortBy: String, ascending: Int)

    @Query("UPDATE playlists SET thumbnailOrientation = :orientation WHERE id = :playlistId")
    suspend fun updateThumbnailOrientation(playlistId: Long, orientation: Int)

    @Query("UPDATE playlists SET autoFullscreen = :enabled WHERE id = :playlistId")
    suspend fun updateAutoFullscreen(playlistId: Long, enabled: Int)

    @Query("UPDATE playlists SET playbackMode = :mode WHERE id = :playlistId")
    suspend fun updatePlaybackMode(playlistId: Long, mode: Int)

    @Query("UPDATE playlists SET coverPaths = :coverPaths WHERE id = :playlistId")
    suspend fun updateCoverPaths(playlistId: Long, coverPaths: String)

    @Query("SELECT coverPaths FROM playlists WHERE id = :playlistId")
    suspend fun getCoverPaths(playlistId: Long): String

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT filePath FROM playlist_videos WHERE playlistId = :playlistId ORDER BY addedAt ASC LIMIT 4")
    suspend fun getFirstVideoPaths(playlistId: Long): List<String>
}

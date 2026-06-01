package com.rxplayer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val videoCount: Int
)

@Dao
interface PlaylistDao {
    @Query("""
        SELECT p.id, p.name, p.createdAt,
        (SELECT COUNT(*) FROM playlist_videos pv WHERE pv.playlistId = p.id) AS videoCount
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

    @Query("SELECT pv.* FROM playlist_videos pv WHERE pv.playlistId = :playlistId ORDER BY pv.addedAt ASC")
    fun getVideosInPlaylist(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addVideoToPlaylist(video: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND filePath = :filePath")
    suspend fun removeVideoFromPlaylist(playlistId: Long, filePath: String)
}

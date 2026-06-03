package com.rxplayer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlayHistoryEntity::class,
        ScenePointEntity::class,
        VideoEntity::class,
        FolderEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        StreamEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scenePointDao(): ScenePointDao
    abstract fun videoDao(): VideoDao
    abstract fun folderDao(): FolderDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun streamDao(): StreamDao
}

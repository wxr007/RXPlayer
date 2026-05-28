package com.rxplayer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlayHistoryEntity::class,
        FavoriteEntity::class,
        ScenePointEntity::class,
        VideoEntity::class,
        FolderEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scenePointDao(): ScenePointDao
    abstract fun videoDao(): VideoDao
    abstract fun folderDao(): FolderDao
}

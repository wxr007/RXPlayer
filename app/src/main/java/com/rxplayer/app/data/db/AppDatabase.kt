package com.rxplayer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlayHistoryEntity::class,
        FavoriteEntity::class,
        ScenePointEntity::class,
        VideoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scenePointDao(): ScenePointDao
    abstract fun videoDao(): VideoDao
}

package com.rxplayer.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rxplayer.app.data.db.AppDatabase
import com.rxplayer.app.data.db.ScenePointDao
import com.rxplayer.app.data.db.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "rxplayer.db"
        ).addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideScenePointDao(database: AppDatabase): ScenePointDao {
        return database.scenePointDao()
    }

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `videos` (
                    `id` INTEGER NOT NULL,
                    `folderPath` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL DEFAULT 0,
                    `fileSize` INTEGER NOT NULL DEFAULT 0,
                    `resolution` TEXT NOT NULL DEFAULT '',
                    `mimeType` TEXT NOT NULL DEFAULT '',
                    `addedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )"""
            )
        }
    }
}

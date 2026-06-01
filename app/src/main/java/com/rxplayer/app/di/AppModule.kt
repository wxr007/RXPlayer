package com.rxplayer.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rxplayer.app.data.db.AppDatabase
import com.rxplayer.app.data.db.FolderDao
import com.rxplayer.app.data.db.PlaylistDao
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao {
        return database.folderDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `playlist_videos` (
                    `playlistId` INTEGER NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `videoName` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL DEFAULT 0,
                    `resolution` TEXT NOT NULL DEFAULT '',
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `filePath`)
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `playbackMode` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `autoFullscreen` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `thumbnailOrientation` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `displayMode` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `gridColumns` INTEGER NOT NULL DEFAULT 4")
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `sortBy` TEXT NOT NULL DEFAULT 'name'")
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `sortAscending` INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `folders` (
                    `path` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `videoCount` INTEGER NOT NULL DEFAULT 0,
                    `coverPaths` TEXT NOT NULL DEFAULT '',
                    `addedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`path`)
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `videos_new` (
                    `id` INTEGER NOT NULL,
                    `folderPath` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL DEFAULT 0,
                    `fileSize` INTEGER NOT NULL DEFAULT 0,
                    `resolution` TEXT NOT NULL DEFAULT '',
                    `mimeType` TEXT NOT NULL DEFAULT '',
                    `addedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`, `folderPath`)
                )
            """.trimIndent())
            db.execSQL("INSERT INTO `videos_new` SELECT * FROM `videos`")
            db.execSQL("DROP TABLE `videos`")
            db.execSQL("ALTER TABLE `videos_new` RENAME TO `videos`")
        }
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

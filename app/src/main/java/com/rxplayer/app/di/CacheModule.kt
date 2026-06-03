package com.rxplayer.app.di

import android.content.Context
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideExoDatabaseProvider(@ApplicationContext context: Context): ExoDatabaseProvider {
        return ExoDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext context: Context,
        databaseProvider: ExoDatabaseProvider
    ): SimpleCache {
        val cacheDir = File(context.cacheDir, "exoplayer_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    fun provideUpstreamDataSourceFactory(@ApplicationContext context: Context): DataSource.Factory {
        return DefaultDataSource.Factory(context)
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: SimpleCache,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: ExoDatabaseProvider,
        cache: SimpleCache,
        upstreamFactory: DataSource.Factory
    ): DownloadManager {
        val downloadIndex = DefaultDownloadIndex(databaseProvider, "downloads")
        val downloadCacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
        val downloaderFactory = DefaultDownloaderFactory(downloadCacheFactory)
        return DownloadManager(context, downloadIndex, downloaderFactory)
    }
}

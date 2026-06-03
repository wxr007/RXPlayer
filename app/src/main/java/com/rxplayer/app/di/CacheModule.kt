package com.rxplayer.app.di

import android.content.Context
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
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
    fun provideCacheDataSourceFactory(cache: SimpleCache): CacheDataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
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
        cache: SimpleCache
    ): DownloadManager {
        val upstreamFactory = DefaultHttpDataSource.Factory()
        return DownloadManager(
            context, databaseProvider, cache, upstreamFactory,
            Executors.newSingleThreadExecutor()
        )
    }
}

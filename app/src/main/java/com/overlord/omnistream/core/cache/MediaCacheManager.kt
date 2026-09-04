package com.overlord.omnistream.core.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_SIZE: Long = 1024 * 1024 * 512 // 512 MB LRU 快取

    @Synchronized
    fun init(context: Context) {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "omni_media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
    }

    fun getCache(): SimpleCache {
        return simpleCache ?: throw IllegalStateException("MediaCacheManager not initialized")
    }

    /**
     * 建立支援邊播邊緩存的 DataSource.Factory
     */
    fun createCacheDataSourceFactory(
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

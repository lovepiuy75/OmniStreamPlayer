package com.overlord.omnistream

import android.app.Application
import com.overlord.omnistream.core.cache.MediaCacheManager
import com.overlord.omnistream.data.local.AppDatabase
import com.overlord.omnistream.data.repository.PlayerRepository

class OmniStreamApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: PlayerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. 初始化資料庫
        database = AppDatabase.getInstance(this)

        // 2. 初始化儲存庫
        repository = PlayerRepository(this, database)

        // 3. 初始化 ExoPlayer 快取管理器（邊播邊快取，LRU 磁碟緩存）
        MediaCacheManager.init(this)
    }

    companion object {
        lateinit var instance: OmniStreamApp
            private set
    }
}

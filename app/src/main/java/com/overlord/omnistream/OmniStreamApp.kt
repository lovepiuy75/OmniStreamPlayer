package com.overlord.omnistream

import android.app.Application
import android.util.Log
import com.overlord.omnistream.core.CrashHandler
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

        // 0. 安裝全域崩潰攔截器 (防止靜默閃退，提供可視化診斷介面)
        CrashHandler.init(this)

        try {
            // 1. 初始化資料庫
            database = AppDatabase.getInstance(this)

            // 2. 初始化儲存庫
            repository = PlayerRepository(this, database)

            // 3. 初始化 ExoPlayer 快取管理器（邊播邊快取，LRU 磁碟緩存）
            MediaCacheManager.init(this)
        } catch (t: Throwable) {
            Log.e("OmniStreamApp", "Fatal initialization error caught safely", t)
        }
    }

    companion object {
        lateinit var instance: OmniStreamApp
            private set
    }
}


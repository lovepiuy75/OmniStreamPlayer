package com.overlord.omnistream.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.overlord.omnistream.OmniStreamApp
import com.overlord.omnistream.core.cache.MediaCacheManager
import com.overlord.omnistream.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 支援關閉螢幕背景播放的核心 Media3 前台服務
 */
@OptIn(UnstableApi::class)
class OmniMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as OmniStreamApp

        // 1. 配置支援 Bearer Token 注入與邊播邊緩存的 DataSource
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirect(true)
            .setDefaultRequestProperties(
                buildMap {
                    app.repository.gdriveService.currentAccessToken?.let {
                        put("Authorization", "Bearer $it")
                    }
                }
            )

        val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val cacheDataSourceFactory = MediaCacheManager.createCacheDataSourceFactory(upstreamFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        // 2. 建立 ExoPlayer 實例並設置音訊屬性
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus: 自動處理音訊焦點（來電暫停、通話結束續播）
            )
            .setWakeMode(C.WAKE_MODE_NETWORK) // 鎖屏時保持網路與 CPU 運作
            .build()

        // 3. 監聽播放進度以支援跨啟動斷點續播
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveCurrentPlaybackProgress()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveCurrentPlaybackProgress()
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    progressTrackerJob?.cancel()
                }
            }
        })

        // 4. 點擊通知欄時跳轉回 MainActivity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = serviceScope.launch {
            while (isActive) {
                delay(3000) // 每 3 秒定期保存進度
                saveCurrentPlaybackProgress()
            }
        }
    }

    private fun saveCurrentPlaybackProgress() {
        val currentItem = exoPlayer.currentMediaItem ?: return
        val currentPosition = exoPlayer.currentPosition
        val isPlaying = exoPlayer.isPlaying
        val app = applicationContext as OmniStreamApp
        serviceScope.launch(Dispatchers.IO) {
            app.repository.savePlaybackState(currentItem.mediaId, currentPosition, isPlaying)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveCurrentPlaybackProgress()
        progressTrackerJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

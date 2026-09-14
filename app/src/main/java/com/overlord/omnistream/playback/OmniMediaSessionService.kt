package com.overlord.omnistream.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import kotlinx.coroutines.withContext

/**
 * 支援關閉螢幕背景播放的核心 Media3 前台服務
 */
@OptIn(UnstableApi::class)
class OmniMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null

    private val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
                    android.util.Log.i("OmniSessionService", "Audio becoming noisy (headphone/bluetooth disconnected), auto-pausing playback.")
                    exoPlayer.pause()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as OmniStreamApp

        // 註冊耳機與藍芽斷線監聽廣播，斷線時立刻自動暫停
        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, filter)

        // 1. 配置 YouTube 與 Google Drive 友善之相容 User-Agent 與邊播邊緩存的 DataSource
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/21.26.364 (Linux; U; Android 11)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

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
            .setHandleAudioBecomingNoisy(true) // 耳機/藍芽斷開時原生自動暫停
            .setWakeMode(C.WAKE_MODE_NETWORK) // 鎖屏時保持網路與 CPU 運作
            .build()

        // 3. 監聽播放進度以支援跨啟動斷點續播、YouTube 連續播放預解析與自動容錯
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveCurrentPlaybackProgress()
                if (mediaItem == null) return

                val currentId = mediaItem.mediaId
                val currentUri = mediaItem.localConfiguration?.uri?.toString() ?: ""

                // 1. 若當前曲目為 YouTube 且 URI 為網頁網址或尚未解析，立即背景解析真實直鏈並更新播放
                if (currentId.startsWith("yt_") && (currentUri.contains("youtube.com/watch") || currentUri.isBlank())) {
                    serviceScope.launch(Dispatchers.IO) {
                        val vid = com.overlord.omnistream.data.youtube.YouTubeAudioExtractor.extractVideoId(currentId)
                        val mediaInfo = app.repository.ytAudioExtractor.extractMediaInfo(vid)
                        if (mediaInfo != null && mediaInfo.audioUrl.isNotBlank()) {
                            app.repository.updateItemMediaUri(currentId, mediaInfo.audioUrl)
                            withContext(Dispatchers.Main) {
                                if (exoPlayer.currentMediaItem?.mediaId == currentId) {
                                    val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                    val updatedMediaItem = mediaItem.buildUpon()
                                        .setUri(mediaInfo.audioUrl)
                                        .build()
                                    val currentIndex = exoPlayer.currentMediaItemIndex
                                    exoPlayer.replaceMediaItem(currentIndex, updatedMediaItem)
                                    exoPlayer.seekTo(currentIndex, currentPos)
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            }
                        }
                    }
                }

                // 2. 預解析下一首 YouTube 曲目 (Pre-fetch Next)，達成零延遲連續播放
                prefetchNextYouTubeItem()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveCurrentPlaybackProgress()
                if (isPlaying) {
                    startProgressTracker()
                    prefetchNextYouTubeItem()
                } else {
                    progressTrackerJob?.cancel()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("OmniSessionService", "Player error: ${error.errorCodeName}, attempting recovery or skip...", error)
                val currentItem = exoPlayer.currentMediaItem ?: return
                val currentId = currentItem.mediaId

                if (currentId.startsWith("yt_")) {
                    serviceScope.launch(Dispatchers.IO) {
                        val vid = com.overlord.omnistream.data.youtube.YouTubeAudioExtractor.extractVideoId(currentId)
                        val freshInfo = app.repository.ytAudioExtractor.extractMediaInfo(vid)
                        if (freshInfo != null && freshInfo.audioUrl.isNotBlank()) {
                            app.repository.updateItemMediaUri(currentId, freshInfo.audioUrl)
                            withContext(Dispatchers.Main) {
                                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val updatedMediaItem = currentItem.buildUpon()
                                    .setUri(freshInfo.audioUrl)
                                    .build()
                                val currentIndex = exoPlayer.currentMediaItemIndex
                                exoPlayer.replaceMediaItem(currentIndex, updatedMediaItem)
                                exoPlayer.seekTo(currentIndex, currentPos)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        } else {
                            // 若影片無法解析（私人、下架、地區限制），自動跳至下一首曲目，確保連續播放不卡死
                            withContext(Dispatchers.Main) {
                                android.util.Log.w("OmniSessionService", "Cannot recover $currentId, auto skipping to next media item.")
                                if (exoPlayer.hasNextMediaItem()) {
                                    exoPlayer.seekToNextMediaItem()
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            }
                        }
                    }
                } else {
                    // 非 YouTube 檔案若損壞，若有下一首也自動跳轉
                    if (exoPlayer.hasNextMediaItem()) {
                        serviceScope.launch(Dispatchers.Main) {
                            delay(1000)
                            exoPlayer.seekToNextMediaItem()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
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

    /**
     * 提前預解析佇列中的下一首 YouTube 音訊直鏈 (Pre-fetch Next)
     * 避免當前曲目播放完畢切換到下一首時，因 URL 尚未解析或過期導致連續播放中斷或卡頓
     */
    private fun prefetchNextYouTubeItem() {
        val nextIndex = exoPlayer.currentMediaItemIndex + 1
        if (nextIndex < exoPlayer.mediaItemCount) {
            val nextItem = exoPlayer.getMediaItemAt(nextIndex)
            val nextId = nextItem.mediaId
            val nextUri = nextItem.localConfiguration?.uri?.toString() ?: ""

            if (nextId.startsWith("yt_") && (nextUri.contains("youtube.com/watch") || nextUri.isBlank())) {
                val app = applicationContext as OmniStreamApp
                serviceScope.launch(Dispatchers.IO) {
                    val vid = com.overlord.omnistream.data.youtube.YouTubeAudioExtractor.extractVideoId(nextId)
                    val info = app.repository.ytAudioExtractor.extractMediaInfo(vid)
                    if (info != null && info.audioUrl.isNotBlank()) {
                        app.repository.updateItemMediaUri(nextId, info.audioUrl)
                        withContext(Dispatchers.Main) {
                            if (nextIndex < exoPlayer.mediaItemCount && exoPlayer.getMediaItemAt(nextIndex).mediaId == nextId) {
                                val updated = nextItem.buildUpon()
                                    .setUri(info.audioUrl)
                                    .build()
                                exoPlayer.replaceMediaItem(nextIndex, updated)
                                android.util.Log.d("OmniSessionService", "Successfully pre-fetched next YouTube audio for index $nextIndex ($nextId)")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            // ignore if not registered
        }
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

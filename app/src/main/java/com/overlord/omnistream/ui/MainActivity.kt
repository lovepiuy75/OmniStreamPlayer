package com.overlord.omnistream.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.overlord.omnistream.OmniStreamApp
import com.overlord.omnistream.data.gdrive.DriveFolderSyncWorker
import com.overlord.omnistream.data.local.entity.SubscriptionEntity
import com.overlord.omnistream.playback.PlaybackController
import com.overlord.omnistream.ui.components.MiniPlayerBar
import com.overlord.omnistream.ui.screens.*
import com.overlord.omnistream.ui.theme.BgDark
import com.overlord.omnistream.ui.theme.CyanAccent
import com.overlord.omnistream.ui.theme.OmniStreamTheme
import com.overlord.omnistream.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var playbackController: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as OmniStreamApp
        val repo = app.repository

        // 1. 初始化背景播放控制器
        playbackController = PlaybackController(this)
        playbackController.connect {
            // 連線完成後，執行跨啟動「斷點續播」恢復
            restorePreviousPlaybackState(repo)
        }

        // 2. 啟動 Google 雲端定時背景同步 (每 4 小時自動檢查資料夾更新)
        scheduleDriveBackgroundSync()

        // 3. 繪製 Jetpack Compose 介面
        setContent {
            OmniStreamTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                var showFullPlayer by remember { mutableStateOf(false) }

                val playlist by repo.getPlaylistFlow().collectAsState(initial = emptyList())
                val isPlaying by playbackController.isPlaying.collectAsState()
                val currentItem by playbackController.currentMediaItem.collectAsState()

                Scaffold(
                    bottomBar = {
                        Column {
                            // 底部常駐 Mini Player Bar
                            MiniPlayerBar(
                                controller = playbackController,
                                isPlaying = isPlaying,
                                title = currentItem?.mediaMetadata?.title?.toString() ?: "",
                                artist = currentItem?.mediaMetadata?.artist?.toString() ?: "",
                                onClick = { showFullPlayer = true }
                            )

                            // 底部導航欄
                            NavigationBar(containerColor = BgDark) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "播放清單") },
                                    label = { Text("清單") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanAccent,
                                        selectedTextColor = CyanAccent,
                                        unselectedIconColor = TextPrimary,
                                        unselectedTextColor = TextPrimary
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Cloud, contentDescription = "雲端硬碟") },
                                    label = { Text("Google 雲端") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "YouTube") },
                                    label = { Text("YouTube") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> PlaylistScreen(
                                items = playlist,
                                onItemClick = { index ->
                                    playbackController.setPlaylistAndPlay(playlist, startIndex = index)
                                },
                                onDeleteItem = { id ->
                                    lifecycleScope.launch { repo.removeItemFromPlaylist(id) }
                                }
                            )
                            1 -> GDriveScreen(
                                onAddFolder = { folderId, name ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        app.database.subscriptionDao().insert(
                                            SubscriptionEntity(id = folderId, name = name, type = "GDRIVE")
                                        )
                                        // 立即初次拉取
                                        val files = repo.gdriveService.fetchFolderAudioFiles(folderId, name)
                                        repo.addItemsToPlaylist(files)
                                    }
                                },
                                onSyncNow = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        // 手動觸發雲端同步
                                    }
                                }
                            )
                            2 -> YouTubeScreen(
                                onAddChannel = { channelId, name ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        app.database.subscriptionDao().insert(
                                            SubscriptionEntity(id = channelId, name = name, type = "YOUTUBE")
                                        )
                                        val videos = repo.ytRssParser.fetchChannelLatestVideos(channelId, name)
                                        repo.addItemsToPlaylist(videos)
                                    }
                                },
                                onSyncVideos = {
                                    // 檢查 YouTube 更新
                                }
                            )
                        }

                        // 全螢幕播放面板
                        if (showFullPlayer) {
                            PlayerScreen(
                                controller = playbackController,
                                title = currentItem?.mediaMetadata?.title?.toString() ?: "",
                                artist = currentItem?.mediaMetadata?.artist?.toString() ?: "",
                                isPlaying = isPlaying,
                                onClose = { showFullPlayer = false }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 斷點續播記憶恢復：讀取上次播放歌曲與毫秒進度
     */
    private fun restorePreviousPlaybackState(repo: com.overlord.omnistream.data.repository.PlayerRepository) {
        lifecycleScope.launch(Dispatchers.IO) {
            val lastState = repo.getPlaybackState()
            val playlist = repo.getAllPlaylistItems()

            if (lastState != null && playlist.isNotEmpty()) {
                val index = playlist.indexOfFirst { it.id == lastState.currentItemId }.coerceAtLeast(0)
                playbackController.setPlaylistAndPlay(
                    items = playlist,
                    startIndex = index,
                    startPositionMs = lastState.currentPositionMs
                )
                // 恢復進度後預設暫停，讓 Bryant 點擊開始接續播
                playbackController.pause()
            }
        }
    }

    private fun scheduleDriveBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<DriveFolderSyncWorker>(
            4, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "OmniDriveFolderSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}

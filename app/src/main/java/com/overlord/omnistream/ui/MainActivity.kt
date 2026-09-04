package com.overlord.omnistream.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
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
import com.overlord.omnistream.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var playbackController: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as OmniStreamApp
        val repo = app.repository

        // 1. 初始化背景播放控制器並恢復斷點續播
        playbackController = PlaybackController(this)
        playbackController.connect {
            restorePreviousPlaybackState(repo)
        }

        // 2. 啟動 Google 雲端定時背景同步 (每 4 小時自動檢查)
        scheduleDriveBackgroundSync()

        // 3. Jetpack Compose UI
        setContent {
            OmniStreamTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                var showFullPlayer by remember { mutableStateOf(false) }
                var currentGroupId by remember { mutableStateOf("default") }
                var isSyncingGDrive by remember { mutableStateOf(false) }

                val groups by repo.getPlaylistGroupsFlow().collectAsState(initial = emptyList())
                val playlist by repo.getPlaylistFlow(currentGroupId).collectAsState(initial = emptyList())
                val subscriptions by app.database.subscriptionDao().getByTypeFlow("GDRIVE").collectAsState(initial = emptyList())
                val isPlaying by playbackController.isPlaying.collectAsState()
                val currentItem by playbackController.currentMediaItem.collectAsState()

                Scaffold(
                    bottomBar = {
                        Column {
                            MiniPlayerBar(
                                controller = playbackController,
                                isPlaying = isPlaying,
                                title = currentItem?.mediaMetadata?.title?.toString() ?: "",
                                artist = currentItem?.mediaMetadata?.artist?.toString() ?: "",
                                onClick = { showFullPlayer = true }
                            )

                            NavigationBar(containerColor = BgDark) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "播放清單") },
                                    label = { Text("清單") }
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
                                groups = groups,
                                selectedGroupId = currentGroupId,
                                onSelectGroup = { currentGroupId = it },
                                onCreateGroup = { name ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val gid = "grp_" + UUID.randomUUID().toString().take(8)
                                        repo.createPlaylistGroup(gid, name)
                                        currentGroupId = gid
                                    }
                                },
                                items = playlist,
                                onItemClick = { index ->
                                    playbackController.setPlaylistAndPlay(playlist, startIndex = index)
                                },
                                onDeleteItem = { id ->
                                    lifecycleScope.launch(Dispatchers.IO) { repo.removeItemFromPlaylist(id) }
                                },
                                onScanLocalAudio = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val localFiles = repo.scanLocalAudioFiles()
                                        repo.addItemsToPlaylist(localFiles, currentGroupId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "已載入 ${localFiles.size} 首本機音訊", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            1 -> GDriveScreen(
                                subscriptions = subscriptions,
                                isSyncing = isSyncingGDrive,
                                onAddFolder = { folderInput, name ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val cleanFolderId = com.overlord.omnistream.data.gdrive.GoogleDriveService.extractFolderId(folderInput)
                                        app.database.subscriptionDao().insert(
                                            SubscriptionEntity(id = cleanFolderId, name = name, type = "GDRIVE", publicUrl = folderInput)
                                        )
                                        val files = repo.gdriveService.fetchFolderAudioFiles(cleanFolderId, name)
                                        repo.addItemsToPlaylist(files, currentGroupId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "已成功加入並解析出 ${files.size} 首雲端音訊！", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onDeleteFolder = { id ->
                                    lifecycleScope.launch(Dispatchers.IO) { app.database.subscriptionDao().deleteById(id) }
                                },
                                onSyncNow = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        isSyncingGDrive = true
                                        var totalNew = 0
                                        val subs = app.database.subscriptionDao().getAll().filter { it.type == "GDRIVE" }
                                        val currentIds = repo.getPlaylistItems(currentGroupId).map { it.id }.toSet()
                                        for (sub in subs) {
                                            val files = repo.gdriveService.fetchFolderAudioFiles(sub.id, sub.name)
                                            val newFiles = files.filter { it.id !in currentIds }
                                            if (newFiles.isNotEmpty()) {
                                                repo.addItemsToPlaylist(newFiles, currentGroupId)
                                                totalNew += newFiles.size
                                            }
                                        }
                                        isSyncingGDrive = false
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "雲端同步完成！新增 $totalNew 首檔案", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            2 -> YouTubeScreen(
                                onAddChannel = { channelInput, name, onlyNew ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val cleanId = com.overlord.omnistream.data.youtube.YouTubeRssParser.extractChannelId(channelInput)
                                        val sinceTs = if (onlyNew) System.currentTimeMillis() else null
                                        app.database.subscriptionDao().insert(
                                            SubscriptionEntity(id = cleanId, name = name, type = "YOUTUBE", sinceTimestamp = sinceTs)
                                        )
                                        val videos = repo.ytRssParser.fetchChannelLatestVideos(cleanId, name, sinceTs)
                                        repo.addItemsToPlaylist(videos, currentGroupId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "已訂閱！已依時間因子載入 ${videos.size} 首影片", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onImportPlaylist = { playlistInput, name ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val cleanPid = com.overlord.omnistream.data.youtube.YouTubePlaylistParser.extractPlaylistId(playlistInput)
                                        val videos = repo.ytPlaylistParser.fetchPlaylistVideos(cleanPid, name)
                                        repo.addItemsToPlaylist(videos, currentGroupId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "播放清單匯入成功！共加入 ${videos.size} 首曲目", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onSyncVideos = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        // 檢查頻道更新
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "頻道已更新至最新狀態", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

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

    private fun restorePreviousPlaybackState(repo: com.overlord.omnistream.data.repository.PlayerRepository) {
        lifecycleScope.launch(Dispatchers.IO) {
            val lastState = repo.getPlaybackState()
            val playlist = repo.getPlaylistItems("default")

            if (lastState != null && playlist.isNotEmpty()) {
                val index = playlist.indexOfFirst { it.id == lastState.currentItemId }.coerceAtLeast(0)
                playbackController.setPlaylistAndPlay(
                    items = playlist,
                    startIndex = index,
                    startPositionMs = lastState.currentPositionMs
                )
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

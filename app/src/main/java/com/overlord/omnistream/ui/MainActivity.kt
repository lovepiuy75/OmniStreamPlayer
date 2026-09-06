package com.overlord.omnistream.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.overlord.omnistream.core.model.MediaSourceType
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

        // 1.5 檢查本機備份並自動無縫還原（避免重新安裝 App 後設定遺失）
        lifecycleScope.launch(Dispatchers.IO) {
            val subsCount = app.database.subscriptionDao().getAll().size
            val itemsCount = repo.getPlaylistItems("default").size
            if (subsCount == 0 && itemsCount == 0) {
                val restored = repo.backupManager.restoreBackupIfAvailable()
                if (restored > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "✨ 已自動為您接回前次保留的設定與訂閱清單！", Toast.LENGTH_LONG).show()
                    }
                }
            }
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
                var isSyncingYouTube by remember { mutableStateOf(false) }

                val groups by repo.getPlaylistGroupsFlow().collectAsState(initial = emptyList())
                val playlist by repo.getPlaylistFlow(currentGroupId).collectAsState(initial = emptyList())
                val subscriptions by app.database.subscriptionDao().getByTypeFlow("GDRIVE").collectAsState(initial = emptyList())
                val ytSubscriptions by app.database.subscriptionDao().getByTypeFlow("YOUTUBE").collectAsState(initial = emptyList())
                val isPlaying by playbackController.isPlaying.collectAsState()
                val currentItem by playbackController.currentMediaItem.collectAsState()

                // 當全螢幕播放器開啟時，按 Android 返回鍵收合播放器回到當前分頁，避免退出 App
                BackHandler(enabled = showFullPlayer) {
                    showFullPlayer = false
                }

                Scaffold(
                    bottomBar = {
                        Column {
                            if (!showFullPlayer) {
                                MiniPlayerBar(
                                    controller = playbackController,
                                    isPlaying = isPlaying,
                                    title = currentItem?.mediaMetadata?.title?.toString() ?: "",
                                    artist = currentItem?.mediaMetadata?.artist?.toString() ?: "",
                                    onClick = { showFullPlayer = true }
                                )
                            }

                            NavigationBar(containerColor = BgDark) {
                                NavigationBarItem(
                                    selected = selectedTab == 0 && !showFullPlayer,
                                    onClick = {
                                        selectedTab = 0
                                        showFullPlayer = false
                                    },
                                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "播放清單") },
                                    label = { Text("清單") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanAccent,
                                        selectedTextColor = CyanAccent,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1 && !showFullPlayer,
                                    onClick = {
                                        selectedTab = 1
                                        showFullPlayer = false
                                    },
                                    icon = { Icon(Icons.Default.Cloud, contentDescription = "雲端硬碟") },
                                    label = { Text("Google 雲端") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanAccent,
                                        selectedTextColor = CyanAccent,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2 && !showFullPlayer,
                                    onClick = {
                                        selectedTab = 2
                                        showFullPlayer = false
                                    },
                                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "YouTube") },
                                    label = { Text("YouTube") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanAccent,
                                        selectedTextColor = CyanAccent,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
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
                                currentPlayingId = currentItem?.mediaId,
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
                                    val clicked = playlist[index]
                                    if (clicked.mediaUri.contains("youtube.com/watch") || clicked.mediaUri.isBlank()) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val vid = com.overlord.omnistream.data.youtube.YouTubeAudioExtractor.extractVideoId(clicked.id.ifBlank { clicked.mediaUri })
                                            val mediaInfo = repo.ytAudioExtractor.extractMediaInfo(vid)
                                            val freshUrl = mediaInfo?.audioUrl
                                            if (freshUrl != null) {
                                                repo.updateItemMediaUri(clicked.id, freshUrl)
                                                val updatedList = playlist.toMutableList()
                                                updatedList[index] = clicked.copy(
                                                    mediaUri = freshUrl,
                                                    title = if (clicked.title.startsWith("YouTube 播放清單 #") || clicked.title.isBlank()) mediaInfo.title else clicked.title,
                                                    artist = if (clicked.artist == "YouTube 播放清單") mediaInfo.author else clicked.artist
                                                )
                                                withContext(Dispatchers.Main) {
                                                    playbackController.playItemAtIndex(updatedList, index)
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    playbackController.playItemAtIndex(playlist, index)
                                                }
                                            }
                                        }
                                    } else {
                                        playbackController.playItemAtIndex(playlist, index)
                                    }
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
                                },
                                onManualBackup = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val ok = repo.backupManager.createBackup()
                                        withContext(Dispatchers.Main) {
                                            if (ok) {
                                                Toast.makeText(this@MainActivity, "備份成功！已存至 Download/omnistream_backup.json", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "備份失敗，請檢視權限", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onManualRestore = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val count = repo.backupManager.restoreBackupIfAvailable()
                                        withContext(Dispatchers.Main) {
                                            if (count > 0) {
                                                Toast.makeText(this@MainActivity, "還原成功！已恢復 $count 筆訂閱與設定", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "未找到有效的備份檔案或資料已存在", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                            2 -> YouTubeScreen(
                                subscriptions = ytSubscriptions,
                                isSyncing = isSyncingYouTube,
                                onAddChannel = { channelInput, name, onlyNew ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val resolvedId = repo.ytRssParser.resolveRealChannelId(channelInput)
                                        val sinceTs = if (onlyNew) System.currentTimeMillis() else null
                                        app.database.subscriptionDao().insert(
                                            SubscriptionEntity(
                                                id = resolvedId,
                                                name = name,
                                                type = "YOUTUBE",
                                                isPlaylist = false,
                                                publicUrl = channelInput,
                                                sinceTimestamp = sinceTs,
                                                lastSyncedTime = System.currentTimeMillis()
                                            )
                                        )
                                        val videos = repo.ytRssParser.fetchChannelLatestVideos(resolvedId, name, sinceTs)
                                        repo.addItemsToPlaylist(videos, currentGroupId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "已訂閱！已載入 ${videos.size} 首影片至清單", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeleteSubscription = { id ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        app.database.subscriptionDao().deleteById(id)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "已移除追蹤紀錄", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onImportPlaylist = { playlistInput, name ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val cleanPid = com.overlord.omnistream.data.youtube.YouTubePlaylistParser.extractPlaylistId(playlistInput)
                                        app.database.subscriptionDao().insert(
                                            SubscriptionEntity(
                                                id = cleanPid,
                                                name = name,
                                                type = "YOUTUBE",
                                                isPlaylist = true,
                                                publicUrl = playlistInput,
                                                lastSyncedTime = System.currentTimeMillis()
                                            )
                                        )
                                        val videos = repo.ytPlaylistParser.fetchPlaylistVideos(cleanPid, name)
                                        repo.addItemsToPlaylist(videos, currentGroupId)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "播放清單匯入成功！共加入 ${videos.size} 首曲目", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onSyncVideos = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        isSyncingYouTube = true
                                        val channels = app.database.subscriptionDao().getAll().filter { it.type == "YOUTUBE" && !it.isPlaylist }
                                        val currentIds = repo.getPlaylistItems(currentGroupId).map { it.id }.toSet()
                                        var totalNew = 0
                                        for (ch in channels) {
                                            val videos = repo.ytRssParser.fetchChannelLatestVideos(ch.id, ch.name, ch.sinceTimestamp)
                                            val newVideos = videos.filter { it.id !in currentIds }
                                            if (newVideos.isNotEmpty()) {
                                                repo.addItemsToPlaylist(newVideos, currentGroupId)
                                                totalNew += newVideos.size
                                            }
                                            app.database.subscriptionDao().updateLastSyncedTime(ch.id, System.currentTimeMillis())
                                        }
                                        isSyncingYouTube = false
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "頻道檢查完成！共新增 $totalNew 首最新曲目", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = showFullPlayer,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
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
            try {
                val lastState = repo.getPlaybackState()
                val playlist = repo.getPlaylistItems("default")

                if (lastState != null && playlist.isNotEmpty()) {
                    val index = playlist.indexOfFirst { it.id == lastState.currentItemId }.coerceAtLeast(0)
                    withContext(Dispatchers.Main) {
                        playbackController.setPlaylistAndPlay(
                            items = playlist,
                            startIndex = index,
                            startPositionMs = lastState.currentPositionMs
                        )
                        playbackController.pause()
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Failed to restore playback state", e)
            }
        }
    }

    private fun scheduleDriveBackgroundSync() {
        try {
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
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to schedule drive sync worker", e)
        }
    }


    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}

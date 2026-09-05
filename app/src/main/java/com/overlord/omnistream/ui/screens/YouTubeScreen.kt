package com.overlord.omnistream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overlord.omnistream.data.local.entity.SubscriptionEntity
import com.overlord.omnistream.ui.theme.*

@Composable
fun YouTubeScreen(
    subscriptions: List<SubscriptionEntity>,
    onAddChannel: (channelIdOrUrl: String, name: String, onlyNew: Boolean) -> Unit,
    onDeleteSubscription: (id: String) -> Unit,
    onImportPlaylist: (playlistUrlOrId: String, name: String) -> Unit,
    onSyncVideos: () -> Unit,
    isSyncing: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 頻道追蹤 (含時間因子), 1: 匯入播放清單

    var channelInput by remember { mutableStateOf("") }
    var channelNameInput by remember { mutableStateOf("") }
    var filterOnlyNew by remember { mutableStateOf(false) } // 預設關閉：載入近期影片供立即聆聽

    var playlistInput by remember { mutableStateOf("") }
    var playlistNameInput by remember { mutableStateOf("") }

    val channels = remember(subscriptions) { subscriptions.filter { !it.isPlaylist } }
    val playlists = remember(subscriptions) { subscriptions.filter { it.isPlaylist } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "YouTube 內容整合",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardDark,
            contentColor = RedAccent
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("YouTuber 頻道更新") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("匯入 YouTube 播放清單") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // 1. 頻道更新追蹤卡片 (含時間因子過濾)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "追蹤頻道並設定時間過濾",
                        color = RedAccent,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "支援免 API Key 自動提取純音訊，可設定時間因子只接收新發布影片，排除歷史看過的舊片。",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = channelNameInput,
                        onValueChange = { channelNameInput = it },
                        label = { Text("頻道名稱 (如: 科技導讀)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it },
                        label = { Text("頻道網址、@Handle 或 ID (UC...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 時間因子選擇器
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = filterOnlyNew,
                            onCheckedChange = { filterOnlyNew = it },
                            colors = CheckboxDefaults.colors(checkedColor = RedAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "只加入「追蹤後」發布的新影片 (排除歷史舊片)",
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (filterOnlyNew) "開啟中：後續自動更新僅收錄新片 (首次追蹤仍保留最新5首)" else "已關閉：直接載入近期 15~25 首影片供立即聆聽",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (channelInput.isNotBlank()) {
                                onAddChannel(channelInput, channelNameInput.ifBlank { "YouTuber" }, filterOnlyNew)
                                channelInput = ""
                                channelNameInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("開始追蹤並自動過濾", color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 已追蹤頻道清單區塊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已追蹤頻道 (${channels.size})",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                Button(
                    onClick = onSyncVideos,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RedAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("檢查中...", color = RedAccent)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = "同步", tint = RedAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("檢查全部更新", color = RedAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未追蹤任何 YouTuber 頻道。\n在上方輸入頻道名稱與網址即可開始追蹤！",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels, key = { it.id }) { sub ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subscriptions,
                                    contentDescription = "頻道",
                                    tint = RedAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sub.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sub.id,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (sub.sinceTimestamp != null) "⚡ 時間過濾：開啟 (只收新發布)" else "✦ 收錄全部近期影片",
                                        color = if (sub.sinceTimestamp != null) RedAccent else CyanAccent,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(onClick = { onDeleteSubscription(sub.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "刪除追蹤",
                                        tint = RedAccent.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 匯入 YouTube 既有播放清單
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "匯入 YouTube 播放清單",
                        color = AmberAccent,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "貼入 YouTube 播放清單網址 (包含 list=PL...)，一鍵將整份清單解析為純音訊加入 App！",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        label = { Text("清單名稱 (如: 專注工作音樂)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = playlistInput,
                        onValueChange = { playlistInput = it },
                        label = { Text("YouTube 播放清單網址或 ID") },
                        placeholder = { Text("https://www.youtube.com/playlist?list=PL...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (playlistInput.isNotBlank()) {
                                onImportPlaylist(playlistInput, playlistNameInput.ifBlank { "YouTube 清單" })
                                playlistInput = ""
                                playlistNameInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("解析並整批匯入", color = BgDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 已匯入播放清單區塊
            Text(
                text = "已加入的播放清單 (${playlists.size})",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未加入任何 YouTube 播放清單。\n在上方貼入清單網址即可匯入！",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = "播放清單",
                                    tint = AmberAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = pl.id,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "✦ 已匯入至播放清單",
                                        color = AmberAccent,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(onClick = { onDeleteSubscription(pl.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "移除紀錄",
                                        tint = RedAccent.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

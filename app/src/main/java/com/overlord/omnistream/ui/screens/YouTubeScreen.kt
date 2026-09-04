package com.overlord.omnistream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overlord.omnistream.ui.theme.*

@Composable
fun YouTubeScreen(
    onAddChannel: (channelIdOrUrl: String, name: String, onlyNew: Boolean) -> Unit,
    onImportPlaylist: (playlistUrlOrId: String, name: String) -> Unit,
    onSyncVideos: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 頻道追蹤 (含時間因子), 1: 匯入播放清單

    var channelInput by remember { mutableStateOf("") }
    var channelNameInput by remember { mutableStateOf("") }
    var filterOnlyNew by remember { mutableStateOf(true) } // 時間因子預設: 只抓加入後的新片

    var playlistInput by remember { mutableStateOf("") }
    var playlistNameInput by remember { mutableStateOf("") }

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
            // 頻道更新追蹤 (含時間因子過濾)
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
                        label = { Text("頻道網址或 ID (UC...)") },
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
                        Text(
                            text = "只加入「追蹤後」發布的新影片 (排除歷史舊片)",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSyncVideos,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("立即檢查 YouTuber 更新", color = RedAccent)
        }
    }
}

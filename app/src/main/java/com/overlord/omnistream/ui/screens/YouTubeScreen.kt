package com.overlord.omnistream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overlord.omnistream.ui.theme.*

@Composable
fun YouTubeScreen(
    onAddChannel: (channelId: String, channelName: String) -> Unit,
    onSyncVideos: () -> Unit
) {
    var channelIdInput by remember { mutableStateOf("") }
    var channelNameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "YouTube 頻道更新追蹤",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "追蹤 YouTuber 頻道",
                    color = RedAccent,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "透過免 API Key 的 RSS 即時追蹤 YouTuber 最新影片，並抽取純音訊加入清單，可關閉螢幕背景收聽。",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = channelNameInput,
                    onValueChange = { channelNameInput = it },
                    label = { Text("頻道名稱 (如: 科技導讀)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = channelIdInput,
                    onValueChange = { channelIdInput = it },
                    label = { Text("頻道 ID (UC...)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (channelIdInput.isNotBlank()) {
                            onAddChannel(channelIdInput, channelNameInput.ifBlank { "YouTuber" })
                            channelIdInput = ""
                            channelNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("訂閱並自動抓取最新影片", color = TextPrimary)
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

package com.overlord.omnistream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overlord.omnistream.data.local.entity.SubscriptionEntity
import com.overlord.omnistream.ui.theme.*

@Composable
fun GDriveScreen(
    subscriptions: List<SubscriptionEntity>,
    isSyncing: Boolean,
    onAddFolder: (folderIdOrUrl: String, name: String) -> Unit,
    onDeleteFolder: (id: String) -> Unit,
    onSyncNow: () -> Unit
) {
    var folderInput by remember { mutableStateOf("") }
    var folderNameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "Google 雲端硬碟自動同步",
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
                    text = "監控雲端資料夾 (公開連結/ID 皆可)",
                    color = CyanAccent,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "直接貼入 Google Drive 資料夾共用網址或 ID，免登入自動抓取資料夾內所有音訊連續播放！",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("自訂名稱 (如: 每日新知 Podcast)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderInput,
                    onValueChange = { folderInput = it },
                    label = { Text("資料夾網址或 ID") },
                    placeholder = { Text("https://drive.google.com/drive/folders/...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (folderInput.isNotBlank()) {
                            onAddFolder(folderInput, folderNameInput.ifBlank { "雲端資料夾" })
                            folderInput = ""
                            folderNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("新增並立即載入音訊", color = BgDark)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已監控資料夾 (${subscriptions.size})",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CyanAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("同步中...", color = CyanAccent)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = "同步", tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("立即同步全部", color = CyanAccent)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "尚未加入任何雲端資料夾\n貼上連結即可連續播放資料夾音訊",
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subscriptions) { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardDark)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, color = TextPrimary, fontSize = 15.sp)
                            Text("ID: ${sub.id.take(15)}...", color = TextSecondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDeleteFolder(sub.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "刪除", tint = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

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
fun GDriveScreen(
    onAddFolder: (folderId: String, name: String) -> Unit,
    onSyncNow: () -> Unit
) {
    var folderIdInput by remember { mutableStateOf("") }
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
                    text = "監控指定資料夾",
                    color = CyanAccent,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "輸入資料夾 ID 或共用連結，App 會在背景自動偵測每天新增的內容並主動加入播放清單。",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("資料夾名稱 (如: 每日新知音訊)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderIdInput,
                    onValueChange = { folderIdInput = it },
                    label = { Text("資料夾 ID 或網址") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (folderIdInput.isNotBlank()) {
                            onAddFolder(folderIdInput, folderNameInput.ifBlank { "雲端資料夾" })
                            folderIdInput = ""
                            folderNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("新增並監控此資料夾", color = BgDark)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSyncNow,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("立即執行雲端同步", color = CyanAccent)
        }
    }
}

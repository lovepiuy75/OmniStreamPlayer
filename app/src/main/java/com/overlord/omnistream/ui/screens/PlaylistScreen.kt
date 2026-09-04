package com.overlord.omnistream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import com.overlord.omnistream.data.local.entity.PlaylistGroupEntity
import com.overlord.omnistream.ui.theme.*

@Composable
fun PlaylistScreen(
    groups: List<PlaylistGroupEntity>,
    selectedGroupId: String,
    onSelectGroup: (String) -> Unit,
    onCreateGroup: (name: String) -> Unit,
    items: List<PlaylistItem>,
    onItemClick: (Int) -> Unit,
    onDeleteItem: (String) -> Unit,
    onScanLocalAudio: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val currentGroupName = groups.find { it.id == selectedGroupId }?.name ?: "預設清單"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        // 頂部多清單切換選單與操作列 (穩定版 Box + DropdownMenu，杜絕 M3 實驗性 API 簽名不相容閃退)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { isDropdownExpanded = true },
                    colors = CardDefaults.outlinedCardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$currentGroupName (${items.size})",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "選擇清單",
                            tint = CyanAccent
                        )
                    }
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.background(CardDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("預設清單", color = TextPrimary) },
                        onClick = {
                            onSelectGroup("default")
                            isDropdownExpanded = false
                        }
                    )
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name, color = TextPrimary) },
                            onClick = {
                                onSelectGroup(group.id)
                                isDropdownExpanded = false
                            }
                        )
                    }
                    Divider(color = SurfaceDark)
                    DropdownMenuItem(
                        text = { Text("＋ 新增播放清單...", color = CyanAccent) },
                        onClick = {
                            isDropdownExpanded = false
                            showCreateDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 掃描本機按鈕
            IconButton(
                onClick = onScanLocalAudio,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardDark)
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = "掃描本機音樂", tint = AmberAccent)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "目前播放清單為空\n可點擊右上角掃描手機檔案，或至雲端/YouTube新增音訊",
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items) { index, item ->
                    PlaylistItemRow(
                        item = item,
                        index = index + 1,
                        onClick = { onItemClick(index) },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        }
    }

    // 建立新播放清單 Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新增播放清單", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("清單名稱") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            onCreateGroup(newGroupName)
                            newGroupName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("建立", color = BgDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardDark
        )
    }
}

@Composable
fun PlaylistItemRow(
    item: PlaylistItem,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (badgeColor, badgeText) = when (item.sourceType) {
                    MediaSourceType.LOCAL -> AmberAccent to "本機"
                    MediaSourceType.GDRIVE -> CyanAccent to "雲端"
                    MediaSourceType.YOUTUBE -> RedAccent to "YouTube"
                }
                Text(
                    text = "[$badgeText]",
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "移除",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

package com.overlord.omnistream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.overlord.omnistream.playback.PlaybackController
import com.overlord.omnistream.ui.theme.*

@Composable
fun PlayerScreen(
    controller: PlaybackController,
    title: String,
    artist: String,
    isPlaying: Boolean,
    onClose: () -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 頂部關閉 / 下拉箭頭
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TextSecondary)
        )

        // 封面唱盤視覺（霸王色 Tactical 圓環風格）
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(CardDark),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OMNI STREAM",
                    color = CyanAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 標題與創作者
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.ifEmpty { "OmniStream 音訊播放器" },
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = artist.ifEmpty { "無縫跨來源播放" },
                color = CyanAccent,
                fontSize = 14.sp
            )
        }

        // 播放控制面板
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = CardDark
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { if (isPlaying) controller.pause() else controller.play() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暫停",
                        tint = BgDark,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { controller.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

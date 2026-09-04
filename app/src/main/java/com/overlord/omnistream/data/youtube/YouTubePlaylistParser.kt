package com.overlord.omnistream.data.youtube

import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * 免 API Key 的 YouTube 播放清單解析器 (透過 YouTube InnerTube 端點)
 */
class YouTubePlaylistParser(private val client: OkHttpClient = OkHttpClient()) {

    companion object {
        private val PLAYLIST_ID_PATTERN = Pattern.compile("(?:[?&]list=|^list=)?([a-zA-Z0-9_-]+)")

        fun extractPlaylistId(input: String): String {
            val trimmed = input.trim()
            val matcher = PLAYLIST_ID_PATTERN.matcher(trimmed)
            return if (matcher.find()) {
                matcher.group(1) ?: matcher.group()
            } else {
                trimmed.substringBefore("&")
            }
        }
    }

    suspend fun fetchPlaylistVideos(
        playlistIdOrUrl: String,
        playlistTitle: String = "YouTube 播放清單"
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(playlistIdOrUrl)
        val url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"

        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240401.00.00")
                    put("hl", "zh-TW")
                    put("gl", "TW")
                })
            })
            put("browseId", "VL$playlistId")
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val items = mutableListOf<PlaylistItem>()
        val seenVids = mutableSetOf<String>()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            // 正則匹配所有包含於播放清單內的影片 ID
            val vidPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
            val matcher = vidPattern.matcher(body)
            var count = 1
            while (matcher.find()) {
                val vid = matcher.group(1)
                if (seenVids.add(vid)) {
                    items.add(
                        PlaylistItem(
                            id = "yt_$vid",
                            title = "$playlistTitle #$count",
                            artist = playlistTitle,
                            mediaUri = "https://www.youtube.com/watch?v=$vid",
                            sourceType = MediaSourceType.YOUTUBE,
                            artworkUri = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext items
    }
}

package com.overlord.omnistream.data.youtube

import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * YouTube 頻道 RSS Feed 偵測器：
 * 支援時間因子 (sinceTimestamp)，只加入指定時間後的新發布影片
 */
class YouTubeRssParser(private val client: OkHttpClient = OkHttpClient()) {

    companion object {
        private val CHANNEL_ID_PATTERN = Pattern.compile("UC[a-zA-Z0-9_-]{22}")

        fun extractChannelId(input: String): String {
            val trimmed = input.trim()
            val matcher = CHANNEL_ID_PATTERN.matcher(trimmed)
            return if (matcher.find()) matcher.group() else trimmed
        }
    }

    suspend fun fetchChannelLatestVideos(
        channelIdOrUrl: String,
        channelName: String,
        sinceTimestamp: Long? = null
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        var channelId = extractChannelId(channelIdOrUrl)

        // 若輸入為 @handle 或自訂網址，自動從頻道頁面 HTML 解析出正式 UC 開頭 channelId
        if (!channelId.startsWith("UC")) {
            val handleUrl = if (channelIdOrUrl.startsWith("http")) {
                channelIdOrUrl
            } else {
                "https://www.youtube.com/${if (channelIdOrUrl.startsWith("@")) "" else "@"}$channelIdOrUrl"
            }
            try {
                val handleRequest = Request.Builder()
                    .url(handleUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val handleResp = client.newCall(handleRequest).execute()
                val handleHtml = handleResp.body?.string() ?: ""
                val m = CHANNEL_ID_PATTERN.matcher(handleHtml)
                if (m.find()) {
                    channelId = m.group()
                }
            } catch (e: Exception) {
                // Ignore fallback to raw channelId
            }
        }

        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val request = Request.Builder().url(url).build()
        val items = mutableListOf<PlaylistItem>()

        try {
            val response = client.newCall(request).execute()
            val xmlData = response.body?.string() ?: return@withContext emptyList()

            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlData.toByteArray()))
            val entryNodes = doc.getElementsByTagName("entry")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            for (i in 0 until entryNodes.length) {
                val entry = entryNodes.item(i)
                val children = entry.childNodes
                var videoId: String? = null
                var title: String? = null
                var publishedMs: Long? = null

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName) {
                        "yt:videoId" -> videoId = node.textContent.trim()
                        "title" -> title = node.textContent.trim()
                        "published" -> {
                            val pubText = node.textContent.trim()
                            try {
                                val clean = pubText.substringBefore("+").substringBefore("Z")
                                publishedMs = dateFormat.parse(clean)?.time
                            } catch (e: Exception) {
                                // Ignore date parse errors
                            }
                        }
                    }
                }

                // 核心時間因子檢核：若設定了 sinceTimestamp 且發布時間早於該門檻，則跳過！
                if (sinceTimestamp != null && publishedMs != null && publishedMs < sinceTimestamp) {
                    continue
                }

                if (videoId != null && title != null) {
                    items.add(
                        PlaylistItem(
                            id = "yt_$videoId",
                            title = title,
                            artist = channelName,
                            mediaUri = "https://www.youtube.com/watch?v=$videoId",
                            sourceType = MediaSourceType.YOUTUBE,
                            artworkUri = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                            ytChannelId = channelId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext items
    }
}

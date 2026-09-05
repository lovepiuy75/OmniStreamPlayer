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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * YouTube 頻道 RSS Feed 偵測器：
 * 支援時間因子 (sinceTimestamp)，只加入指定時間後的新發布影片
 */
class YouTubeRssParser(
    private val client: OkHttpClient = OkHttpClient(),
    private val audioExtractor: YouTubeAudioExtractor = YouTubeAudioExtractor(client)
) {

    companion object {
        private val CHANNEL_ID_PATTERN = Pattern.compile("UC[a-zA-Z0-9_-]{22}")
        private val CANONICAL_CHANNEL_PATTERN = Pattern.compile("<link\\s+rel=[\"']canonical[\"']\\s+href=[\"']https://www\\.youtube\\.com/channel/(UC[a-zA-Z0-9_-]{22})[\"']")
        private val EXTERNAL_ID_PATTERN = Pattern.compile("[\"']externalId[\"']\\s*:\\s*[\"'](UC[a-zA-Z0-9_-]{22})[\"']")
        private val BROWSE_ID_PATTERN = Pattern.compile("[\"']browseId[\"']\\s*:\\s*[\"'](UC[a-zA-Z0-9_-]{22})[\"']")

        fun extractChannelId(input: String): String {
            val trimmed = input.trim()
            val matcher = CHANNEL_ID_PATTERN.matcher(trimmed)
            return if (matcher.find()) matcher.group() else trimmed
        }
    }

    suspend fun resolveRealChannelId(channelIdOrUrl: String): String = withContext(Dispatchers.IO) {
        val trimmed = channelIdOrUrl.trim()
        val directMatcher = CHANNEL_ID_PATTERN.matcher(trimmed)
        if (directMatcher.find()) {
            return@withContext directMatcher.group()
        }

        // 若輸入為 @handle 或自訂網址，自動從頻道頁面 HTML 解析出真實 UC 開頭 channelId
        val handleUrl = if (trimmed.startsWith("http")) {
            trimmed
        } else {
            "https://www.youtube.com/${if (trimmed.startsWith("@")) "" else "@"}$trimmed"
        }

        try {
            val handleRequest = Request.Builder()
                .url(handleUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val handleResp = client.newCall(handleRequest).execute()
            val handleHtml = handleResp.body?.string() ?: ""

            var m = CANONICAL_CHANNEL_PATTERN.matcher(handleHtml)
            if (m.find()) return@withContext m.group(1) ?: trimmed

            m = EXTERNAL_ID_PATTERN.matcher(handleHtml)
            if (m.find()) return@withContext m.group(1) ?: trimmed

            m = BROWSE_ID_PATTERN.matcher(handleHtml)
            if (m.find()) return@withContext m.group(1) ?: trimmed
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext trimmed
    }

    suspend fun fetchChannelLatestVideos(
        channelIdOrUrl: String,
        channelName: String,
        sinceTimestamp: Long? = null
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val channelId = resolveRealChannelId(channelIdOrUrl)

        // 1. 優先使用 InnerTube 官方上傳播放清單 (UU + 22 位元)，高相容且不會觸發 RSS 500 錯誤
        if (channelId.startsWith("UC") && channelId.length >= 24) {
            try {
                val uploadsPlaylistId = "UU" + channelId.substring(2)
                val playlistParser = YouTubePlaylistParser(client, audioExtractor)
                val plVideos = playlistParser.fetchPlaylistVideos(uploadsPlaylistId, channelName)
                if (plVideos.isNotEmpty()) {
                    val updatedItems = plVideos.map { it.copy(ytChannelId = channelId) }
                    if (sinceTimestamp == null) {
                        return@withContext updatedItems.take(25)
                    } else {
                        // 首次追蹤若無新片，保證至少留最新 5 首供立即播放聆聽
                        return@withContext updatedItems.take(5)
                    }
                }
            } catch (e: Exception) {
                // Fallback to RSS
            }
        }

        // 2. 備援方案：YouTube RSS Feed
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val allItems = mutableListOf<Pair<PlaylistItem, Long?>>()

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

                if (videoId != null && title != null) {
                    allItems.add(
                        Pair(
                            PlaylistItem(
                                id = "yt_$videoId",
                                title = title,
                                artist = channelName,
                                mediaUri = "https://www.youtube.com/watch?v=$videoId",
                                sourceType = MediaSourceType.YOUTUBE,
                                artworkUri = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                ytChannelId = channelId
                            ),
                            publishedMs
                        )
                    )
                }
            }

            var filteredItems = if (sinceTimestamp != null) {
                allItems.filter { it.second != null && it.second!! >= sinceTimestamp }.map { it.first }
            } else {
                allItems.map { it.first }
            }

            // 安全防線：若時間因子過濾後為 0 首，但該頻道有影片，至少保留最新 5 首供立即播放
            if (filteredItems.isEmpty() && allItems.isNotEmpty()) {
                filteredItems = allItems.take(5).map { it.first }
            }

            // 併發預解析 RSS 音訊串流 URL (前 20 首)
            val deferred = filteredItems.take(20).map { item ->
                async {
                    val vid = YouTubeAudioExtractor.extractVideoId(item.id)
                    val streamUrl = audioExtractor.extractAudioStreamUrl(vid)
                    if (streamUrl != null) {
                        item.copy(mediaUri = streamUrl)
                    } else {
                        item
                    }
                }
            }
            return@withContext deferred.awaitAll() + filteredItems.drop(20)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext emptyList()
    }
}

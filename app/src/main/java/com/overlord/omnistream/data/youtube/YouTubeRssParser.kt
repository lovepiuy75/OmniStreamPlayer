package com.overlord.omnistream.data.youtube

import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 透過免費、無需 API Key 的 YouTube 頻道 RSS Feed 偵測最新影片
 * URL: https://www.youtube.com/feeds/videos.xml?channel_id={channelId}
 */
class YouTubeRssParser(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun fetchChannelLatestVideos(
        channelId: String,
        channelName: String
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
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

            for (i in 0 until entryNodes.length) {
                val entry = entryNodes.item(i)
                val children = entry.childNodes
                var videoId: String? = null
                var title: String? = null

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName) {
                        "yt:videoId" -> videoId = node.textContent.trim()
                        "title" -> title = node.textContent.trim()
                    }
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

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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * 免 API Key 的 YouTube 播放清單解析器 (透過 YouTube InnerTube 端點)
 */
class YouTubePlaylistParser(
    private val client: OkHttpClient = OkHttpClient(),
    private val audioExtractor: YouTubeAudioExtractor = YouTubeAudioExtractor(client)
) {

    companion object {
        private val PLAYLIST_ID_PATTERN = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)")

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

        val parsedVideos = mutableListOf<Triple<String, String, String>>() // (vid, title, artist)

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            // 1. 優先從 InnerTube JSON 結構中遞迴解析真實標題與創作者 (lockupViewModel & playlistVideoRenderer)
            try {
                val json = JSONObject(body)
                val items = extractPlaylistItemsFromJson(json, playlistTitle)
                parsedVideos.addAll(items)
            } catch (e: Exception) {
                // Ignore json parse error and fallback to regex
            }

            // 2. 若 JSON 結構未取得項目，回退至正則匹配影片 ID
            if (parsedVideos.isEmpty()) {
                val vidPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                val matcher = vidPattern.matcher(body)
                val seen = mutableSetOf<String>()
                var count = 1
                while (matcher.find()) {
                    val vid = matcher.group(1)
                    if (seen.add(vid)) {
                        parsedVideos.add(Triple(vid, "$playlistTitle #$count", playlistTitle))
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 併發預解析前 25 首影片之真實 audio stream URL，確保加入播放清單即可秒播與連續播放
        val deferredList = parsedVideos.take(25).map { (vid, title, artist) ->
            async {
                val directUrl = audioExtractor.extractAudioStreamUrl(vid)
                PlaylistItem(
                    id = "yt_$vid",
                    title = title,
                    artist = artist,
                    mediaUri = directUrl ?: "https://www.youtube.com/watch?v=$vid",
                    sourceType = MediaSourceType.YOUTUBE,
                    artworkUri = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
                )
            }
        }
        val preloaded = deferredList.awaitAll()
        val remaining = parsedVideos.drop(25).map { (vid, title, artist) ->
            PlaylistItem(
                id = "yt_$vid",
                title = title,
                artist = artist,
                mediaUri = "https://www.youtube.com/watch?v=$vid",
                sourceType = MediaSourceType.YOUTUBE,
                artworkUri = "https://img.youtube.com/vi/$vid/hqdefault.jpg"
            )
        }

        return@withContext (preloaded + remaining)
    }

    private fun extractPlaylistItemsFromJson(
        json: JSONObject,
        defaultTitle: String
    ): List<Triple<String, String, String>> {
        val results = mutableListOf<Triple<String, String, String>>()
        val seen = mutableSetOf<String>()

        fun walk(obj: Any) {
            when (obj) {
                is JSONObject -> {
                    if (obj.has("lockupViewModel")) {
                        val lvm = obj.optJSONObject("lockupViewModel")
                        val vid = lvm?.optString("contentId")
                        if (!vid.isNullOrBlank() && seen.add(vid)) {
                            val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                            val title = meta?.optJSONObject("title")?.optString("content").orEmpty().ifBlank { "$defaultTitle #${seen.size}" }
                            val artist = meta?.optJSONObject("metadata")
                                ?.optJSONObject("contentMetadataViewModel")
                                ?.optJSONArray("metadataRows")
                                ?.optJSONObject(0)
                                ?.optJSONArray("metadataParts")
                                ?.optJSONObject(0)
                                ?.optJSONObject("text")
                                ?.optString("content").orEmpty().ifBlank { defaultTitle }
                            results.add(Triple(vid, title, artist))
                        }
                    } else if (obj.has("playlistVideoRenderer")) {
                        val pvr = obj.optJSONObject("playlistVideoRenderer")
                        val vid = pvr?.optString("videoId")
                        if (!vid.isNullOrBlank() && seen.add(vid)) {
                            val titleObj = pvr.optJSONObject("title")
                            val title = titleObj?.optString("simpleText").orEmpty().ifBlank {
                                titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                            }.ifBlank { "$defaultTitle #${seen.size}" }
                            val artist = pvr.optJSONObject("shortBylineText")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text").orEmpty().ifBlank { defaultTitle }
                            results.add(Triple(vid, title, artist))
                        }
                    }
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        obj.opt(key)?.let { walk(it) }
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until obj.length()) {
                        obj.opt(i)?.let { walk(it) }
                    }
                }
            }
        }

        walk(json)
        return results
    }
}

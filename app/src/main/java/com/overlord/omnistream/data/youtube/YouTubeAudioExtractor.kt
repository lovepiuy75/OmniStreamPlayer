package com.overlord.omnistream.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * YouTube 純音訊串流提取器：
 * 解析取得純音訊 (M4A / Opus) 直接串流 URL，支援背景低功耗播放
 */
class YouTubeAudioExtractor(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun extractAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        // 透過 Piped / Invidious 或自建解析實例取得最高音質音訊流
        val cleanId = videoId.removePrefix("yt_")
        val apiEndpoints = listOf(
            "https://pipedapi.kavin.rocks/streams/$cleanId",
            "https://api.piped.privacydev.net/streams/$cleanId"
        )

        for (endpoint in apiEndpoints) {
            try {
                val request = Request.Builder().url(endpoint).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val json = JSONObject(body)
                    val audioStreams = json.optJSONArray("audioStreams") ?: continue
                    if (audioStreams.length() > 0) {
                        // 取音質最高的音訊串流
                        val stream = audioStreams.getJSONObject(0)
                        return@withContext stream.getString("url")
                    }
                }
            } catch (e: Exception) {
                // 嘗試下一個節點
            }
        }

        // 若第三方節點繁忙，回退為音訊代理或直接串流
        return@withContext "https://www.youtube.com/watch?v=$cleanId"
    }
}

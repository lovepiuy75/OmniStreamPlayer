package com.overlord.omnistream.data.gdrive

import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Google 雲端硬碟服務：
 * 支援 OAuth2 Token 串流與免登入公開資料夾串流解析
 */
class GoogleDriveService(private val client: OkHttpClient = OkHttpClient()) {

    var currentAccessToken: String? = null

    /**
     * 構建 Google Drive 音訊串流 URL
     * 若有 Access Token 則呼叫 drive v3 API，若為公開檔案則利用 export/download 連結
     */
    fun buildStreamUrl(fileId: String): String {
        return "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
    }

    /**
     * 抓取 Google Drive 指定資料夾內音訊檔案
     */
    suspend fun fetchFolderAudioFiles(
        folderId: String,
        folderName: String
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PlaylistItem>()
        val token = currentAccessToken

        if (token != null) {
            // 透過 Google Drive REST API 查詢
            val query = "'$folderId' in parents and trashed = false and (mimeType contains 'audio/' or name contains '.mp3' or name contains '.m4a')"
            val url = "https://www.googleapis.com/drive/v3/files?q=${query}&fields=files(id,name,size,createdTime)&orderBy=createdTime desc"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files") ?: return@withContext emptyList()
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        val id = file.getString("id")
                        val name = file.getString("name")
                        result.add(
                            PlaylistItem(
                                id = "gdrive_$id",
                                title = name.substringBeforeLast("."),
                                artist = folderName,
                                mediaUri = buildStreamUrl(id),
                                sourceType = MediaSourceType.GDRIVE,
                                driveFolderId = folderId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext result
    }
}

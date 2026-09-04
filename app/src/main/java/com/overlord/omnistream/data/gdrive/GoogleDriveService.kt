package com.overlord.omnistream.data.gdrive

import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Google 雲端硬碟服務：
 * 支援 OAuth2 Token 串流，以及免登入公開資料夾爬取與 HTTP 206 Range 直接串流
 */
class GoogleDriveService(private val client: OkHttpClient = OkHttpClient()) {

    var currentAccessToken: String? = null

    companion object {
        private val FOLDER_ID_PATTERN = Pattern.compile("(?:folders/|[?&]id=)([a-zA-Z0-9_-]{25,})")

        /**
          * 從完整網址或純文字中精準提取 Google Drive Folder ID
          */
        fun extractFolderId(input: String): String {
            val trimmed = input.trim()
            val matcher = FOLDER_ID_PATTERN.matcher(trimmed)
            return if (matcher.find()) {
                matcher.group(1) ?: matcher.group()
            } else {
                // 若本身為純 ID
                trimmed.substringBefore("?").substringBefore("&")
            }
        }
    }

    /**
     * 構建公開檔案直接串流 URL（支援 ExoPlayer HTTP Range 206）
     */
    fun buildPublicStreamUrl(fileId: String): String {
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    /**
     * 抓取 Google Drive 指定資料夾內音訊檔案
     */
    suspend fun fetchFolderAudioFiles(
        folderIdOrUrl: String,
        folderName: String
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val folderId = extractFolderId(folderIdOrUrl)
        val token = currentAccessToken

        // 若有 Token 優先使用官方 API
        if (token != null) {
            val apiFiles = fetchViaOfficialApi(folderId, folderName, token)
            if (apiFiles.isNotEmpty()) return@withContext apiFiles
        }

        // 否則使用免登入公開資料夾解析器
        return@withContext fetchPublicFolderAudioFiles(folderId, folderName)
    }

    private fun fetchViaOfficialApi(
        folderId: String,
        folderName: String,
        token: String
    ): List<PlaylistItem> {
        val result = mutableListOf<PlaylistItem>()
        val query = "'$folderId' in parents and trashed = false and (mimeType contains 'audio/' or name contains '.mp3' or name contains '.m4a')"
        val url = "https://www.googleapis.com/drive/v3/files?q=${query}&fields=files(id,name,size,createdTime)&orderBy=name asc"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                val files = json.optJSONArray("files") ?: return emptyList()
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val id = file.getString("id")
                    val name = file.getString("name")
                    result.add(
                        PlaylistItem(
                            id = "gdrive_$id",
                            title = name.substringBeforeLast("."),
                            artist = folderName,
                            mediaUri = "https://www.googleapis.com/drive/v3/files/$id?alt=media",
                            sourceType = MediaSourceType.GDRIVE,
                            driveFolderId = folderId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * 免登入公開資料夾爬取：解析 Google Drive 資料夾網頁並提取檔案清單
     */
    suspend fun fetchPublicFolderAudioFiles(
        folderId: String,
        folderName: String
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PlaylistItem>()
        val url = "https://drive.google.com/drive/folders/$folderId"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext emptyList()

            // 1. 嘗試由 window['_DRIVE_ivd'] 提取檔案列表
            val ivdPattern = Pattern.compile("""window\['_DRIVE_ivd'\]\s*=\s*'([^']+)'""")
            val ivdMatcher = ivdPattern.matcher(html)
            if (ivdMatcher.find()) {
                val rawEscaped = ivdMatcher.group(1)
                val decoded = unescapeUnicode(rawEscaped)
                try {
                    val root = JSONArray(decoded)
                    extractFilesFromJson(root, folderId, folderName, result)
                    if (result.isNotEmpty()) return@withContext result
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 備用方案：透過 DOM data-id 與 tooltip 標籤提取
            val fallbackPattern = Pattern.compile("""data-id="([a-zA-Z0-9_-]{25,})"[^>]*data-tooltip="([^"]+\.(?:mp3|m4a|wav|aac|ogg))""")
            val matcher = fallbackPattern.matcher(html)
            val seenIds = mutableSetOf<String>()
            while (matcher.find()) {
                val fileId = matcher.group(1)
                val fileName = matcher.group(2)
                if (seenIds.add(fileId)) {
                    result.add(
                        PlaylistItem(
                            id = "gdrive_$fileId",
                            title = fileName.substringBeforeLast("."),
                            artist = folderName,
                            mediaUri = buildPublicStreamUrl(fileId),
                            sourceType = MediaSourceType.GDRIVE,
                            driveFolderId = folderId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext result
    }

    private fun extractFilesFromJson(
        node: Any?,
        folderId: String,
        folderName: String,
        results: MutableList<PlaylistItem>
    ) {
        when (node) {
            is JSONArray -> {
                if (node.length() >= 4) {
                    val first = node.opt(0)
                    val third = node.opt(2)
                    val fourth = node.opt(3)
                    if (first is String && third is String && fourth is String) {
                        val isAudio = fourth.startsWith("audio/") ||
                                third.endsWith(".mp3", ignoreCase = true) ||
                                third.endsWith(".m4a", ignoreCase = true) ||
                                third.endsWith(".wav", ignoreCase = true)
                        if (isAudio && first.length >= 25) {
                            results.add(
                                PlaylistItem(
                                    id = "gdrive_$first",
                                    title = third.substringBeforeLast("."),
                                    artist = folderName,
                                    mediaUri = buildPublicStreamUrl(first),
                                    sourceType = MediaSourceType.GDRIVE,
                                    driveFolderId = folderId
                                )
                            )
                        }
                    }
                }
                for (i in 0 until node.length()) {
                    extractFilesFromJson(node.opt(i), folderId, folderName, results)
                }
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractFilesFromJson(node.opt(keys.next()), folderId, folderName, results)
                }
            }
        }
    }

    private fun unescapeUnicode(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (input[i] == '\\') {
                if (i + 1 < input.length) {
                    when (input[i + 1]) {
                        'x' -> {
                            if (i + 3 < input.length) {
                                val hex = input.substring(i + 2, i + 4)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                                continue
                            }
                        }
                        'u' -> {
                            if (i + 5 < input.length) {
                                val hex = input.substring(i + 2, i + 6)
                                sb.append(hex.toInt(16).toChar())
                                i += 6
                                continue
                            }
                        }
                        '/' -> { sb.append('/'); i += 2; continue }
                        '"' -> { sb.append('"'); i += 2; continue }
                        '\\' -> { sb.append('\\'); i += 2; continue }
                        'n' -> { sb.append('\n'); i += 2; continue }
                        'r' -> { sb.append('\r'); i += 2; continue }
                        't' -> { sb.append('\t'); i += 2; continue }
                    }
                }
            }
            sb.append(input[i])
            i++
        }
        return sb.toString()
    }
}

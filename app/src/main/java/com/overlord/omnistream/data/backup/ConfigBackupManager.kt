package com.overlord.omnistream.data.backup

import android.content.Context
import android.os.Environment
import com.overlord.omnistream.data.local.AppDatabase
import com.overlord.omnistream.data.local.entity.PlaylistGroupEntity
import com.overlord.omnistream.data.local.entity.PlaylistItemEntity
import com.overlord.omnistream.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 使用者設定與訂閱清單自動備份/還原管理器
 * 確保移除 App 後重新安裝時，能無縫接回所有 Google Drive、YouTube 頻道/清單與播放群組
 */
class ConfigBackupManager(private val context: Context, private val database: AppDatabase) {

    companion object {
        const val BACKUP_FILE_NAME = "omnistream_backup.json"
    }

    fun getBackupFiles(): List<File> {
        val files = mutableListOf<File>()
        // 1. 公共 Download 目錄（App 移除後檔案仍長存，重灌後可直接接回）
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null) {
                downloadDir.mkdirs()
                files.add(File(downloadDir, BACKUP_FILE_NAME))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 外部應用文件目錄
        context.getExternalFilesDir(null)?.let {
            files.add(File(it, BACKUP_FILE_NAME))
        }

        // 3. 內部私有目錄
        files.add(File(context.filesDir, BACKUP_FILE_NAME))

        return files
    }

    /**
     * 建立完整設定與清單備份
     */
    suspend fun createBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val subs = database.subscriptionDao().getAll()
            val groups = database.playlistGroupDao().getAll()
            val items = database.playlistDao().getAll()

            val rootJson = JSONObject()
            rootJson.put("version", 1)
            rootJson.put("timestamp", System.currentTimeMillis())

            // 1. Subscriptions
            val subsArray = JSONArray()
            for (sub in subs) {
                val obj = JSONObject()
                obj.put("id", sub.id)
                obj.put("name", sub.name)
                obj.put("type", sub.type)
                obj.put("publicUrl", sub.publicUrl)
                obj.put("lastSyncedTime", sub.lastSyncedTime)
                obj.put("autoAddToPlaylist", sub.autoAddToPlaylist)
                obj.put("sinceTimestamp", sub.sinceTimestamp ?: -1L)
                obj.put("isPlaylist", sub.isPlaylist)
                subsArray.put(obj)
            }
            rootJson.put("subscriptions", subsArray)

            // 2. Playlist Groups
            val groupsArray = JSONArray()
            for (g in groups) {
                val obj = JSONObject()
                obj.put("id", g.id)
                obj.put("name", g.name)
                obj.put("createdAt", g.createdAt)
                groupsArray.put(obj)
            }
            rootJson.put("playlist_groups", groupsArray)

            // 3. Playlist Items
            val itemsArray = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("title", item.title)
                obj.put("artist", item.artist)
                obj.put("durationMs", item.durationMs)
                obj.put("mediaUri", item.mediaUri)
                obj.put("sourceType", item.sourceType)
                obj.put("artworkUri", item.artworkUri)
                obj.put("driveFolderId", item.driveFolderId)
                obj.put("ytChannelId", item.ytChannelId)
                obj.put("sortOrder", item.sortOrder)
                obj.put("addedTime", item.addedTime)
                obj.put("playlistGroupId", item.playlistGroupId)
                itemsArray.put(obj)
            }
            rootJson.put("playlist_items", itemsArray)

            val jsonString = rootJson.toString(2)
            for (file in getBackupFiles()) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(jsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 若本機存在備份且目前資料庫為空，無縫還原
     */
    suspend fun restoreBackupIfAvailable(): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val targetFile = getBackupFiles().firstOrNull { it.exists() && it.length() > 0 } ?: return@withContext 0

        try {
            val content = targetFile.readText(Charsets.UTF_8)
            val rootJson = JSONObject(content)

            // 1. 還原 Subscriptions
            val subsArray = rootJson.optJSONArray("subscriptions")
            if (subsArray != null) {
                for (i in 0 until subsArray.length()) {
                    val obj = subsArray.getJSONObject(i)
                    val sinceTs = obj.optLong("sinceTimestamp", -1L).takeIf { it > 0 }
                    val entity = SubscriptionEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = obj.getString("type"),
                        publicUrl = obj.optString("publicUrl").takeIf { it.isNotBlank() },
                        lastSyncedTime = obj.optLong("lastSyncedTime", 0L),
                        autoAddToPlaylist = obj.optBoolean("autoAddToPlaylist", true),
                        sinceTimestamp = sinceTs,
                        isPlaylist = obj.optBoolean("isPlaylist", false)
                    )
                    database.subscriptionDao().insert(entity)
                    restoredCount++
                }
            }

            // 2. 還原 Groups
            val groupsArray = rootJson.optJSONArray("playlist_groups")
            if (groupsArray != null) {
                for (i in 0 until groupsArray.length()) {
                    val obj = groupsArray.getJSONObject(i)
                    val entity = PlaylistGroupEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    database.playlistGroupDao().insert(entity)
                }
            }

            // 3. 還原 Playlist Items
            val itemsArray = rootJson.optJSONArray("playlist_items")
            if (itemsArray != null) {
                val items = mutableListOf<PlaylistItemEntity>()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    items.add(
                        PlaylistItemEntity(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            durationMs = obj.optLong("durationMs", 0L),
                            mediaUri = obj.getString("mediaUri"),
                            sourceType = obj.optString("sourceType", "LOCAL"),
                            artworkUri = obj.optString("artworkUri").takeIf { it.isNotBlank() },
                            driveFolderId = obj.optString("driveFolderId").takeIf { it.isNotBlank() },
                            ytChannelId = obj.optString("ytChannelId").takeIf { it.isNotBlank() },
                            sortOrder = obj.optInt("sortOrder", i),
                            addedTime = obj.optLong("addedTime", System.currentTimeMillis()),
                            playlistGroupId = obj.optString("playlistGroupId", "default")
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    database.playlistDao().insertAll(items)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        restoredCount
    }
}

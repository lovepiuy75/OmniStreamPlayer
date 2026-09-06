package com.overlord.omnistream.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem
import com.overlord.omnistream.data.gdrive.GoogleDriveService
import com.overlord.omnistream.data.local.AppDatabase
import com.overlord.omnistream.data.local.entity.PlaybackStateEntity
import com.overlord.omnistream.data.local.entity.PlaylistGroupEntity
import com.overlord.omnistream.data.local.entity.PlaylistItemEntity
import com.overlord.omnistream.data.youtube.YouTubeAudioExtractor
import com.overlord.omnistream.data.youtube.YouTubePlaylistParser
import com.overlord.omnistream.data.youtube.YouTubeRssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlayerRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    val gdriveService by lazy { GoogleDriveService() }
    val ytRssParser by lazy { YouTubeRssParser() }
    val ytPlaylistParser by lazy { YouTubePlaylistParser() }
    val ytAudioExtractor by lazy { YouTubeAudioExtractor() }

    val playlistDao by lazy { database.playlistDao() }
    val groupDao by lazy { database.playlistGroupDao() }
    val playbackStateDao by lazy { database.playbackStateDao() }
    val subscriptionDao by lazy { database.subscriptionDao() }
    val backupManager by lazy { com.overlord.omnistream.data.backup.ConfigBackupManager(context, database) }


    // 播放清單群組
    fun getPlaylistGroupsFlow(): Flow<List<PlaylistGroupEntity>> = groupDao.getAllFlow()

    suspend fun createPlaylistGroup(id: String, name: String) {
        groupDao.insert(PlaylistGroupEntity(id = id, name = name))
        backupManager.createBackup()
    }

    suspend fun deletePlaylistGroup(id: String) {
        groupDao.deleteById(id)
        playlistDao.clearGroup(id)
        backupManager.createBackup()
    }

    // 取得指定群組的播放清單
    fun getPlaylistFlow(groupId: String = "default"): Flow<List<PlaylistItem>> {
        return playlistDao.getByGroupFlow(groupId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getPlaylistItems(groupId: String = "default"): List<PlaylistItem> {
        return playlistDao.getByGroup(groupId).map { it.toDomain() }
    }

    suspend fun addItemsToPlaylist(items: List<PlaylistItem>, groupId: String = "default") {
        val currentMax = playlistDao.getMaxSortOrder(groupId) ?: 0
        val entities = items.mapIndexed { index, item ->
            PlaylistItemEntity.fromDomain(item, currentMax + index + 1, groupId)
        }
        playlistDao.insertAll(entities)
        backupManager.createBackup()
    }

    suspend fun removeItemFromPlaylist(id: String) {
        playlistDao.deleteById(id)
        backupManager.createBackup()
    }

    suspend fun clearPlaylist(groupId: String = "default") {
        playlistDao.clearGroup(groupId)
        backupManager.createBackup()
    }

    suspend fun updateItemMediaUri(id: String, mediaUri: String) {
        playlistDao.updateMediaUri(id, mediaUri)
    }

    suspend fun updateItemDetails(id: String, title: String, artist: String, mediaUri: String) {
        playlistDao.updateItemDetails(id, title, artist, mediaUri)
    }

    // 本機音訊掃描 (MediaStore)
    suspend fun scanLocalAudioFiles(): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaylistItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val query = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )
        query?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "未知曲目"
                val artist = cursor.getString(artistCol) ?: "本機音訊"
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                list.add(
                    PlaylistItem(
                        id = "local_$id",
                        title = title,
                        artist = artist,
                        durationMs = duration,
                        mediaUri = contentUri.toString(),
                        sourceType = MediaSourceType.LOCAL
                    )
                )
            }
        }
        return@withContext list
    }

    // 斷點續播狀態
    fun getPlaybackStateFlow(): Flow<PlaybackStateEntity?> = playbackStateDao.getStateFlow()
    suspend fun getPlaybackState(): PlaybackStateEntity? = playbackStateDao.getState()
    suspend fun savePlaybackState(currentId: String?, positionMs: Long, isPlaying: Boolean) {
        playbackStateDao.saveState(
            PlaybackStateEntity(
                currentItemId = currentId,
                currentPositionMs = positionMs,
                isPlaying = isPlaying
            )
        )
    }
}

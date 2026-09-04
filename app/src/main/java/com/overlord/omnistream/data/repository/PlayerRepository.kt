package com.overlord.omnistream.data.repository

import android.content.Context
import com.overlord.omnistream.core.model.PlaylistItem
import com.overlord.omnistream.data.gdrive.GoogleDriveService
import com.overlord.omnistream.data.local.AppDatabase
import com.overlord.omnistream.data.local.entity.PlaybackStateEntity
import com.overlord.omnistream.data.local.entity.PlaylistItemEntity
import com.overlord.omnistream.data.youtube.YouTubeAudioExtractor
import com.overlord.omnistream.data.youtube.YouTubeRssParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlayerRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    val gdriveService = GoogleDriveService()
    val ytRssParser = YouTubeRssParser()
    val ytAudioExtractor = YouTubeAudioExtractor()

    private val playlistDao = database.playlistDao()
    private val playbackStateDao = database.playbackStateDao()
    private val subscriptionDao = database.subscriptionDao()

    // 取得播放清單 Flow
    fun getPlaylistFlow(): Flow<List<PlaylistItem>> {
        return playlistDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getAllPlaylistItems(): List<PlaylistItem> {
        return playlistDao.getAll().map { it.toDomain() }
    }

    suspend fun addItemsToPlaylist(items: List<PlaylistItem>) {
        val currentMax = playlistDao.getMaxSortOrder() ?: 0
        val entities = items.mapIndexed { index, item ->
            PlaylistItemEntity.fromDomain(item, currentMax + index + 1)
        }
        playlistDao.insertAll(entities)
    }

    suspend fun removeItemFromPlaylist(id: String) {
        playlistDao.deleteById(id)
    }

    suspend fun clearPlaylist() {
        playlistDao.clear()
    }

    // 斷點續播狀態記憶
    fun getPlaybackStateFlow(): Flow<PlaybackStateEntity?> {
        return playbackStateDao.getStateFlow()
    }

    suspend fun getPlaybackState(): PlaybackStateEntity? {
        return playbackStateDao.getState()
    }

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

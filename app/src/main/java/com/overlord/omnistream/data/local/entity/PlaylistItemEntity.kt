package com.overlord.omnistream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.overlord.omnistream.core.model.MediaSourceType
import com.overlord.omnistream.core.model.PlaylistItem

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val mediaUri: String,
    val sourceType: String,
    val artworkUri: String?,
    val driveFolderId: String?,
    val ytChannelId: String?,
    val sortOrder: Int,
    val addedTime: Long
) {
    fun toDomain(): PlaylistItem {
        return PlaylistItem(
            id = id,
            title = title,
            artist = artist,
            durationMs = durationMs,
            mediaUri = mediaUri,
            sourceType = MediaSourceType.valueOf(sourceType),
            artworkUri = artworkUri,
            driveFolderId = driveFolderId,
            ytChannelId = ytChannelId,
            addedTime = addedTime
        )
    }

    companion object {
        fun fromDomain(item: PlaylistItem, sortOrder: Int): PlaylistItemEntity {
            return PlaylistItemEntity(
                id = item.id,
                title = item.title,
                artist = item.artist,
                durationMs = item.durationMs,
                mediaUri = item.mediaUri,
                sourceType = item.sourceType.name,
                artworkUri = item.artworkUri,
                driveFolderId = item.driveFolderId,
                ytChannelId = item.ytChannelId,
                sortOrder = sortOrder,
                addedTime = item.addedTime
            )
        }
    }
}

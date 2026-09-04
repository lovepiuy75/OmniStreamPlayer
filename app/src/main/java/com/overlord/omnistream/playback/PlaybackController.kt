package com.overlord.omnistream.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前端 UI 與 Media3 MediaSession 通訊的控制器
 */
@OptIn(UnstableApi::class)
class PlaybackController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem = _currentMediaItem.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    fun connect(onConnected: () -> Unit = {}) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, OmniMediaSessionService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            setupPlayerListener()
            onConnected()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _currentMediaItem.value = controller.currentMediaItem

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaItem.value = mediaItem
                _durationMs.value = controller.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _durationMs.value = controller.duration.coerceAtLeast(0L)
            }
        })
    }

    fun play() = mediaController?.play()
    fun pause() = mediaController?.pause()
    fun seekTo(positionMs: Long) = mediaController?.seekTo(positionMs)
    fun skipToNext() = mediaController?.seekToNextMediaItem()
    fun skipToPrevious() = mediaController?.seekToPreviousMediaItem()

    fun setPlaylistAndPlay(items: List<PlaylistItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        val controller = mediaController ?: return
        val mediaItems = items.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(item.mediaUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .build()
                )
                .build()
        }
        controller.setMediaItems(mediaItems, startIndex, startPositionMs)
        controller.prepare()
        controller.play()
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

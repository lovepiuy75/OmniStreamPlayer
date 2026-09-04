package com.overlord.omnistream.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.overlord.omnistream.core.model.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前端 UI 與 Media3 MediaSession 通訊的控制器 (保證於 Main Thread 調用以防止線程違規崩潰)
 */
@OptIn(UnstableApi::class)
class PlaybackController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem = _currentMediaItem.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    fun connect(onConnected: () -> Unit = {}) {
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, OmniMediaSessionService::class.java)
            )
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    setupPlayerListener()
                    onConnected()
                } catch (e: Throwable) {
                    Log.e("PlaybackController", "Failed to connect to MediaSession", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Throwable) {
            Log.e("PlaybackController", "Error initializing SessionToken or Builder", e)
        }
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

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    fun play() = runOnMainThread { mediaController?.play() }
    fun pause() = runOnMainThread { mediaController?.pause() }
    fun seekTo(positionMs: Long) = runOnMainThread { mediaController?.seekTo(positionMs) }
    fun skipToNext() = runOnMainThread { mediaController?.seekToNextMediaItem() }
    fun skipToPrevious() = runOnMainThread { mediaController?.seekToPreviousMediaItem() }

    fun setPlaylistAndPlay(items: List<PlaylistItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        runOnMainThread {
            val controller = mediaController ?: return@runOnMainThread
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
    }

    fun release() {
        runOnMainThread {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }
}


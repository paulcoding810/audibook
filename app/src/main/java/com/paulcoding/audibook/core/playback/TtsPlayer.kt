package com.paulcoding.audibook.core.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Exposes [ReadAloudController] to Media3 so the session gets notification, lock-screen and headset controls. */
@UnstableApi
class TtsPlayer(private val controller: ReadAloudController) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch { controller.state.collect { invalidateState() } }
    }

    override fun getState(): State {
        val playback = controller.state.value
        val book = playback.book
        val commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_STOP,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_GET_TIMELINE,
            )
            .apply {
                if (book != null) {
                    add(COMMAND_SEEK_TO_NEXT)
                    add(COMMAND_SEEK_TO_PREVIOUS)
                    add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
            }
            .build()
        val builder = State.Builder().setAvailableCommands(commands)
        if (book == null) return builder.setPlaybackState(STATE_IDLE).build()

        val metadata = MediaMetadata.Builder()
            .setTitle(playback.chapterTitle ?: book.title)
            .setArtist(book.title)
            .setAlbumTitle(book.title)
            .setAlbumArtist(book.author)
            .setArtworkUri(book.coverPath?.let { Uri.fromFile(File(it)) })
            .build()
        val item = MediaItemData.Builder(book.id)
            .setMediaItem(MediaItem.Builder().setMediaId(book.id).setMediaMetadata(metadata).build())
            .build()
        return builder
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(STATE_READY)
            .setPlayWhenReady(playback.isPlaying, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) controller.play() else controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> controller.nextChapter()
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.previousChapter()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }
}

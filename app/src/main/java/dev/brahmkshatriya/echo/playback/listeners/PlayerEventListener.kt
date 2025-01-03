package dev.brahmkshatriya.echo.playback.listeners

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaSession
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.TrackLikeClient
import dev.brahmkshatriya.echo.extensions.getExtension
import dev.brahmkshatriya.echo.extensions.isClient
import dev.brahmkshatriya.echo.playback.Current
import dev.brahmkshatriya.echo.playback.MediaItemUtils
import dev.brahmkshatriya.echo.playback.MediaItemUtils.extensionId
import dev.brahmkshatriya.echo.playback.MediaItemUtils.isLoaded
import dev.brahmkshatriya.echo.playback.MediaItemUtils.retries
import dev.brahmkshatriya.echo.playback.PlayerCommands.getLikeButton
import dev.brahmkshatriya.echo.playback.PlayerCommands.getRepeatButton
import dev.brahmkshatriya.echo.playback.ResumptionUtils
import dev.brahmkshatriya.echo.playback.StreamableLoadingException
import dev.brahmkshatriya.echo.ui.exception.AppException
import kotlinx.coroutines.flow.MutableStateFlow

class PlayerEventListener(
    private val context: Context,
    private val session: MediaSession,
    private val currentFlow: MutableStateFlow<Current?>,
    private val extensionList: MutableStateFlow<List<MusicExtension>?>,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable { updateCurrent() }

    private fun updateCurrent() {
        handler.removeCallbacks(runnable)
        ResumptionUtils.saveCurrentPos(context, player.currentPosition)
        handler.postDelayed(runnable, 1000)
    }

    val player get() = session.player

    private fun updateCustomLayout() {
        val item = player.currentMediaItem ?: return
        val supportsLike = extensionList.getExtension(item.extensionId)?.isClient<TrackLikeClient>()
            ?: false

        val commandButtons = listOfNotNull(
            getRepeatButton(context, player.repeatMode),
            getLikeButton(context, item).takeIf { supportsLike }
        )
        session.setCustomLayout(commandButtons)
    }

    private fun updateCurrentFlow() {
        currentFlow.value = player.currentMediaItem?.let {
            val isPlaying = player.isPlaying && player.playbackState == Player.STATE_READY
            Current(player.currentMediaItemIndex, it, it.isLoaded, isPlaying)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateCurrentFlow()
        updateCustomLayout()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        updateCurrentFlow()
        updateCustomLayout()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        updateCurrentFlow()
        ResumptionUtils.saveQueue(context, player)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateCustomLayout()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateCurrentFlow()
        updateCustomLayout()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateCurrentFlow()
        updateCurrent()
    }

    private val maxRetries = 1
    private var last: Pair<MediaItem, Throwable>? = null
    override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause?.cause
        if (cause !is StreamableLoadingException) return
        if (cause.cause !is AppException.Other) return
        val mediaItem = cause.mediaItem
        if (last?.first?.mediaId != mediaItem.mediaId && last?.second == cause.cause.cause)
            return
        val index = player.currentMediaItemIndex.takeIf {
            player.currentMediaItem?.mediaId == mediaItem.mediaId
        } ?: return
        last = mediaItem to cause.cause.cause
        val retries = mediaItem.retries
        handler.post {
            if (retries >= maxRetries) {
                val hasMore = index < player.mediaItemCount - 1
                if (!hasMore) return@post
                player.seekToNextMediaItem()
            } else {
                val new = MediaItemUtils.withRetry(mediaItem)
                player.replaceMediaItem(index, new)
            }
            player.play()
        }
    }
}

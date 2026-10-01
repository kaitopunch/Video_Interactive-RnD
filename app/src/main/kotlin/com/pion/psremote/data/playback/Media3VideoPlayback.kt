package com.pion.psremote.data.playback

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.util.EventLogger
import androidx.tracing.Trace
import com.pion.psremote.BuildConfig
import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.playback.VideoPlayback
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * [VideoPlayback] on a Media3 [ExoPlayer]. One of the two files that import `androidx.media3.**`;
 * the other is the surface composable that draws [player] (LLM.md §4).
 *
 * Cues are [PlayerMessage]s pinned to a video position. The player delivers one when *playback* reaches
 * that position, which is what makes a step's slow phase immune to buffering and to time spent in the
 * background (README §4, D5). Delivery is on the main looper, the same thread the ViewModel runs on.
 */
@OptIn(UnstableApi::class)
class Media3VideoPlayback(context: Context) : VideoPlayback {

    /**
     * Speed changes go to the platform `AudioTrack` instead of Media3's own time-stretcher. The default
     * applies a new speed only after the audio already buffered at the old one has played out: measured
     * on an SM-A165F, the first ~0.66 s of a 0.5× phase still ran at 1×, so a 2 000 ms slow phase lasted
     * 1 340 ms of real time. With the platform path the change is immediate.
     */
    val player: ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        DefaultRenderersFactory(context.applicationContext).setEnableAudioTrackPlaybackParams(true),
    ).build()

    /** Unlimited, so an event raised while the collector is busy is queued, never dropped. */
    private val eventChannel = Channel<PlaybackEvent>(Channel.UNLIMITED)
    override val events: Flow<PlaybackEvent> = eventChannel.receiveAsFlow()

    private var pendingCue: PlayerMessage? = null
    private var hasReportedPrepared = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                // READY recurs after every seek and rebuffer; only the first one means "prepared".
                Player.STATE_READY -> if (!hasReportedPrepared) {
                    hasReportedPrepared = true
                    Trace.endAsyncSection(TRACE_DEMO_READY, TRACE_COOKIE)
                    emit(PlaybackEvent.Prepared)
                }
                Player.STATE_ENDED -> emit(PlaybackEvent.Ended)
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            emit(PlaybackEvent.Failed(error.errorCodeName))
        }
    }

    init {
        // Ends at the first READY: the wait a user sits through between tapping a game and its first frame —
        // player built, script read and checked, video prepared. Read by `OpenDemoBenchmark` (LLM.md §10).
        // A screen left before its first frame leaves it open: trace tools show it unfinished, and the
        // benchmark's query skips unfinished slices.
        Trace.beginAsyncSection(TRACE_DEMO_READY, TRACE_COOKIE)
        player.addListener(listener)
        // Debug builds log every state, speed and play/pause change with its media position (tag
        // "EventLogger"), which is how a stop point is checked against its stopPositionMs on a device.
        if (BuildConfig.DEBUG) player.addAnalyticsListener(EventLogger())
    }

    override fun prepare(videoUri: String) {
        hasReportedPrepared = false
        player.playWhenReady = false
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    override fun scheduleCue(positionMs: Long) {
        cancelCue()
        if (positionMs <= player.currentPosition + CUE_GUARD_MS) {
            emit(PlaybackEvent.CueReached(positionMs))
            return
        }
        pendingCue = player.createMessage { _, _ -> emit(PlaybackEvent.CueReached(positionMs)) }
            .setPosition(positionMs)
            .setLooper(Looper.getMainLooper())
            .setDeleteAfterDelivery(true)
            .send()
    }

    override fun cancelCue() {
        pendingCue?.cancel()
        pendingCue = null
    }

    /** Called once, by the owner of this object, when the screen that used it is gone for good. */
    fun release() {
        cancelCue()
        player.removeListener(listener)
        player.release()
        eventChannel.close()
    }

    private fun emit(event: PlaybackEvent) {
        eventChannel.trySend(event)
    }

    private companion object {
        /**
         * How far ahead of the main thread's view of the position a cue must be to be left to the player.
         * The playback thread runs ahead of `currentPosition` by up to a frame or two, and a message whose
         * position it has already passed is never delivered: a stop 15 ms after its trigger was skippable,
         * and the video then never paused. Firing a cue this early instead is invisible.
         */
        const val CUE_GUARD_MS = 40L

        const val TRACE_DEMO_READY = "PsRemote.demoReady"

        /** One player per screen, so one section open at a time: a fixed cookie is enough. */
        const val TRACE_COOKIE = 1
    }
}

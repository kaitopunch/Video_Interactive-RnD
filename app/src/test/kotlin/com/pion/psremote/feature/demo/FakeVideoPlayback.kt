package com.pion.psremote.feature.demo

import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.playback.VideoPlayback
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class FakeVideoPlayback : VideoPlayback {
    private val eventChannel = Channel<PlaybackEvent>(Channel.UNLIMITED)
    override val events = eventChannel.receiveAsFlow()

    var preparedUri: String? = null
        private set
    var isPlaying = false
        private set
    private var recordedSpeed = 1f
    val speed: Float get() = recordedSpeed
    private var recordedMuted = false
    val muted: Boolean get() = recordedMuted
    var lastSeekMs: Long? = null
        private set
    var pendingCueMs: Long? = null
        private set
    var cancelCueCount = 0
        private set
    val calls = mutableListOf<String>()

    fun emit(event: PlaybackEvent) {
        if (event is PlaybackEvent.CueReached && event.positionMs == pendingCueMs) pendingCueMs = null
        check(eventChannel.trySend(event).isSuccess)
    }

    /** Ends [events] with [cause], as a player that crashed or was released under its collector would. */
    fun breakEvents(cause: Throwable) {
        eventChannel.close(cause)
    }

    override fun prepare(videoUri: String) {
        preparedUri = videoUri
        isPlaying = false
        calls += "prepare:$videoUri"
    }

    override fun play() {
        isPlaying = true
        calls += "play"
    }

    override fun pause() {
        isPlaying = false
        calls += "pause"
    }

    override fun seekTo(positionMs: Long) {
        lastSeekMs = positionMs
        calls += "seekTo:$positionMs"
    }

    override fun setSpeed(speed: Float) {
        recordedSpeed = speed
        calls += "setSpeed:$speed"
    }

    override fun setMuted(muted: Boolean) {
        recordedMuted = muted
        calls += "setMuted:$muted"
    }

    override fun scheduleCue(positionMs: Long) {
        pendingCueMs = positionMs
        calls += "scheduleCue:$positionMs"
    }

    override fun cancelCue() {
        pendingCueMs = null
        cancelCueCount++
        calls += "cancelCue"
    }
}

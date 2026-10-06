package com.pion.psremote.domain.playback

import kotlinx.coroutines.flow.Flow

/**
 * The port the demo ViewModel drives the video through. No Media3 type crosses it, so the ViewModel
 * stays free of platform imports and its tests run against a fake (LLM.md §2).
 *
 * The implementation is `data/playback/Media3VideoPlayback.kt`. Every method is called on the main thread.
 */
interface VideoPlayback {

    /** Events in the order the player raised them. Exactly one collector: the screen's ViewModel. */
    val events: Flow<PlaybackEvent>

    /** Loads [videoUri] paused at 0. [PlaybackEvent.Prepared] follows once, when the first frame can render. */
    fun prepare(videoUri: String)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun setMuted(muted: Boolean)

    /**
     * Raises [PlaybackEvent.CueReached] when playback reaches [positionMs] on the video's own timeline,
     * replacing any cue still pending. A position at or behind the current one fires at once, so a
     * cue can never be skipped by a pause that overshot it by a frame.
     *
     * Position, not a timer: time spent buffering, paused or in the background does not move the
     * position, so it cannot use up a step's slow phase (requirements.md §4).
     */
    fun scheduleCue(positionMs: Long)

    fun cancelCue()
}

sealed interface PlaybackEvent {

    data object Prepared : PlaybackEvent

    data class CueReached(val positionMs: Long) : PlaybackEvent

    data object Ended : PlaybackEvent

    /** [reason] is the player's error code name, for the log and the error screen. */
    data class Failed(val reason: String?) : PlaybackEvent
}

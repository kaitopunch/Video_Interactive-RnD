package com.pion.psremote.core.common

/**
 * Every way an operation is allowed to fail.
 *
 * Carries no string a user sees: the screen maps it to a `@StringRes` at render time, so one error
 * reads correctly in both shipped languages (MVI doc §5).
 */
sealed interface AppError {

    /**
     * A game, or a part of it, is not there or cannot be read: an id the catalogue does not list, a catalogue
     * entry missing its script or video field, a video URL the server has no file at. [what] names it.
     */
    data class NotFound(val what: String) : AppError

    /**
     * The catalogue or a video could not be fetched: no connection, a timeout, or the server answered with an
     * error or a body that is not the catalogue. [reason] is the HTTP status or the exception's class name.
     */
    data class Network(val reason: String?) : AppError

    /** The player failed while preparing or playing the video. [reason] is Media3's error code name. */
    data class Playback(val reason: String?) : AppError

    /** The gap between what the layer below promised and what it did. Raised by `launchSafely`. */
    data class Unexpected(val message: String?) : AppError
}

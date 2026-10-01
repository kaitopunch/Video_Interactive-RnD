package com.pion.psremote.core.common

/**
 * Every way an operation is allowed to fail.
 *
 * Carries no string a user sees: the screen maps it to a `@StringRes` at render time, so one error
 * reads correctly in both shipped languages (MVI doc §5).
 */
sealed interface AppError {

    /** A bundled demo file (video or script) is missing or unreadable. [what] names the file. */
    data class NotFound(val what: String) : AppError

    /** The player failed while preparing or playing the video. [reason] is Media3's error code name. */
    data class Playback(val reason: String?) : AppError

    /** The gap between what the layer below promised and what it did. Raised by `launchSafely`. */
    data class Unexpected(val message: String?) : AppError
}

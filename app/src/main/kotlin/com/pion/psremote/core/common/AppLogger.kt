package com.pion.psremote.core.common

/**
 * The logging port. One method per level, no Android type in the signature.
 *
 * `android.util.Log` is never called from a ViewModel: `android.jar` on the unit-test classpath is a
 * stub and `Log.e()` throws `RuntimeException: ... not mocked` the first time a real error reaches
 * `launchSafely`'s catch block (MVI doc §1). The Android adapter is `core/log/AndroidAppLogger.kt`,
 * the only file allowed that import.
 *
 * [NoOp] is the default so an `MviViewModel` subclass constructs on a bare JVM with no test double.
 */
interface AppLogger {

    fun d(message: () -> String)

    fun e(throwable: Throwable? = null, message: () -> String)

    companion object {
        val NoOp: AppLogger = object : AppLogger {
            override fun d(message: () -> String) = Unit
            override fun e(throwable: Throwable?, message: () -> String) = Unit
        }
    }
}

package com.pion.psremote.core.log

import android.util.Log
import com.pion.psremote.core.common.AppLogger

/** The only file in the project that imports `android.util.Log` (LLM.md §4). */
object AndroidAppLogger : AppLogger {

    private const val TAG = "PSRemote"

    override fun d(message: () -> String) {
        Log.d(TAG, message())
    }

    override fun e(throwable: Throwable?, message: () -> String) {
        Log.e(TAG, message(), throwable)
    }
}

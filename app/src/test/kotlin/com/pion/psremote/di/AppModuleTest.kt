package com.pion.psremote.di

import android.content.Context
import com.pion.psremote.domain.playback.VideoPlayback
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

/** A missing binding fails the build here, instead of crashing the screen that first asks for it. */
class AppModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `the graph resolves every dependency it declares`() {
        // Supplied from outside the graph, so verification must not look for a binding:
        //   Context       — androidContext() at startKoin.
        //   String        — DemoViewModel's demoId, the route argument DemoRoute passes in parametersOf.
        //   VideoPlayback — DemoPlaybackHost's player, passed the same way. A `single` would be one player
        //                   shared by every demo, released by the first one to close.
        appModule.verify(extraTypes = listOf(Context::class, String::class, VideoPlayback::class))
    }
}

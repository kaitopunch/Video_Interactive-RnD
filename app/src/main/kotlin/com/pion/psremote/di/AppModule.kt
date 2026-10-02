package com.pion.psremote.di

import com.pion.psremote.BuildConfig
import com.pion.psremote.core.common.AppLogger
import com.pion.psremote.core.log.AndroidAppLogger
import com.pion.psremote.data.catalogue.CatalogueApi
import com.pion.psremote.data.demo.RemoteDemoRepository
import com.pion.psremote.data.playback.VideoCache
import com.pion.psremote.data.playback.VideoDurationReader
import com.pion.psremote.data.playback.VideoDurations
import com.pion.psremote.domain.playback.VideoPlayback
import com.pion.psremote.domain.repository.DemoRepository
import com.pion.psremote.domain.usecase.LoadDemoUseCase
import com.pion.psremote.feature.demo.DemoPlaybackHost
import com.pion.psremote.feature.demo.DemoViewModel
import com.pion.psremote.feature.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The whole object graph, and the only place that names a `data` class for a screen (LLM.md §6).
 * One module: the app has two screens, and a module per layer would be five files around eight lines.
 *
 * Every type is declared exactly once. Koin overrides a second declaration silently, so a duplicate is a
 * load-order coin-flip at runtime, not a compile error.
 */
val appModule = module {
    single<AppLogger> { AndroidAppLogger }
    single { CatalogueApi(apiKey = BuildConfig.CATALOGUE_API_KEY) }
    // One per process: a second SimpleCache on the same folder throws (VideoCache's KDoc).
    single { VideoCache(androidContext()) }
    // Bound under its port too: the repository reads through VideoDurations, so its suite can pass a lambda.
    single { VideoDurationReader(androidContext(), get()) } bind VideoDurations::class
    single<DemoRepository> { RemoteDemoRepository(api = get(), durations = get()) }
    factory { LoadDemoUseCase(get()) }

    viewModel { HomeViewModel(repository = get(), log = get()) }
    viewModel { DemoPlaybackHost(androidContext(), get()) }
    // demoId and the player come from DemoRoute: the route argument, and the host on the same back-stack entry.
    viewModel { params ->
        DemoViewModel(
            demoId = params.get<String>(),
            loadDemo = get(),
            playback = params.get<VideoPlayback>(),
            log = get(),
        )
    }
}

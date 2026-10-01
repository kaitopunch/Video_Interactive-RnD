package com.pion.psremote.feature.demo

import androidx.lifecycle.viewModelScope
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.usecase.LoadDemoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class DemoViewModelTestFixture {
    protected lateinit var dispatcher: TestDispatcher
    protected lateinit var playback: FakeVideoPlayback
    protected lateinit var repository: FakeDemoRepository
    protected lateinit var vm: DemoViewModel
    protected val state: DemoState get() = vm.state.value

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        playback = FakeVideoPlayback()
        repository = FakeDemoRepository(AppResult.Success(source()))
        // The test dispatcher, not Dispatchers.Default: the script is then checked before startPlaying() returns.
        vm = DemoViewModel("sample", LoadDemoUseCase(repository, dispatcher), playback)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    protected fun startPlaying() {
        vm.onIntent(DemoIntent.ScreenStarted)
        playback.emit(PlaybackEvent.Prepared)
    }

    protected fun press(button: ControllerButton) = vm.onIntent(DemoIntent.ButtonPressed(button))

    protected fun release(button: ControllerButton) = vm.onIntent(DemoIntent.ButtonReleased(button))

    protected fun source(script: String = SCRIPT) = DemoSource("asset:///demos/sample/video.mp4", 70_000, script)

    protected companion object {
        const val SCRIPT = """[
            {"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["DPAD_UP","CROSS","R1"],
             "playbackSpeed":0.25,"slowDurationMs":3000},
            {"step_sequence":2,"triggerTimeMs":60000,"targetButtonIds":["CROSS"],
             "playbackSpeed":0,"slowDurationMs":0}
        ]"""
        const val SIMULTANEOUS_SCRIPT = """[
            {"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["L1","R1"],
             "inputMode":"SIMULTANEOUS","playbackSpeed":0.25,"slowDurationMs":3000}
        ]"""
    }
}

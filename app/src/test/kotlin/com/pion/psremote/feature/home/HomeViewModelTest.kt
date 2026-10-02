package com.pion.psremote.feature.home

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.feature.demo.FakeDemoRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var repository: FakeDemoRepository
    private lateinit var vm: HomeViewModel
    private val state: HomeState get() = vm.state.value

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        repository = FakeDemoRepository(listResult = AppResult.Success(DEMOS))
        vm = HomeViewModel(repository)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is read until the screen starts`() {
        assertEquals(HomePhase.Loading, state.phase)
        assertEquals(0, repository.listCalls)
    }

    @Test
    fun `screen start lists the demos once`() = runTest(dispatcher) {
        vm.onIntent(HomeIntent.ScreenStarted)
        vm.onIntent(HomeIntent.ScreenStarted)

        assertEquals(HomePhase.Ready(DEMOS), state.phase)
        assertEquals(1, repository.listCalls)
    }

    @Test
    fun `an empty catalogue is Empty, not an empty grid`() = runTest(dispatcher) {
        repository.listResult = AppResult.Success(emptyList())

        vm.onIntent(HomeIntent.ScreenStarted)

        assertEquals(HomePhase.Empty, state.phase)
    }

    @Test
    fun `a failed read is shown and retried on the next start`() = runTest(dispatcher) {
        val error = AppError.Network("UnknownHostException")
        repository.listResult = AppResult.Failure(error)
        vm.onIntent(HomeIntent.ScreenStarted)
        assertEquals(HomePhase.Failed(error), state.phase)

        repository.listResult = AppResult.Success(DEMOS)
        vm.onIntent(HomeIntent.ScreenStarted)

        assertEquals(HomePhase.Ready(DEMOS), state.phase)
        assertEquals(2, repository.listCalls)
    }

    @Test
    fun `retry after a failure reaches the repository and shows the list`() = runTest(dispatcher) {
        repository.listResult = AppResult.Failure(AppError.Network("HTTP 503"))
        vm.onIntent(HomeIntent.ScreenStarted)

        repository.listResult = AppResult.Success(DEMOS)
        vm.onIntent(HomeIntent.RetryClicked)

        assertEquals(HomePhase.Ready(DEMOS), state.phase)
        assertEquals(2, repository.listCalls)
    }

    @Test
    fun `retry on an empty catalogue fetches it again`() = runTest(dispatcher) {
        repository.listResult = AppResult.Success(emptyList())
        vm.onIntent(HomeIntent.ScreenStarted)

        repository.listResult = AppResult.Success(DEMOS)
        vm.onIntent(HomeIntent.RetryClicked)

        assertEquals(HomePhase.Ready(DEMOS), state.phase)
    }

    @Test
    fun `retry does nothing while the list is shown`() = runTest(dispatcher) {
        vm.onIntent(HomeIntent.ScreenStarted)
        vm.onIntent(HomeIntent.RetryClicked)

        assertEquals(1, repository.listCalls)
    }

    @Test
    fun `retry does not start a second fetch while one is running`() = runTest(dispatcher) {
        repository.listGate = CompletableDeferred()
        vm.onIntent(HomeIntent.ScreenStarted)
        vm.onIntent(HomeIntent.RetryClicked)
        assertEquals(HomePhase.Loading, state.phase)

        repository.listGate?.complete(Unit)

        assertEquals(1, repository.listCalls)
        assertEquals(HomePhase.Ready(DEMOS), state.phase)
    }

    @Test
    fun `a throwing repository is contained, and the retry reaches it`() = runTest(dispatcher) {
        repository.throwOnList = IllegalStateException("catalogue unreadable")
        vm.onIntent(HomeIntent.ScreenStarted)
        assertTrue(state.phase is HomePhase.Failed)
        assertTrue((state.phase as HomePhase.Failed).error is AppError.Unexpected)

        repository.throwOnList = null
        vm.onIntent(HomeIntent.ScreenStarted)

        assertEquals(2, repository.listCalls)
        assertEquals(HomePhase.Ready(DEMOS), state.phase)
    }

    @Test
    fun `a tapped demo is opened exactly once`() = runTest(dispatcher) {
        vm.onIntent(HomeIntent.ScreenStarted)
        vm.onIntent(HomeIntent.DemoClicked(SPIDER_MAN.id))

        vm.effects.test {
            assertEquals(HomeEffect.OpenDemo(SPIDER_MAN.id), awaitItem())
            expectNoEvents()
        }
    }

    private companion object {
        val SPIDER_MAN = DemoSummary("371ed659-b6b5-436c-b9ad-ad41da4ae797", "Spider Man")
        val DEMOS = listOf(DemoSummary("6ef63378-d525-4633-a244-2ea68bf3ef19", "Sample"), SPIDER_MAN)
    }
}

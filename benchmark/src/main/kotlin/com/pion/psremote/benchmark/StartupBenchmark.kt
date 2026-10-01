package com.pion.psremote.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold start to the game list. `None` is a sideloaded APK before ART has compiled anything — how the demo
 * reaches every tester, since it is not on Play and gets no cloud profile. `Partial` is the same APK with
 * the baseline profile installed by profileinstaller.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(private val compilationMode: CompilationMode) {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartToGameList() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        awaitGameList()
    }

    companion object {
        private const val ITERATIONS = 10

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<CompilationMode> = listOf(
            CompilationMode.None(),
            CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        )
    }
}

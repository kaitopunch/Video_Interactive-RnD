package com.pion.psremote.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * Tap on a game → controller on screen, from a fresh process: the Home→Demo transition, the script read and
 * checked, the player built and prepared. `Full` compilation, so a change in the app's own code is compared
 * with JIT warm-up taken out.
 */
@OptIn(ExperimentalMetricApi::class)
class OpenDemoBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openSampleDemo() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(Sections.READ_DEMO, TraceSectionMetric.Mode.First),
            TraceSectionMetric(Sections.DEMO_READY, TraceSectionMetric.Mode.First),
        ),
        compilationMode = CompilationMode.Full(),
        iterations = ITERATIONS,
        setupBlock = { restartOnHome() },
    ) {
        openSampleDemo()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}

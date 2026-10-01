package com.pion.psremote.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * Frames drawn while a tutorial waits for its input: the video is stopped, so every frame is the overlay
 * alone — the dim layer with its holes, and the pulsing ring around the button to press.
 */
class TutorialFrameBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun spotlightWhileWaiting() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = ITERATIONS,
        setupBlock = {
            restartOnHome()
            openSampleDemo()
            awaitFirstStepWaiting()
        },
    ) {
        Thread.sleep(MEASURED_MS)
    }

    private companion object {
        const val ITERATIONS = 5
        const val MEASURED_MS = 5_000L
    }
}

package com.pion.psremote.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the rules for app/src/main/baseline-prof.txt: the path every tester takes — start, pick a game, the
 * first tutorial from dim to stop to press, back to the list. Copy the output over that file after a change to
 * any of those screens (LLM.md §10).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        awaitGameList()
        openSampleDemo()
        awaitFirstStepWaiting()
        completeFirstStep()
        device.waitForIdle()
        device.pressBack()
        awaitGameList()
    }
}

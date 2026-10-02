package com.pion.psremote.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The app under test: :app's `benchmark` build type keeps the release application id. */
const val PACKAGE = "com.pion.psremote"

/** Trace sections the app emits (data layer only, LLM.md §2), read back by the benchmarks. */
object Sections {
    /**
     * `RemoteDemoRepository.load`: the catalogue entry and the video's duration, read over the network since
     * 2026-10-02 (confirm.md H1) — numbers from before that measured an asset read, and do not compare.
     */
    const val READ_DEMO = "PsRemote.readDemo"

    /** Player built → first frame ready: everything the user waits through after tapping a game. */
    const val DEMO_READY = "PsRemote.demoReady"
}

/**
 * The catalogue's "Sample" game is the benchmark fixture — the phone needs a connection, and a build with the
 * catalogue key: 70 s of test pattern, first step a single CROSS at 5 000 ms
 * slowed to 0.5× for 2 000 ms, so the video stops and waits at 6 000 ms ≈ 7 s after it starts.
 */
private const val SAMPLE_TITLE = "Sample"
private const val FIRST_STEP_BUTTON = "CROSS"
private const val FIRST_STEP_WAITING_AFTER_MS = 8_000L

private const val UI_TIMEOUT_MS = 10_000L

fun MacrobenchmarkScope.awaitGameList() {
    check(device.wait(Until.hasObject(By.text(SAMPLE_TITLE)), UI_TIMEOUT_MS)) { "Home never listed $SAMPLE_TITLE" }
}

/** Taps the sample game and returns once the controller is drawn, i.e. the video is prepared and playing. */
fun MacrobenchmarkScope.openSampleDemo() {
    device.findObject(By.text(SAMPLE_TITLE)).click()
    check(device.wait(Until.hasObject(By.desc(FIRST_STEP_BUTTON)), UI_TIMEOUT_MS)) { "The controller never appeared" }
}

/** Lets the sample play into its first step until the video has stopped and the spotlight is pulsing. */
fun MacrobenchmarkScope.awaitFirstStepWaiting() {
    Thread.sleep(FIRST_STEP_WAITING_AFTER_MS)
}

fun MacrobenchmarkScope.completeFirstStep() {
    device.findObject(By.desc(FIRST_STEP_BUTTON)).click()
}

/** A fresh process on Home: every measurement starts from the same place. */
fun MacrobenchmarkScope.restartOnHome() {
    pressHome()
    killProcess()
    startActivityAndWait()
    awaitGameList()
}

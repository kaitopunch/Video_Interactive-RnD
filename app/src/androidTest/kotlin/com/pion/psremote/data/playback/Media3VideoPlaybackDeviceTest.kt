package com.pion.psremote.data.playback

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pion.psremote.domain.playback.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform
import java.io.File

/**
 * The real player on the real decoder, against the sample video — bundled in this test APK, since the app
 * streams its videos (confirm.md H1) — through the app's own cache: what `FakeVideoPlayback` promises the
 * ViewModel suites, checked against Media3. Stops are position cues (LLM.md §12), so the test that matters is
 * where the video actually is when a cue lands — the measurement LLM.md §9 otherwise takes from `EventLogger`.
 *
 * ExoPlayer lives on the main looper, so every call is made there.
 */
@RunWith(AndroidJUnit4::class)
class Media3VideoPlaybackDeviceTest {

    private lateinit var playback: Media3VideoPlayback
    private val events = Channel<PlaybackEvent>(Channel.UNLIMITED)
    private lateinit var collector: kotlinx.coroutines.Job

    @Before
    fun setUp() = onMain {
        playback = Media3VideoPlayback(ApplicationProvider.getApplicationContext<Context>(), cache)
        collector = CoroutineScope(Dispatchers.Main).launch { playback.events.collect { events.send(it) } }
        playback.prepare(SAMPLE)
        assertEquals(PlaybackEvent.Prepared, next())
    }

    @After
    fun tearDown() = onMain {
        playback.release()
        collector.cancel()
    }

    @Test
    fun preparedIsReportedOnceEvenThoughEverySeekIsReadyAgain() = onMain {
        playback.seekTo(10_000)
        playback.seekTo(0)
        playback.play()

        assertNull("a second Prepared after a seek", nextOrNull(QUIET_MS))
    }

    @Test
    fun aCueLandsAtItsPositionOnTheVideoTimeline() = onMain {
        playback.scheduleCue(CUE_MS)
        playback.play()

        assertEquals(PlaybackEvent.CueReached(CUE_MS), next())
        val position = playback.player.currentPosition
        assertTrue("cue at $CUE_MS ms landed at $position ms", position in (CUE_MS - GUARD_MS)..(CUE_MS + LATE_MS))
    }

    /** F4: a stop just ahead of the playback thread would never be delivered; it fires at once instead. */
    @Test
    fun aCueAtOrBehindThePositionFiresAtOnce() = onMain {
        playback.seekTo(3_000)
        playback.scheduleCue(3_000)

        assertEquals(PlaybackEvent.CueReached(3_000), next())
    }

    @Test
    fun aCancelledCueNeverFires() = onMain {
        playback.scheduleCue(CUE_MS)
        playback.cancelCue()
        playback.play()

        assertNull(nextOrNull(CUE_MS + QUIET_MS))
    }

    @Test
    fun aNewCueReplacesThePendingOne() = onMain {
        playback.scheduleCue(CUE_MS)
        playback.scheduleCue(CUE_MS + 200)
        playback.play()

        assertEquals(PlaybackEvent.CueReached(CUE_MS + 200), next())
    }

    @Test
    fun speedAndMuteReachThePlayer() = onMain {
        playback.setSpeed(0.25f)
        playback.setMuted(true)
        assertEquals(0.25f, playback.player.playbackParameters.speed)
        assertEquals(0f, playback.player.volume)

        playback.setSpeed(1f)
        playback.setMuted(false)
        assertEquals(1f, playback.player.playbackParameters.speed)
        assertEquals(1f, playback.player.volume)
    }

    /** README §4: the slow phase is video time, so at 0.5× one second of video takes two seconds. */
    @Test
    fun aCueAtHalfSpeedTakesTwiceAsLongInRealTime() = onMain {
        playback.setSpeed(0.5f)
        playback.scheduleCue(CUE_MS)
        val started = System.nanoTime()
        playback.play()

        assertEquals(PlaybackEvent.CueReached(CUE_MS), next())
        val realMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("$CUE_MS ms of video at 0.5× took $realMs ms", realMs in (2 * CUE_MS - 250)..(2 * CUE_MS + 500))
    }

    @Test
    fun theEndOfTheVideoIsReported() = onMain {
        playback.seekTo(playback.player.duration - 500)
        playback.play()

        assertEquals(PlaybackEvent.Ended, next())
    }

    @Test
    fun releaseEndsTheEventStream() = runBlocking {
        val other = withContext(Dispatchers.Main) { Media3VideoPlayback(ApplicationProvider.getApplicationContext(), cache) }
        val drained = CoroutineScope(Dispatchers.Main).launch { other.events.toList() }
        withContext(Dispatchers.Main) { other.release() }

        withTimeout(TIMEOUT_MS) { drained.join() }
    }

    private suspend fun next(): PlaybackEvent = withTimeout(TIMEOUT_MS) { events.receive() }

    private suspend fun nextOrNull(waitMs: Long): PlaybackEvent? = withTimeoutOrNull(waitMs) { events.receive() }

    private fun onMain(block: suspend CoroutineScope.() -> Unit) = runBlocking { withContext(Dispatchers.Main, block) }

    private companion object {
        /** The app's own: a second `SimpleCache` on the same folder throws (`VideoCache`). */
        val cache: VideoCache get() = KoinPlatform.getKoin().get()

        /**
         * `sample.mp4` from this test APK's assets, copied where the app's player can open it: an `asset://` URI
         * resolves against the app's assets, which hold no video any more.
         */
        val SAMPLE: String by lazy {
            val file = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "sample.mp4")
            if (!file.exists()) {
                InstrumentationRegistry.getInstrumentation().context.assets.open("sample.mp4")
                    .use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            Uri.fromFile(file).toString()
        }
        const val CUE_MS = 1_000L
        const val TIMEOUT_MS = 10_000L
        const val QUIET_MS = 1_500L

        /** `Media3VideoPlayback.CUE_GUARD_MS`: a cue may fire this much early, never later than a frame or two. */
        const val GUARD_MS = 40L
        const val LATE_MS = 100L
    }
}

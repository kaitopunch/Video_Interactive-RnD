package com.pion.psremote.feature.demo

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppLogger
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.core.mvi.MviViewModel
import com.pion.psremote.domain.input.StepProgress
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.DemoLoad
import com.pion.psremote.domain.model.TutorialStep
import com.pion.psremote.domain.playback.PlaybackEvent
import com.pion.psremote.domain.playback.SlowMotionRamp
import com.pion.psremote.domain.playback.VideoPlayback
import com.pion.psremote.domain.score.ScoreTier
import com.pion.psremote.domain.usecase.LoadDemoUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one demo through its script (requirements.md §6). Stops are position-based via [VideoPlayback.scheduleCue] —
 * the resume countdown and the slow-motion ramp are the only timers, and neither stops anything — so
 * buffering and background time never eat a slow phase. What counts as a correct press is [StepProgress]'s.
 */
class DemoViewModel(
    private val demoId: String,
    private val loadDemo: LoadDemoUseCase,
    private val playback: VideoPlayback,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<DemoState, DemoIntent, DemoEffect>(DemoState(), log) {

    /** The validated script in timeline order. Not state: nothing renders the list itself. */
    private var steps: List<TutorialStep> = emptyList()
    /** Index into [steps] of the next step whose trigger has not been reached yet. */
    private var nextStepIndex = 0
    /** Between ON_START and ON_STOP. The video never plays outside that window (D6). */
    private var isScreenStarted = false
    private var loadJob: Job? = null
    private var countdownJob: Job? = null

    init {
        playback.events.collectSafely(onError = ::fail) { onPlaybackEvent(it) }
    }

    override fun onIntent(intent: DemoIntent) {
        when (intent) {
            DemoIntent.ScreenStarted -> onScreenStarted()
            DemoIntent.ScreenStopped -> onScreenStopped()
            is DemoIntent.ButtonPressed -> onButtonPressed(intent.button)
            is DemoIntent.ButtonReleased -> onButtonReleased(intent.button)
            DemoIntent.ReplayClicked -> if (currentState.phase == DemoPhase.Finished) startFromBeginning()
            DemoIntent.ExitClicked -> sendEffect(DemoEffect.Exit)
        }
    }

    private fun onScreenStarted() {
        isScreenStarted = true
        if (loadJob == null) load()
        if (currentState.resumeCountdown != null) startResumeCountdown() else syncPlayback()
    }

    private fun onScreenStopped() {
        isScreenStarted = false
        countdownJob?.cancel()
        if (currentState.phase == DemoPhase.Playing) setState { copy(resumeCountdown = RESUME_COUNTDOWN_SECONDS) }
        syncPlayback()
    }

    private fun load() {
        loadJob = launchSafely(onError = ::fail) {
            when (val result = loadDemo(demoId)) {
                is AppResult.Failure -> fail(result.error)
                is AppResult.Success -> when (val demo = result.value) {
                    is DemoLoad.Invalid -> setState { copy(phase = DemoPhase.InvalidScript(demo.violations)) }
                    is DemoLoad.Ready -> {
                        steps = demo.steps
                        setState { copy(score = score.copy(stepCount = demo.steps.size)) }
                        playback.prepare(demo.videoUri) // continues at PlaybackEvent.Prepared
                    }
                }
            }
        }
    }

    private fun onPlaybackEvent(event: PlaybackEvent) {
        when (event) {
            PlaybackEvent.Prepared -> if (currentState.phase == DemoPhase.Loading) startFromBeginning()
            is PlaybackEvent.CueReached -> onCueReached(event.positionMs)
            PlaybackEvent.Ended -> finish()
            is PlaybackEvent.Failed -> fail(AppError.Playback(event.reason))
        }
    }

    private fun startFromBeginning() {
        nextStepIndex = 0
        // The awards only: the step count is set with the steps, in load(), whatever order the two arrive in.
        setState { copy(phase = DemoPhase.Playing, tutorial = null, score = score.copy(awards = emptyList())) }
        playback.cancelCue()
        playback.setSpeed(NORMAL_SPEED)
        playback.setMuted(false)
        playback.seekTo(0) // before arming, so a trigger at 0 ms is not "already passed"
        armNextTrigger()
        syncPlayback()
    }

    private fun armNextTrigger() {
        steps.getOrNull(nextStepIndex)?.let { playback.scheduleCue(it.triggerTimeMs) }
    }

    /** A cue is acted on only if it is the one expected now; anything else is a stale delivery. */
    private fun onCueReached(positionMs: Long) {
        if (currentState.phase != DemoPhase.Playing) return
        val tutorial = currentState.tutorial
        if (tutorial == null) {
            val step = steps.getOrNull(nextStepIndex)
            if (step != null && positionMs == step.triggerTimeMs) showTutorial(step)
        } else if (!tutorial.isWaiting && positionMs == tutorial.step.stopPositionMs) {
            // Slow phase over, input not complete: stop and keep what was already done (requirements.md §6).
            setState { copy(tutorial = tutorial.copy(isWaiting = true)) }
            syncPlayback()
        }
    }

    private fun showTutorial(step: TutorialStep) {
        setState {
            copy(tutorial = ActiveTutorial(step, StepProgress.start(step), isWaiting = step.stopsImmediately))
        }
        if (!step.stopsImmediately) {
            playback.setMuted(true) // audio at 0.25× is distorted (D7)
            playback.scheduleCue(step.stopPositionMs)
            // Not a field to cancel: it ends itself once this step has left the screen (LLM.md §12).
            val isStillShown = { currentState.tutorial?.step === step }
            launchSafely { SlowMotionRamp.run(step.playbackSpeed.toFloat(), isStillShown, playback::setSpeed) }
        }
        syncPlayback()
    }

    private fun onButtonPressed(button: ControllerButton) {
        // Outside a tutorial a press is only its visual effect (D3); during the countdown nothing is live.
        val tutorial = currentState.tutorial ?: return
        if (currentState.resumeCountdown != null) return
        val progress = tutorial.progress.press(button)
        if (progress.isComplete) completeStep(tutorial) else setState { copy(tutorial = tutorial.copy(progress = progress)) }
    }

    private fun onButtonReleased(button: ControllerButton) {
        val tutorial = currentState.tutorial ?: return
        setState { copy(tutorial = tutorial.copy(progress = tutorial.progress.release(button))) }
    }

    /**
     * requirements.md §6: done once, pending stop cancelled, tutorial hidden, 1.0× from the current position. Scored in
     * the same update that hides the tutorial, so no frame shows the step gone and its points missing (scoring rules R1).
     */
    private fun completeStep(tutorial: ActiveTutorial) {
        playback.cancelCue()
        nextStepIndex++
        val tier = ScoreTier.of(tutorial.step, completedWhileWaiting = tutorial.isWaiting)
        setState { copy(tutorial = null, score = score.award(tier)) }
        playback.setSpeed(NORMAL_SPEED)
        playback.setMuted(false)
        armNextTrigger()
        syncPlayback()
    }

    private fun finish() {
        if (currentState.phase == DemoPhase.Playing) endRun(DemoPhase.Finished)
    }

    private fun fail(error: AppError) = endRun(DemoPhase.Failed(error))

    /** Nothing pending outlives a run: no cue, no countdown — not even one set by an ON_STOP queued before `Ended`. */
    private fun endRun(phase: DemoPhase) {
        countdownJob?.cancel()
        playback.cancelCue()
        setState { copy(phase = phase, tutorial = null, resumeCountdown = null) }
        syncPlayback()
    }

    /** D6: after a return from the background, a few seconds to settle before the demo continues. */
    private fun startResumeCountdown() {
        countdownJob?.cancel()
        countdownJob = launchSafely(onError = { endResumeCountdown() }) {
            for (secondsLeft in RESUME_COUNTDOWN_SECONDS downTo 1) {
                setState { copy(resumeCountdown = secondsLeft) }
                delay(1.seconds)
            }
            endResumeCountdown()
        }
    }

    private fun endResumeCountdown() {
        setState { copy(resumeCountdown = null) }
        syncPlayback()
    }

    /** The one place that decides play or pause, so no path can resume a video another reason still holds. */
    private fun syncPlayback() {
        val state = currentState
        val shouldPlay = isScreenStarted &&
            state.phase == DemoPhase.Playing &&
            state.resumeCountdown == null &&
            state.tutorial?.isWaiting != true
        if (shouldPlay) playback.play() else playback.pause()
    }

    private companion object {
        const val RESUME_COUNTDOWN_SECONDS = 5
        const val NORMAL_SPEED = 1f
    }
}

package com.pion.psremote.domain.usecase

import com.pion.psremote.core.common.AppResult
import com.pion.psremote.core.common.map
import com.pion.psremote.domain.model.DemoLoad
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.repository.DemoRepository
import com.pion.psremote.domain.script.DemoScriptParser
import com.pion.psremote.domain.script.DemoScriptValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a demo and decides whether it can be played. A script with any violation is never played in
 * part: the whole list goes to the error screen instead (confirm.md Q12).
 *
 * Parsing and checking run on [dispatcher], not the caller's: the caller is the demo screen's ViewModel on
 * the main thread, which is animating the Home→Demo transition at that moment. Tests pass their own
 * dispatcher so the work stays on the test scheduler.
 */
class LoadDemoUseCase(
    private val repository: DemoRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend operator fun invoke(demoId: String): AppResult<DemoLoad> =
        repository.load(demoId).map { source -> withContext(dispatcher) { checkScript(source) } }

    private fun checkScript(source: DemoSource): DemoLoad {
        val parsed = DemoScriptParser.parse(source.scriptJson)
        val violations = DemoScriptValidator.validate(parsed, source.videoDurationMs)
        return if (violations.isEmpty()) {
            DemoLoad.Ready(source.videoUri, parsed.steps.sortedBy { it.sequence })
        } else {
            DemoLoad.Invalid(violations)
        }
    }
}

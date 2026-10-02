package com.pion.psremote.domain.repository

import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.model.DemoSummary

/** Lists the games and finds one's video and script. Never throws across this boundary (MVI doc §5). */
interface DemoRepository {

    /** Every game to show, in the order to show them. Empty when there is none. */
    suspend fun list(): AppResult<List<DemoSummary>>

    suspend fun load(demoId: String): AppResult<DemoSource>
}

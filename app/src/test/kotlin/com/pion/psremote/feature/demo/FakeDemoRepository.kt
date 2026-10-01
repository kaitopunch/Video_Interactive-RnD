package com.pion.psremote.feature.demo

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.domain.repository.DemoRepository

/** Shared by the demo and home suites: each sets only the half of the port its ViewModel calls. */
class FakeDemoRepository(
    var result: AppResult<DemoSource> = AppResult.Failure(AppError.NotFound("not set by this test")),
    var listResult: AppResult<List<DemoSummary>> = AppResult.Success(emptyList()),
) : DemoRepository {
    var throwOnLoad: Throwable? = null
    var throwOnList: Throwable? = null
    var calls = 0
        private set
    var listCalls = 0
        private set

    override suspend fun list(): AppResult<List<DemoSummary>> {
        listCalls++
        throwOnList?.let { throw it }
        return listResult
    }

    override suspend fun load(demoId: String): AppResult<DemoSource> {
        calls++
        throwOnLoad?.let { throw it }
        return result
    }
}

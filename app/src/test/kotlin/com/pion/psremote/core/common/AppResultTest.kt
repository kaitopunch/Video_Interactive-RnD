package com.pion.psremote.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppResultTest {

    @Test
    fun `map transforms a success`() {
        assertEquals(AppResult.Success(139), AppResult.Success(139_000L).map { (it / 1_000).toInt() })
    }

    /** `LoadDemoUseCase` parses the script inside `map`: a failed load must never reach the parser. */
    @Test
    fun `map passes a failure through as it is, without running the transform`() {
        val failure: AppResult<Long> = AppResult.Failure(AppError.Network("timeout"))

        assertSame(failure, failure.map<Long, Int> { error("the transform ran on a failure") })
    }
}

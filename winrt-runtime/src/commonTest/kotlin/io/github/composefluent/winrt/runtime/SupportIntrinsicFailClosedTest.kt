package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class SupportIntrinsicFailClosedTest {
    @Test
    fun projection_support_marker_fails_when_not_lowered() {
        val failure = expectIntrinsicFailure {
            WinRTProjectionSupportIntrinsic.ensureInitialized()
        }

        assertTrue(failure.message.orEmpty().contains("WinRTProjectionSupportIntrinsic.ensureInitialized was not lowered"))
    }

    @Test
    fun authoring_support_marker_fails_when_not_lowered() {
        val failure = expectIntrinsicFailure {
            WinRTAuthoringSupportIntrinsic.ensureInitialized()
        }

        assertTrue(failure.message.orEmpty().contains("WinRTAuthoringSupportIntrinsic.ensureInitialized was not lowered"))
    }

    private fun expectIntrinsicFailure(block: () -> Unit): IllegalStateException =
        try {
            block()
            fail("Expected unlowered support intrinsic to fail closed.")
            error("Unreachable")
        } catch (failure: IllegalStateException) {
            failure
        }
}

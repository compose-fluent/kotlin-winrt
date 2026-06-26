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
    fun generic_type_instantiation_markers_fail_when_not_lowered() {
        val initializeAllFailure = expectIntrinsicFailure {
            WinRTGenericTypeInstantiationSupportIntrinsic.initializeAll()
        }
        val initializeBySourceTypeFailure = expectIntrinsicFailure {
            WinRTGenericTypeInstantiationSupportIntrinsic.initializeBySourceType("Windows.Foundation.Collections.IVector")
        }

        assertTrue(initializeAllFailure.message.orEmpty().contains("initializeAll was not lowered"))
        assertTrue(initializeBySourceTypeFailure.message.orEmpty().contains("initializeBySourceType was not lowered"))
    }

    @Test
    fun generic_abi_markers_fail_when_not_lowered() {
        val delegateNamedFailure = expectIntrinsicFailure {
            WinRTGenericAbiSupportIntrinsic.delegateNamed("_get_Value_Int")
        }
        val delegatesForSourceTypeFailure = expectIntrinsicFailure {
            WinRTGenericAbiSupportIntrinsic.delegatesForSourceType("Windows.Foundation.IReference<Int32>")
        }
        val isDerivedFailure = expectIntrinsicFailure {
            WinRTGenericAbiSupportIntrinsic.isDerivedGenericInterface("Windows.Foundation.Collections.IVector")
        }
        val registerFailure = expectIntrinsicFailure {
            WinRTGenericAbiSupportIntrinsic.registerAbiDelegates { _, _ -> }
        }

        assertTrue(delegateNamedFailure.message.orEmpty().contains("delegateNamed was not lowered"))
        assertTrue(delegatesForSourceTypeFailure.message.orEmpty().contains("delegatesForSourceType was not lowered"))
        assertTrue(isDerivedFailure.message.orEmpty().contains("isDerivedGenericInterface was not lowered"))
        assertTrue(registerFailure.message.orEmpty().contains("registerAbiDelegates was not lowered"))
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

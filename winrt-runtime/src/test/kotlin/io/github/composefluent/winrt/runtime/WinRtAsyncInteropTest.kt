package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtAsyncInteropTest {
    @Test
    fun async_status_maps_abi_values() {
        assertEquals(WinRtAsyncStatus.Started, WinRtAsyncStatus.fromAbi(0))
        assertEquals(WinRtAsyncStatus.Completed, WinRtAsyncStatus.fromAbi(1))
        assertEquals(WinRtAsyncStatus.Canceled, WinRtAsyncStatus.fromAbi(2))
        assertEquals(WinRtAsyncStatus.Error, WinRtAsyncStatus.fromAbi(3))
    }

    @Test
    fun delegate_bridge_supports_int32_async_status_argument() {
        var capturedStatus: WinRtAsyncStatus? = null
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = WinRtAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            capturedStatus = WinRtAsyncStatus.fromAbi(args[1] as Int)
        }

        handle.use {
            it.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, WinRtAsyncStatus.Completed.abiValue))
        }

        assertEquals(WinRtAsyncStatus.Completed, capturedStatus)
    }

    @Test
    fun async_action_await_completes_immediately_when_already_completed() {
        Arena.ofConfined().use { arena ->
            val action = FakeAsyncActionReference(arena, WinRtAsyncStatus.Completed)

            runBlocking {
                action.await()
            }

            assertTrue(action.resultsCalled)
        }
    }

    @Test
    fun async_action_await_registers_completed_callback_and_cancels_underlying_info() {
        Arena.ofConfined().use { arena ->
            val action = FakeAsyncActionReference(arena, WinRtAsyncStatus.Started)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) { action.await() }
                assertFalse(awaitJob.isCompleted)

                action.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
                assertTrue(action.resultsCalled)
                assertTrue(action.completedHandlerClosed())

                val cancelledAction = FakeAsyncActionReference(arena, WinRtAsyncStatus.Started)
                val cancelledJob = launch(start = CoroutineStart.UNDISPATCHED) { cancelledAction.await() }
                cancelledJob.cancel()
                cancelledJob.join()
                assertTrue(cancelledAction.cancelCalled)
                assertTrue(cancelledAction.completedHandlerClosed())
            }
        }
    }

    @Test
    fun async_join_uses_common_await_owner() {
        Arena.ofConfined().use { arena ->
            val action = FakeAsyncActionReference(arena, WinRtAsyncStatus.Completed)
            action.join()
            assertTrue(action.resultsCalled)

            val operation = FakeAsyncOperationReference(
                arena = arena,
                statusState = WinRtAsyncStatus.Completed,
                result = "joined",
            )
            assertEquals("joined", operation.join())
            assertTrue(operation.resultsCalled)
        }
    }

    @Test
    fun async_info_factory_exposes_completed_and_failed_action_as_winrt_async_action() {
        var capturedStatus: WinRtAsyncStatus? = null
        val completedHandler = WinRtDelegateBridge.createUnitDelegate(
            iid = WinRtAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            capturedStatus = WinRtAsyncStatus.fromAbi(args[1] as Int)
        }

        completedHandler.use { handle ->
            AsyncInfo.completedAction().use { action ->
                assertEquals(WinRtAsyncStatus.Completed, action.status())
                action.queryInterface(WinRtAsyncInterfaceIds.IAsyncInfo).getOrThrow().use { asyncInfo ->
                    assertEquals(WinRtAsyncInterfaceIds.IAsyncInfo, asyncInfo.interfaceId)
                }
                handle.createReference().use(action::setCompletedHandler)
                action.getResults()
            }
        }
        assertEquals(WinRtAsyncStatus.Completed, capturedStatus)

        AsyncInfo.actionFromException(WinRtAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)).use { action ->
            assertEquals(WinRtAsyncStatus.Error, action.status())
            assertEquals(KnownHResults.E_ACCESSDENIED, action.errorCode())
        }
    }

    @Test
    fun async_info_factory_exposes_result_operation_through_abi_get_results() {
        AsyncInfo.fromResult(
            result = 42,
            resultSignature = WinRtTypeSignature.int32(),
            resultWriter = WinRtAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value)
            },
        ).use { operation ->
            assertEquals(WinRtAsyncStatus.Completed, operation.status())
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocateInt32Slot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = operation.pointer,
                    slot = WinRtAsyncOperationVftblSlots.GetResults,
                    arg0 = resultOut,
                )
                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(42, PlatformAbi.readInt32(resultOut))
            }
        }
    }

    @Test
    fun async_info_run_action_completes_and_cancels_backing_job() {
        runBlocking {
            AsyncInfo.runAction(this) {
                // Completion flows through the TaskToAsync-style adapter rather than through a fake reference.
            }.use { action ->
                action.await()
                assertEquals(WinRtAsyncStatus.Completed, action.status())
            }

            AsyncInfo.runAction(this) {
                kotlinx.coroutines.awaitCancellation()
            }.use { action ->
                action.cancel()
                kotlinx.coroutines.yield()
                assertEquals(WinRtAsyncStatus.Canceled, action.status())
            }
        }
    }

    @Test
    fun async_info_factory_exposes_progress_action_and_reports_progress() {
        runBlocking {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            var capturedProgress: Int? = null
            val progressSignature = WinRtTypeSignature.int32()
            val progressHandler = WinRtDelegateBridge.createUnitDelegate(
                iid = WinRtAsyncActionWithProgressReference.progressHandlerInterfaceId(progressSignature),
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            ) { args ->
                capturedProgress = args[1] as Int
            }

            progressHandler.use { handler ->
                AsyncInfo.runActionWithProgress<Int>(
                    scope = this,
                    progressSignature = progressSignature,
                    progressValueKind = WinRtDelegateValueKind.INT32,
                ) { _, progress ->
                    gate.await()
                    progress.report(7)
                }.use { action ->
                    action.queryInterface(WinRtAsyncInterfaceIds.IAsyncInfo).getOrThrow().use { asyncInfo ->
                        assertEquals(WinRtAsyncInterfaceIds.IAsyncInfo, asyncInfo.interfaceId)
                    }
                    handler.createReference().use(action::setProgressHandler)
                    gate.complete(Unit)
                    action.await()
                    assertEquals(7, capturedProgress)
                    assertEquals(WinRtAsyncStatus.Completed, action.status())
                }
            }
        }
    }

    @Test
    fun async_info_factory_exposes_operation_with_progress_result_through_abi() {
        val resultSignature = WinRtTypeSignature.int32()
        val progressSignature = WinRtTypeSignature.uint32()
        AsyncInfo.fromResultWithProgress<Int, UInt>(
            result = 42,
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = WinRtDelegateValueKind.UINT32,
            resultWriter = WinRtAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value)
            },
        ).use { operation ->
            assertEquals(
                WinRtAsyncOperationWithProgressReference.interfaceId(resultSignature, progressSignature),
                operation.interfaceId,
            )
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocateInt32Slot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = operation.pointer,
                    slot = WinRtAsyncOperationWithProgressVftblSlots.GetResults,
                    arg0 = resultOut,
                )
                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(42, PlatformAbi.readInt32(resultOut))
            }
        }
    }

    @Test
    fun async_operation_await_completes_with_result_and_maps_error_status() {
        Arena.ofConfined().use { arena ->
            runBlocking {
                val operation = FakeAsyncOperationReference(
                    arena = arena,
                    statusState = WinRtAsyncStatus.Started,
                    result = "done",
                )

                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    assertEquals("done", operation.await())
                }
                operation.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
                assertTrue(operation.resultsCalled)
                assertTrue(operation.completedHandlerClosed())

                val failedOperation = FakeAsyncOperationReference(
                    arena = arena,
                    statusState = WinRtAsyncStatus.Error,
                    result = "ignored",
                    errorCode = KnownHResults.E_ACCESSDENIED,
                )

                try {
                    failedOperation.await()
                } catch (error: WinRtAccessDeniedException) {
                    return@runBlocking
                }

                throw AssertionError("Expected WinRtAccessDeniedException from await().")
            }
        }
    }

    private class FakeAsyncActionReference(
        arena: Arena,
        private var statusState: WinRtAsyncStatus,
        private val errorCode: HResult = KnownHResults.S_OK,
    ) : WinRtAsyncActionReference(arena.allocate(8).asNativePointer()) {
        var resultsCalled = false
        var cancelCalled = false
        private var completedHandle: WinRtDelegateHandle? = null

        override fun status(): WinRtAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults() {
            resultsCalled = true
        }

        override fun cancel() {
            cancelCalled = true
            statusState = WinRtAsyncStatus.Canceled
        }

        override fun registerCompletedHandler(handle: WinRtDelegateHandle) {
            completedHandle = handle
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRtAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }

    private class FakeAsyncOperationReference(
        arena: Arena,
        private var statusState: WinRtAsyncStatus,
        private val result: String,
        private val errorCode: HResult = KnownHResults.S_OK,
    ) : WinRtAsyncOperationReference<String>(
        pointer = arena.allocate(8).asNativePointer(),
        interfaceId = Guid("11111111-2222-3333-4444-555555555555"),
        completedHandlerInterfaceId = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        resultReader = { error("Fake override should be used.") },
    ) {
        var resultsCalled = false
        private var completedHandle: WinRtDelegateHandle? = null

        override fun status(): WinRtAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults(): String {
            resultsCalled = true
            return result
        }

        override fun registerCompletedHandler(handle: WinRtDelegateHandle) {
            completedHandle = handle
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRtAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }
}

private fun isDelegateHandleClosedForTesting(handle: WinRtDelegateHandle): Boolean =
    try {
        handle.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, WinRtAsyncStatus.Completed.abiValue))
        false
    } catch (_: IllegalStateException) {
        true
    }

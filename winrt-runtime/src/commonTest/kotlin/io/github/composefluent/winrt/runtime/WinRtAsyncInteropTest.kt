package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        PlatformAbi.confinedScope().use { scope ->
            val action = FakeAsyncActionReference(scope, WinRtAsyncStatus.Completed)

            runBlocking {
                action.await()
            }

            assertTrue(action.resultsCalled)
        }
    }

    @Test
    fun async_action_await_registers_completed_callback_and_cancels_underlying_info() {
        PlatformAbi.confinedScope().use { scope ->
            val action = FakeAsyncActionReference(scope, WinRtAsyncStatus.Started)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) { action.await() }
                assertFalse(awaitJob.isCompleted)

                action.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
                assertTrue(action.resultsCalled)
                assertTrue(action.completedHandlerClosed())

                val cancelledAction = FakeAsyncActionReference(scope, WinRtAsyncStatus.Started)
                val cancelledJob = launch(start = CoroutineStart.UNDISPATCHED) { cancelledAction.await() }
                cancelledJob.cancel()
                cancelledJob.join()
                assertTrue(cancelledAction.cancelCalled)
                assertTrue(cancelledAction.completedHandlerClosed())
            }
        }
    }

    @Test
    fun async_action_when_completed_closes_delegate_when_registration_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRtIllegalStateException(
                "completed handler already assigned",
                KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
            )
            val action = FakeAsyncActionReference(
                scope = scope,
                statusState = WinRtAsyncStatus.Started,
                registrationFailure = failure,
            )

            try {
                action.whenCompleted { _, _ -> }
            } catch (error: WinRtIllegalStateException) {
                assertEquals(failure, error)
                assertTrue(action.completedHandlerClosed())
                return
            }

            throw AssertionError("Expected completed-handler registration failure.")
        }
    }

    @Test
    fun async_action_await_faults_and_closes_completed_handler_when_get_results_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRtIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val action = FakeAsyncActionReference(scope, WinRtAsyncStatus.Started, resultsFailure = failure)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        action.await()
                    } catch (error: WinRtIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                action.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(action.resultsCalled)
            assertTrue(action.completedHandlerClosed())
        }
    }

    @Test
    fun async_join_uses_common_await_owner() {
        PlatformAbi.confinedScope().use { scope ->
            val action = FakeAsyncActionReference(scope, WinRtAsyncStatus.Completed)
            action.join()
            assertTrue(action.resultsCalled)

            val operation = FakeAsyncOperationReference(
                scope = scope,
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
    fun task_to_async_completed_handler_can_read_terminal_result_when_set_after_completion() {
        val resultSignature = WinRtTypeSignature.int32()
        val operation = AsyncInfo.fromResult(
            result = 42,
            resultSignature = resultSignature,
            resultWriter = WinRtAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value)
            },
        )
        var capturedStatus: WinRtAsyncStatus? = null
        var capturedResult: Int? = null
        val completedHandler = WinRtDelegateBridge.createUnitDelegate(
            iid = WinRtAsyncOperationReference.completedHandlerInterfaceId(resultSignature),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            capturedStatus = WinRtAsyncStatus.fromAbi(args[1] as Int)
            capturedResult = operation.getResults()
        }

        operation.use { asyncOperation ->
            completedHandler.use { handle ->
                handle.createReference().use(asyncOperation::setCompletedHandler)
            }
        }

        assertEquals(WinRtAsyncStatus.Completed, capturedStatus)
        assertEquals(42, capturedResult)
    }

    @Test
    fun task_to_async_completed_handler_rejects_second_assignment() {
        val firstHandler = WinRtDelegateBridge.createUnitDelegate(
            iid = WinRtAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { }
        val secondHandler = WinRtDelegateBridge.createUnitDelegate(
            iid = WinRtAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { }

        firstHandler.use { first ->
            secondHandler.use { second ->
                AsyncInfo.completedAction().use { action ->
                    first.createReference().use { reference ->
                        val firstHr = ComVtableInvoker.invokeArgs(
                            action.pointer,
                            WinRtAsyncActionVftblSlots.PutCompleted,
                            reference.pointer,
                        )
                        assertEquals(KnownHResults.S_OK.value, firstHr)
                    }

                    second.createReference().use { reference ->
                        val secondHr = ComVtableInvoker.invokeArgs(
                            action.pointer,
                            WinRtAsyncActionVftblSlots.PutCompleted,
                            reference.pointer,
                        )
                        assertEquals(KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT.value, secondHr)
                    }
                }
            }
        }
    }

    @Test
    fun task_to_async_close_matches_terminal_state_rules() {
        AsyncInfo.runAction(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)) {
            kotlinx.coroutines.awaitCancellation()
        }.use { action ->
            val startedCloseHr = ComVtableInvoker.invoke(action.pointer, WinRtAsyncInfoVftblSlots.Close)
            assertEquals(KnownHResults.E_ILLEGAL_STATE_CHANGE.value, startedCloseHr)

            val statusHr = PlatformAbi.confinedScope().use { scope ->
                val statusOut = PlatformAbi.allocateInt32Slot(scope)
                ComVtableInvoker.invokeArgs(action.pointer, WinRtAsyncInfoVftblSlots.Status, statusOut)
            }
            assertEquals(KnownHResults.S_OK.value, statusHr)

            action.cancel()
        }

        AsyncInfo.completedAction().use { action ->
            val closeHr = ComVtableInvoker.invoke(action.pointer, WinRtAsyncInfoVftblSlots.Close)
            assertEquals(KnownHResults.S_OK.value, closeHr)

            val statusHr = PlatformAbi.confinedScope().use { scope ->
                val statusOut = PlatformAbi.allocateInt32Slot(scope)
                ComVtableInvoker.invokeArgs(action.pointer, WinRtAsyncInfoVftblSlots.Status, statusOut)
            }
            assertEquals(KnownHResults.E_ILLEGAL_METHOD_CALL.value, statusHr)

            val idHr = PlatformAbi.confinedScope().use { scope ->
                val idOut = PlatformAbi.allocateInt32Slot(scope)
                ComVtableInvoker.invokeArgs(action.pointer, WinRtAsyncInfoVftblSlots.Id, idOut)
            }
            assertEquals(KnownHResults.E_ILLEGAL_METHOD_CALL.value, idHr)

            val secondCloseHr = ComVtableInvoker.invoke(action.pointer, WinRtAsyncInfoVftblSlots.Close)
            assertEquals(KnownHResults.S_OK.value, secondCloseHr)
        }
    }

    @Test
    fun async_info_factory_exposes_reference_ccw_suffix_interfaces() {
        AsyncInfo.completedAction().use { action ->
            listOf(
                IID.IStringable,
                IID.IWeakReferenceSource,
                IID.IMarshal,
                IID.IAgileObject,
                IID.IInspectable,
                IID.IReferenceTrackerTarget,
            ).forEach { iid ->
                action.queryInterface(iid).getOrThrow().use { queried ->
                    assertTrue(queried.sameIdentity(action))
                }
            }
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
    fun async_operation_reference_can_detach_as_winrt_object_return_value() {
        val resultSignature = WinRtTypeSignature.uint32()
        val interfaceId = WinRtAsyncOperationReference.interfaceId(resultSignature)

        AsyncInfo.fromResult(
            result = 4u,
            resultSignature = resultSignature,
            resultWriter = WinRtAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value.toInt())
            },
        ).use { operation ->
            val detached = ComWrappersSupport.detachCCWForObject(operation, interfaceId)
            WinRtAsyncProjectionInterop.operation(
                pointer = detached,
                resultSignature = resultSignature,
                resultOut = PlatformAbi::allocateInt32Slot,
                resultReader = { resultOut -> PlatformAbi.readInt32(resultOut).toUInt() },
            ).use { projected ->
                projected.queryInterface(IID.IInspectable).getOrThrow().use { inspectable ->
                    assertTrue(inspectable.sameIdentity(projected))
                }
                assertEquals(WinRtAsyncStatus.Completed, projected.status())
                assertEquals(4u, projected.getResults())
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
    fun task_to_async_cancel_waits_for_terminal_job_completion_before_invoking_completed() {
        runBlocking {
            val enteredJob = CompletableDeferred<Unit>()
            val releaseJob = CompletableDeferred<Unit>()
            var capturedStatus: WinRtAsyncStatus? = null
            val completedHandler = WinRtDelegateBridge.createUnitDelegate(
                iid = WinRtAsyncInterfaceIds.AsyncActionCompletedHandler,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            ) { args ->
                capturedStatus = WinRtAsyncStatus.fromAbi(args[1] as Int)
            }

            completedHandler.use { handle ->
                AsyncInfo.runAction(this) {
                    enteredJob.complete(Unit)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            releaseJob.await()
                        }
                    }
                }.use { action ->
                    handle.createReference().use(action::setCompletedHandler)
                    enteredJob.await()

                    action.cancel()
                    kotlinx.coroutines.yield()
                    assertEquals(WinRtAsyncStatus.Canceled, action.status())
                    assertEquals(null, capturedStatus)

                    val closeWhileCancellationRequestedHr = ComVtableInvoker.invoke(action.pointer, WinRtAsyncInfoVftblSlots.Close)
                    assertEquals(KnownHResults.E_ILLEGAL_STATE_CHANGE.value, closeWhileCancellationRequestedHr)

                    val resultsWhileCancellationRequestedHr =
                        ComVtableInvoker.invoke(action.pointer, WinRtAsyncActionVftblSlots.GetResults)
                    assertEquals(KnownHResults.E_ILLEGAL_METHOD_CALL.value, resultsWhileCancellationRequestedHr)

                    releaseJob.complete(Unit)
                    kotlinx.coroutines.yield()
                    assertEquals(WinRtAsyncStatus.Canceled, action.status())
                    assertEquals(WinRtAsyncStatus.Canceled, capturedStatus)
                }
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
    fun async_action_with_progress_await_faults_and_closes_completed_handler_when_get_results_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRtIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val action = FakeAsyncActionWithProgressReference(scope, WinRtAsyncStatus.Started, resultsFailure = failure)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        action.await()
                    } catch (error: WinRtIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                action.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(action.resultsCalled)
            assertTrue(action.completedHandlerClosed())
        }
    }

    @Test
    fun async_operation_with_progress_await_faults_and_closes_completed_handler_when_get_results_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRtIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val operation = FakeAsyncOperationWithProgressReference(
                scope = scope,
                statusState = WinRtAsyncStatus.Started,
                result = "ignored",
                resultsFailure = failure,
            )

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        operation.await()
                    } catch (error: WinRtIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                operation.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(operation.resultsCalled)
            assertTrue(operation.completedHandlerClosed())
        }
    }

    @Test
    fun async_operation_await_completes_with_result_and_maps_error_status() {
        PlatformAbi.confinedScope().use { scope ->
            runBlocking {
                val operation = FakeAsyncOperationReference(
                    scope = scope,
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
                    scope = scope,
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

    @Test
    fun async_operation_await_faults_and_closes_completed_handler_when_get_results_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRtIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val operation = FakeAsyncOperationReference(
                scope = scope,
                statusState = WinRtAsyncStatus.Started,
                result = "ignored",
                resultsFailure = failure,
            )

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        operation.await()
                    } catch (error: WinRtIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                operation.complete(WinRtAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(operation.resultsCalled)
            assertTrue(operation.completedHandlerClosed())
        }
    }

    private class FakeAsyncActionReference(
        scope: NativeScope,
        private var statusState: WinRtAsyncStatus,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
        private val registrationFailure: Throwable? = null,
    ) : WinRtAsyncActionReference(PlatformAbi.allocateBytes(scope, 8)) {
        var resultsCalled = false
        var cancelCalled = false
        private var completedHandle: WinRtDelegateHandle? = null

        override fun status(): WinRtAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults() {
            resultsCalled = true
            resultsFailure?.let { throw it }
        }

        override fun cancel() {
            cancelCalled = true
            statusState = WinRtAsyncStatus.Canceled
        }

        override fun registerCompletedHandler(handle: WinRtDelegateHandle) {
            completedHandle = handle
            registrationFailure?.let { throw it }
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
        scope: NativeScope,
        private var statusState: WinRtAsyncStatus,
        private val result: String,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
    ) : WinRtAsyncOperationReference<String>(
        pointer = PlatformAbi.allocateBytes(scope, 8),
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
            resultsFailure?.let { throw it }
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

    private class FakeAsyncActionWithProgressReference(
        scope: NativeScope,
        private var statusState: WinRtAsyncStatus,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
    ) : WinRtAsyncActionWithProgressReference<Int>(
        pointer = PlatformAbi.allocateBytes(scope, 8),
        interfaceId = Guid("22222222-3333-4444-5555-666666666666"),
        progressHandlerInterfaceId = Guid("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"),
        completedHandlerInterfaceId = Guid("cccccccc-dddd-eeee-ffff-aaaaaaaaaaaa"),
    ) {
        var resultsCalled = false
        private var completedHandle: WinRtDelegateHandle? = null

        override fun status(): WinRtAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults() {
            resultsCalled = true
            resultsFailure?.let { throw it }
        }

        override fun whenCompleted(
            callback: (WinRtAsyncActionWithProgressReference<Int>, WinRtAsyncStatus) -> Unit,
        ): WinRtDelegateHandle {
            val handle = WinRtDelegateBridge.createUnitDelegate(
                iid = Guid("cccccccc-dddd-eeee-ffff-aaaaaaaaaaaa"),
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            ) { args ->
                callback(this, WinRtAsyncStatus.fromAbi(args[1] as Int))
            }
            completedHandle = handle
            return handle
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRtAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }

    private class FakeAsyncOperationWithProgressReference(
        scope: NativeScope,
        private var statusState: WinRtAsyncStatus,
        private val result: String,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
    ) : WinRtAsyncOperationWithProgressReference<String, Int>(
        pointer = PlatformAbi.allocateBytes(scope, 8),
        interfaceId = Guid("33333333-4444-5555-6666-777777777777"),
        progressHandlerInterfaceId = Guid("dddddddd-eeee-ffff-aaaa-bbbbbbbbbbbb"),
        completedHandlerInterfaceId = Guid("eeeeeeee-ffff-aaaa-bbbb-cccccccccccc"),
        resultReader = { error("Fake override should be used.") },
    ) {
        var resultsCalled = false
        private var completedHandle: WinRtDelegateHandle? = null

        override fun status(): WinRtAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults(): String {
            resultsCalled = true
            resultsFailure?.let { throw it }
            return result
        }

        override fun whenCompleted(
            callback: (WinRtAsyncOperationWithProgressReference<String, Int>, WinRtAsyncStatus) -> Unit,
        ): WinRtDelegateHandle {
            val handle = WinRtDelegateBridge.createUnitDelegate(
                iid = Guid("eeeeeeee-ffff-aaaa-bbbb-cccccccccccc"),
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            ) { args ->
                callback(this, WinRtAsyncStatus.fromAbi(args[1] as Int))
            }
            completedHandle = handle
            return handle
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

package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinRTAsyncInteropTest {
    @Test
    fun async_status_maps_abi_values() {
        assertEquals(WinRTAsyncStatus.Started, WinRTAsyncStatus.fromAbi(0))
        assertEquals(WinRTAsyncStatus.Completed, WinRTAsyncStatus.fromAbi(1))
        assertEquals(WinRTAsyncStatus.Canceled, WinRTAsyncStatus.fromAbi(2))
        assertEquals(WinRTAsyncStatus.Error, WinRTAsyncStatus.fromAbi(3))
    }

    @Test
    fun delegate_bridge_supports_int32_async_status_argument() {
        var capturedStatus: WinRTAsyncStatus? = null
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = WinRTAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            capturedStatus = WinRTAsyncStatus.fromAbi(args[1] as Int)
        }

        handle.use {
            it.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, WinRTAsyncStatus.Completed.abiValue))
        }

        assertEquals(WinRTAsyncStatus.Completed, capturedStatus)
    }

    @Test
    fun async_action_await_completes_immediately_when_already_completed() {
        PlatformAbi.confinedScope().use { scope ->
            val action = FakeAsyncActionReference(scope, WinRTAsyncStatus.Completed)

            runBlocking {
                action.await()
            }

            assertTrue(action.resultsCalled)
        }
    }

    @Test
    fun async_action_await_registers_completed_callback_and_cancels_underlying_info() {
        PlatformAbi.confinedScope().use { scope ->
            val action = FakeAsyncActionReference(scope, WinRTAsyncStatus.Started)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) { action.await() }
                assertFalse(awaitJob.isCompleted)

                action.complete(WinRTAsyncStatus.Completed)
                awaitJob.join()
                assertTrue(action.resultsCalled)
                assertTrue(action.completedHandlerClosed())

                val cancelledAction = FakeAsyncActionReference(scope, WinRTAsyncStatus.Started)
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
            val failure = WinRTIllegalStateException(
                "completed handler already assigned",
                KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
            )
            val action = FakeAsyncActionReference(
                scope = scope,
                statusState = WinRTAsyncStatus.Started,
                registrationFailure = failure,
            )

            try {
                action.whenCompleted { _, _ -> }
            } catch (error: WinRTIllegalStateException) {
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
            val failure = WinRTIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val action = FakeAsyncActionReference(scope, WinRTAsyncStatus.Started, resultsFailure = failure)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        action.await()
                    } catch (error: WinRTIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                action.complete(WinRTAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(action.resultsCalled)
            assertTrue(action.completedHandlerClosed())
        }
    }

    @Test
    fun async_action_await_maps_error_cancelled_to_coroutine_cancellation() {
        PlatformAbi.confinedScope().use { scope ->
            runBlocking {
                val action = FakeAsyncActionReference(
                    scope = scope,
                    statusState = WinRTAsyncStatus.Error,
                    errorCode = KnownHResults.ERROR_CANCELLED,
                )

                try {
                    action.await()
                } catch (error: CancellationException) {
                    assertEquals("WinRT async action was canceled.", error.message)
                    return@runBlocking
                }

                throw AssertionError("Expected CancellationException from canceled WinRT async action.")
            }
        }
    }

    @Test
    fun async_join_uses_common_await_owner() {
        PlatformAbi.confinedScope().use { scope ->
            val action = FakeAsyncActionReference(scope, WinRTAsyncStatus.Completed)
            action.join()
            assertTrue(action.resultsCalled)

            val operation = FakeAsyncOperationReference(
                scope = scope,
                statusState = WinRTAsyncStatus.Completed,
                result = "joined",
            )
            assertEquals("joined", operation.join())
            assertTrue(operation.resultsCalled)
        }
    }

    @Test
    fun async_info_factory_exposes_completed_and_failed_action_as_winrt_async_action() {
        var capturedStatus: WinRTAsyncStatus? = null
        val completedHandler = WinRTDelegateBridge.createUnitDelegate(
            iid = WinRTAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            capturedStatus = WinRTAsyncStatus.fromAbi(args[1] as Int)
        }

        completedHandler.use { handle ->
            AsyncInfo.completedAction().use { action ->
                assertEquals(WinRTAsyncStatus.Completed, action.status())
                action.queryInterface(WinRTAsyncInterfaceIds.IAsyncInfo).getOrThrow().use { asyncInfo ->
                    assertEquals(WinRTAsyncInterfaceIds.IAsyncInfo, asyncInfo.interfaceId)
                }
                handle.createReference().use(action::setCompletedHandler)
                action.getResults()
            }
        }
        assertEquals(WinRTAsyncStatus.Completed, capturedStatus)

        AsyncInfo.actionFromException(WinRTAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)).use { action ->
            assertEquals(WinRTAsyncStatus.Error, action.status())
            assertEquals(KnownHResults.E_ACCESSDENIED, action.errorCode())
        }
    }

    @Test
    fun task_to_async_completed_handler_can_read_terminal_result_when_set_after_completion() {
        val resultSignature = WinRTTypeSignature.int32()
        val operation = AsyncInfo.fromResult(
            result = 42,
            resultSignature = resultSignature,
            resultWriter = WinRTAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value)
            },
        )
        var capturedStatus: WinRTAsyncStatus? = null
        var capturedResult: Int? = null
        val completedHandler = WinRTDelegateBridge.createUnitDelegate(
            iid = WinRTAsyncOperationReference.completedHandlerInterfaceId(resultSignature),
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            capturedStatus = WinRTAsyncStatus.fromAbi(args[1] as Int)
            capturedResult = operation.getResults()
        }

        operation.use { asyncOperation ->
            completedHandler.use { handle ->
                handle.createReference().use(asyncOperation::setCompletedHandler)
            }
        }

        assertEquals(WinRTAsyncStatus.Completed, capturedStatus)
        assertEquals(42, capturedResult)
    }

    @Test
    fun task_to_async_completed_handler_rejects_second_assignment() {
        val firstHandler = WinRTDelegateBridge.createUnitDelegate(
            iid = WinRTAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { }
        val secondHandler = WinRTDelegateBridge.createUnitDelegate(
            iid = WinRTAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { }

        firstHandler.use { first ->
            secondHandler.use { second ->
                AsyncInfo.completedAction().use { action ->
                    first.createReference().use { reference ->
                        val firstHr = ComVtableInvoker.invokeArgs(
                            action.pointer,
                            WinRTAsyncActionVftblSlots.PutCompleted,
                            reference.pointer,
                        )
                        assertEquals(KnownHResults.S_OK.value, firstHr)
                    }

                    second.createReference().use { reference ->
                        val secondHr = ComVtableInvoker.invokeArgs(
                            action.pointer,
                            WinRTAsyncActionVftblSlots.PutCompleted,
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
            val startedCloseHr = ComVtableInvoker.invoke(action.pointer, WinRTAsyncInfoVftblSlots.Close)
            assertEquals(KnownHResults.E_ILLEGAL_STATE_CHANGE.value, startedCloseHr)

            val statusHr = PlatformAbi.confinedScope().use { scope ->
                val statusOut = PlatformAbi.allocateInt32Slot(scope)
                ComVtableInvoker.invokeArgs(action.pointer, WinRTAsyncInfoVftblSlots.Status, statusOut)
            }
            assertEquals(KnownHResults.S_OK.value, statusHr)

            action.cancel()
        }

        AsyncInfo.completedAction().use { action ->
            val closeHr = ComVtableInvoker.invoke(action.pointer, WinRTAsyncInfoVftblSlots.Close)
            assertEquals(KnownHResults.S_OK.value, closeHr)

            val statusHr = PlatformAbi.confinedScope().use { scope ->
                val statusOut = PlatformAbi.allocateInt32Slot(scope)
                ComVtableInvoker.invokeArgs(action.pointer, WinRTAsyncInfoVftblSlots.Status, statusOut)
            }
            assertEquals(KnownHResults.E_ILLEGAL_METHOD_CALL.value, statusHr)

            val idHr = PlatformAbi.confinedScope().use { scope ->
                val idOut = PlatformAbi.allocateInt32Slot(scope)
                ComVtableInvoker.invokeArgs(action.pointer, WinRTAsyncInfoVftblSlots.Id, idOut)
            }
            assertEquals(KnownHResults.E_ILLEGAL_METHOD_CALL.value, idHr)

            val secondCloseHr = ComVtableInvoker.invoke(action.pointer, WinRTAsyncInfoVftblSlots.Close)
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
            resultSignature = WinRTTypeSignature.int32(),
            resultWriter = WinRTAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value)
            },
        ).use { operation ->
            assertEquals(WinRTAsyncStatus.Completed, operation.status())
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocateInt32Slot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = operation.pointer,
                    slot = WinRTAsyncOperationVftblSlots.GetResults,
                    arg0 = resultOut,
                )
                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(42, PlatformAbi.readInt32(resultOut))
            }
        }
    }

    @Test
    fun async_operation_reference_can_detach_as_winrt_object_return_value() {
        val resultSignature = WinRTTypeSignature.uint32()
        val interfaceId = WinRTAsyncOperationReference.interfaceId(resultSignature)

        AsyncInfo.fromResult(
            result = 4u,
            resultSignature = resultSignature,
            resultWriter = WinRTAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value.toInt())
            },
        ).use { operation ->
            val detached = ComWrappersSupport.detachCCWForObject(operation, interfaceId)
            WinRTAsyncProjectionInterop.operation(
                pointer = detached,
                resultSignature = resultSignature,
                resultOut = PlatformAbi::allocateInt32Slot,
                resultReader = { resultOut -> PlatformAbi.readInt32(resultOut).toUInt() },
            ).use { projected ->
                projected.queryInterface(IID.IInspectable).getOrThrow().use { inspectable ->
                    assertTrue(inspectable.sameIdentity(projected))
                }
                assertEquals(WinRTAsyncStatus.Completed, projected.status())
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
                assertEquals(WinRTAsyncStatus.Completed, action.status())
            }

            AsyncInfo.runAction(this) {
                kotlinx.coroutines.awaitCancellation()
            }.use { action ->
                action.cancel()
                kotlinx.coroutines.yield()
                assertEquals(WinRTAsyncStatus.Canceled, action.status())
            }
        }
    }

    @Test
    fun task_to_async_cancel_waits_for_terminal_job_completion_before_invoking_completed() {
        runBlocking {
            val enteredJob = CompletableDeferred<Unit>()
            val releaseJob = CompletableDeferred<Unit>()
            var capturedStatus: WinRTAsyncStatus? = null
            val completedHandler = WinRTDelegateBridge.createUnitDelegate(
                iid = WinRTAsyncInterfaceIds.AsyncActionCompletedHandler,
                parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
            ) { args ->
                capturedStatus = WinRTAsyncStatus.fromAbi(args[1] as Int)
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
                    assertEquals(WinRTAsyncStatus.Canceled, action.status())
                    assertEquals(null, capturedStatus)

                    val closeWhileCancellationRequestedHr = ComVtableInvoker.invoke(action.pointer, WinRTAsyncInfoVftblSlots.Close)
                    assertEquals(KnownHResults.E_ILLEGAL_STATE_CHANGE.value, closeWhileCancellationRequestedHr)

                    val resultsWhileCancellationRequestedHr =
                        ComVtableInvoker.invoke(action.pointer, WinRTAsyncActionVftblSlots.GetResults)
                    assertEquals(KnownHResults.E_ILLEGAL_METHOD_CALL.value, resultsWhileCancellationRequestedHr)

                    releaseJob.complete(Unit)
                    kotlinx.coroutines.yield()
                    assertEquals(WinRTAsyncStatus.Canceled, action.status())
                    assertEquals(WinRTAsyncStatus.Canceled, capturedStatus)
                }
            }
        }
    }

    @Test
    fun async_info_factory_exposes_progress_action_and_reports_progress() {
        runBlocking {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            var capturedProgress: Int? = null
            val progressSignature = WinRTTypeSignature.int32()
            val progressHandler = WinRTDelegateBridge.createUnitDelegate(
                iid = WinRTAsyncActionWithProgressReference.progressHandlerInterfaceId(progressSignature),
                parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
            ) { args ->
                capturedProgress = args[1] as Int
            }

            progressHandler.use { handler ->
                AsyncInfo.runActionWithProgress<Int>(
                    scope = this,
                    progressSignature = progressSignature,
                    progressValueKind = WinRTDelegateValueKind.INT32,
                ) { _, progress ->
                    gate.await()
                    progress.report(7)
                }.use { action ->
                    action.queryInterface(WinRTAsyncInterfaceIds.IAsyncInfo).getOrThrow().use { asyncInfo ->
                        assertEquals(WinRTAsyncInterfaceIds.IAsyncInfo, asyncInfo.interfaceId)
                    }
                    handler.createReference().use(action::setProgressHandler)
                    gate.complete(Unit)
                    action.await()
                    assertEquals(7, capturedProgress)
                    assertEquals(WinRTAsyncStatus.Completed, action.status())
                }
            }
        }
    }

    @Test
    fun async_info_factory_exposes_operation_with_progress_result_through_abi() {
        val resultSignature = WinRTTypeSignature.int32()
        val progressSignature = WinRTTypeSignature.uint32()
        AsyncInfo.fromResultWithProgress<Int, UInt>(
            result = 42,
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = WinRTDelegateValueKind.UINT32,
            resultWriter = WinRTAsyncResultWriter { value, resultOut ->
                PlatformAbi.writeInt32(resultOut, value)
            },
        ).use { operation ->
            assertEquals(
                WinRTAsyncOperationWithProgressReference.interfaceId(resultSignature, progressSignature),
                operation.interfaceId,
            )
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocateInt32Slot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = operation.pointer,
                    slot = WinRTAsyncOperationWithProgressVftblSlots.GetResults,
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
            val failure = WinRTIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val action = FakeAsyncActionWithProgressReference(scope, WinRTAsyncStatus.Started, resultsFailure = failure)

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        action.await()
                    } catch (error: WinRTIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                action.complete(WinRTAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(action.resultsCalled)
            assertTrue(action.completedHandlerClosed())
        }
    }

    @Test
    fun async_action_with_progress_await_maps_error_cancelled_to_coroutine_cancellation() {
        PlatformAbi.confinedScope().use { scope ->
            runBlocking {
                val action = FakeAsyncActionWithProgressReference(
                    scope = scope,
                    statusState = WinRTAsyncStatus.Error,
                    errorCode = KnownHResults.ERROR_CANCELLED,
                )

                try {
                    action.await()
                } catch (error: CancellationException) {
                    assertEquals("WinRT async action was canceled.", error.message)
                    return@runBlocking
                }

                throw AssertionError("Expected CancellationException from canceled WinRT async action.")
            }
        }
    }

    @Test
    fun async_operation_with_progress_await_faults_and_closes_completed_handler_when_get_results_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRTIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val operation = FakeAsyncOperationWithProgressReference(
                scope = scope,
                statusState = WinRTAsyncStatus.Started,
                result = "ignored",
                resultsFailure = failure,
            )

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        operation.await()
                    } catch (error: WinRTIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                operation.complete(WinRTAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(operation.resultsCalled)
            assertTrue(operation.completedHandlerClosed())
        }
    }

    @Test
    fun async_operation_with_progress_await_maps_error_cancelled_to_coroutine_cancellation() {
        PlatformAbi.confinedScope().use { scope ->
            runBlocking {
                val operation = FakeAsyncOperationWithProgressReference(
                    scope = scope,
                    statusState = WinRTAsyncStatus.Error,
                    result = "unused",
                    errorCode = KnownHResults.ERROR_CANCELLED,
                )

                try {
                    operation.await()
                } catch (error: CancellationException) {
                    assertEquals("WinRT async operation was canceled.", error.message)
                    return@runBlocking
                }

                throw AssertionError("Expected CancellationException from canceled WinRT async operation.")
            }
        }
    }

    @Test
    fun async_operation_await_completes_with_result_and_maps_error_status() {
        PlatformAbi.confinedScope().use { scope ->
            runBlocking {
                val operation = FakeAsyncOperationReference(
                    scope = scope,
                    statusState = WinRTAsyncStatus.Started,
                    result = "done",
                )

                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    assertEquals("done", operation.await())
                }
                operation.complete(WinRTAsyncStatus.Completed)
                awaitJob.join()
                assertTrue(operation.resultsCalled)
                assertTrue(operation.completedHandlerClosed())

                val failedOperation = FakeAsyncOperationReference(
                    scope = scope,
                    statusState = WinRTAsyncStatus.Error,
                    result = "ignored",
                    errorCode = KnownHResults.E_ACCESSDENIED,
                )

                try {
                    failedOperation.await()
                } catch (error: WinRTAccessDeniedException) {
                    return@runBlocking
                }

                throw AssertionError("Expected WinRTAccessDeniedException from await().")
            }
        }
    }

    @Test
    fun async_operation_await_maps_error_cancelled_to_coroutine_cancellation() {
        PlatformAbi.confinedScope().use { scope ->
            runBlocking {
                val operation = FakeAsyncOperationReference(
                    scope = scope,
                    statusState = WinRTAsyncStatus.Error,
                    result = "unused",
                    errorCode = KnownHResults.ERROR_CANCELLED,
                )

                try {
                    operation.await()
                } catch (error: CancellationException) {
                    assertEquals("WinRT async operation was canceled.", error.message)
                    return@runBlocking
                }

                throw AssertionError("Expected CancellationException from canceled WinRT async operation.")
            }
        }
    }

    @Test
    fun async_operation_await_faults_and_closes_completed_handler_when_get_results_fails() {
        PlatformAbi.confinedScope().use { scope ->
            val failure = WinRTIllegalStateException("get results failed", KnownHResults.E_FAIL)
            val operation = FakeAsyncOperationReference(
                scope = scope,
                statusState = WinRTAsyncStatus.Started,
                result = "ignored",
                resultsFailure = failure,
            )

            runBlocking {
                val awaitJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        operation.await()
                    } catch (error: WinRTIllegalStateException) {
                        assertEquals(failure, error)
                        return@launch
                    }
                    throw AssertionError("Expected getResults failure from await().")
                }
                operation.complete(WinRTAsyncStatus.Completed)
                awaitJob.join()
            }

            assertTrue(operation.resultsCalled)
            assertTrue(operation.completedHandlerClosed())
        }
    }

    private class FakeAsyncActionReference(
        scope: NativeScope,
        private var statusState: WinRTAsyncStatus,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
        private val registrationFailure: Throwable? = null,
    ) : WinRTAsyncActionReference(PlatformAbi.allocateBytes(scope, 8)) {
        var resultsCalled = false
        var cancelCalled = false
        private var completedHandle: WinRTDelegateHandle? = null

        override fun status(): WinRTAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults() {
            resultsCalled = true
            resultsFailure?.let { throw it }
        }

        override fun cancel() {
            cancelCalled = true
            statusState = WinRTAsyncStatus.Canceled
        }

        override fun registerCompletedHandler(handle: WinRTDelegateHandle) {
            completedHandle = handle
            registrationFailure?.let { throw it }
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRTAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }

    private class FakeAsyncOperationReference(
        scope: NativeScope,
        private var statusState: WinRTAsyncStatus,
        private val result: String,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
    ) : WinRTAsyncOperationReference<String>(
        pointer = PlatformAbi.allocateBytes(scope, 8),
        interfaceId = Guid("11111111-2222-3333-4444-555555555555"),
        completedHandlerInterfaceId = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        resultReader = { error("Fake override should be used.") },
    ) {
        var resultsCalled = false
        private var completedHandle: WinRTDelegateHandle? = null

        override fun status(): WinRTAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults(): String {
            resultsCalled = true
            resultsFailure?.let { throw it }
            return result
        }

        override fun registerCompletedHandler(handle: WinRTDelegateHandle) {
            completedHandle = handle
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRTAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }

    private class FakeAsyncActionWithProgressReference(
        scope: NativeScope,
        private var statusState: WinRTAsyncStatus,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
    ) : WinRTAsyncActionWithProgressReference<Int>(
        pointer = PlatformAbi.allocateBytes(scope, 8),
        interfaceId = Guid("22222222-3333-4444-5555-666666666666"),
        progressHandlerInterfaceId = Guid("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"),
        completedHandlerInterfaceId = Guid("cccccccc-dddd-eeee-ffff-aaaaaaaaaaaa"),
    ) {
        var resultsCalled = false
        private var completedHandle: WinRTDelegateHandle? = null

        override fun status(): WinRTAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults() {
            resultsCalled = true
            resultsFailure?.let { throw it }
        }

        override fun whenCompleted(
            callback: (WinRTAsyncActionWithProgressReference<Int>, WinRTAsyncStatus) -> Unit,
        ): WinRTDelegateHandle {
            val handle = WinRTDelegateBridge.createUnitDelegate(
                iid = Guid("cccccccc-dddd-eeee-ffff-aaaaaaaaaaaa"),
                parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
            ) { args ->
                callback(this, WinRTAsyncStatus.fromAbi(args[1] as Int))
            }
            completedHandle = handle
            return handle
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRTAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }

    private class FakeAsyncOperationWithProgressReference(
        scope: NativeScope,
        private var statusState: WinRTAsyncStatus,
        private val result: String,
        private val errorCode: HResult = KnownHResults.S_OK,
        private val resultsFailure: Throwable? = null,
    ) : WinRTAsyncOperationWithProgressReference<String, Int>(
        pointer = PlatformAbi.allocateBytes(scope, 8),
        interfaceId = Guid("33333333-4444-5555-6666-777777777777"),
        progressHandlerInterfaceId = Guid("dddddddd-eeee-ffff-aaaa-bbbbbbbbbbbb"),
        completedHandlerInterfaceId = Guid("eeeeeeee-ffff-aaaa-bbbb-cccccccccccc"),
        resultReader = { error("Fake override should be used.") },
    ) {
        var resultsCalled = false
        private var completedHandle: WinRTDelegateHandle? = null

        override fun status(): WinRTAsyncStatus = statusState

        override fun errorCode(): HResult = errorCode

        override fun getResults(): String {
            resultsCalled = true
            resultsFailure?.let { throw it }
            return result
        }

        override fun whenCompleted(
            callback: (WinRTAsyncOperationWithProgressReference<String, Int>, WinRTAsyncStatus) -> Unit,
        ): WinRTDelegateHandle {
            val handle = WinRTDelegateBridge.createUnitDelegate(
                iid = Guid("eeeeeeee-ffff-aaaa-bbbb-cccccccccccc"),
                parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
            ) { args ->
                callback(this, WinRTAsyncStatus.fromAbi(args[1] as Int))
            }
            completedHandle = handle
            return handle
        }

        fun completedHandlerClosed(): Boolean =
            completedHandle?.let(::isDelegateHandleClosedForTesting) == true

        fun complete(newStatus: WinRTAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }
}

private fun isDelegateHandleClosedForTesting(handle: WinRTDelegateHandle): Boolean =
    try {
        handle.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, WinRTAsyncStatus.Completed.abiValue))
        false
    } catch (_: IllegalStateException) {
        true
    }

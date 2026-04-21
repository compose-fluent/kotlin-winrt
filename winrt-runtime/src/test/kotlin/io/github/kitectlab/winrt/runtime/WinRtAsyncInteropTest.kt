package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.util.concurrent.CompletionException
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
            it.invokeAbiForTesting(listOf(NativeInterop.nullPointer, WinRtAsyncStatus.Completed.abiValue))
        }

        assertEquals(WinRtAsyncStatus.Completed, capturedStatus)
    }

    @Test
    fun async_action_future_completes_immediately_when_already_completed() {
        Arena.ofConfined().use { arena ->
            val action = FakeAsyncActionReference(arena, WinRtAsyncStatus.Completed)

            val future = action.toCompletableFuture()

            assertTrue(future.isDone)
            assertFalse(future.isCompletedExceptionally)
            assertTrue(action.resultsCalled)
        }
    }

    @Test
    fun async_action_future_registers_completed_callback_and_cancels_underlying_info() {
        Arena.ofConfined().use { arena ->
            val action = FakeAsyncActionReference(arena, WinRtAsyncStatus.Started)

            val future = action.toCompletableFuture()
            assertFalse(future.isDone)

            action.complete(WinRtAsyncStatus.Completed)

            assertTrue(future.isDone)
            assertTrue(action.resultsCalled)

            val cancelledAction = FakeAsyncActionReference(arena, WinRtAsyncStatus.Started)
            val cancelledFuture = cancelledAction.toCompletableFuture()
            assertTrue(cancelledFuture.cancel(true))
            assertTrue(cancelledAction.cancelCalled)
        }
    }

    @Test
    fun async_operation_future_completes_with_result_and_maps_error_status() {
        Arena.ofConfined().use { arena ->
            val operation = FakeAsyncOperationReference(
                arena = arena,
                statusState = WinRtAsyncStatus.Started,
                result = "done",
            )

            val future = operation.toCompletableFuture()
            operation.complete(WinRtAsyncStatus.Completed)
            assertEquals("done", future.join())
            assertTrue(operation.resultsCalled)

            val failedOperation = FakeAsyncOperationReference(
                arena = arena,
                statusState = WinRtAsyncStatus.Error,
                result = "ignored",
                errorCode = KnownHResults.E_ACCESSDENIED,
            )

            try {
                failedOperation.toCompletableFuture().join()
            } catch (error: CompletionException) {
                assertTrue(error.cause is WinRtAccessDeniedException)
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

        fun complete(newStatus: WinRtAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(NativeInterop.nullPointer, newStatus.abiValue))
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

        fun complete(newStatus: WinRtAsyncStatus) {
            statusState = newStatus
            completedHandle?.invokeAbiForTesting(listOf(NativeInterop.nullPointer, newStatus.abiValue))
        }

        override fun close() = Unit
    }
}

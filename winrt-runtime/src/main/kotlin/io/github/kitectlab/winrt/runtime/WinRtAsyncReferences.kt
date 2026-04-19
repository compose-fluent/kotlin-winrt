package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

open class WinRtAsyncReferenceBase(
    pointer: MemorySegment,
    interfaceId: Guid,
) : ComObjectReference(pointer, interfaceId) {
    override fun invokeInt32Method(slot: Int): Int {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return resultOut.get(ValueLayout.JAVA_INT, 0)
        }
    }

    protected fun invokeHResultMethod(slot: Int): HResult = HResult(invokeInt32Method(slot))

    protected fun invokeNullableObjectMethod(slot: Int): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            val resultPointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (resultPointer == MemorySegment.NULL) null else IUnknownReference(resultPointer)
        }
    }
}

open class WinRtAsyncInfoReference(
    pointer: MemorySegment,
    interfaceId: Guid = WinRtAsyncInterfaceIds.IAsyncInfo,
) : WinRtAsyncReferenceBase(pointer, interfaceId) {
    open fun id(): UInt = invokeUInt32Method(WinRtAsyncInfoVftblSlots.Id)

    open fun status(): WinRtAsyncStatus =
        WinRtAsyncStatus.fromAbi(invokeInt32Method(WinRtAsyncInfoVftblSlots.Status))

    open fun errorCode(): HResult = invokeHResultMethod(WinRtAsyncInfoVftblSlots.ErrorCode)

    open fun cancel() {
        invokeUnitMethod(WinRtAsyncInfoVftblSlots.Cancel)
    }

    override fun close() {
        if (isDisposed) {
            return
        }
        try {
            invokeUnitMethod(WinRtAsyncInfoVftblSlots.Close)
        } finally {
            super.close()
        }
    }
}

open class WinRtAsyncActionReference(
    pointer: MemorySegment,
    interfaceId: Guid = WinRtAsyncInterfaceIds.IAsyncAction,
) : WinRtAsyncInfoReference(pointer, interfaceId) {
    open fun setCompletedHandler(handler: ComObjectReference) {
        invokeUnitMethodWithObjectArg(WinRtAsyncActionVftblSlots.PutCompleted, handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        invokeNullableObjectMethod(WinRtAsyncActionVftblSlots.GetCompleted)?.let { reference ->
            ComObjectReference(reference.pointer, WinRtAsyncInterfaceIds.AsyncActionCompletedHandler)
        }

    open fun getResults() {
        invokeUnitMethod(WinRtAsyncActionVftblSlots.GetResults)
    }

    open fun whenCompleted(callback: (WinRtAsyncActionReference, WinRtAsyncStatus) -> Unit): WinRtDelegateHandle {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = WinRtAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRtAsyncStatus.fromAbi(args[1] as Int))
        }
        registerCompletedHandler(handle)
        return handle
    }

    open fun toCompletableFuture(): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        completeAsyncAction(future, status())
        if (future.isDone) {
            return future
        }

        val handle = whenCompleted { _, completedStatus ->
            completeAsyncAction(future, completedStatus)
        }

        future.whenComplete { _, _ ->
            if (future.isCancelled) {
                runCatching(::cancel)
            }
        }
        future.whenCompleteAsync { _, _ ->
            handle.close()
        }

        if (!future.isDone) {
            completeAsyncAction(future, status())
        }

        return future
    }

    protected open fun registerCompletedHandler(handle: WinRtDelegateHandle) {
        handle.createReference().use(::setCompletedHandler)
    }

    protected open fun completeAsyncAction(
        future: CompletableFuture<Unit>,
        currentStatus: WinRtAsyncStatus,
    ) {
        when (currentStatus) {
            WinRtAsyncStatus.Started -> Unit
            WinRtAsyncStatus.Completed -> {
                getResults()
                future.complete(Unit)
            }

            WinRtAsyncStatus.Canceled -> future.completeExceptionally(
                WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
            )

            WinRtAsyncStatus.Error -> future.completeExceptionally(
                WinRtExceptionTranslator.exceptionFor(errorCode(), "WinRT async action"),
            )
        }
    }
}

open class WinRtAsyncOperationReference<T>(
    pointer: MemorySegment,
    interfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
    private val resultReader: (WinRtAsyncOperationReference<T>) -> T,
) : WinRtAsyncInfoReference(pointer, interfaceId) {
    open fun setCompletedHandler(handler: ComObjectReference) {
        invokeUnitMethodWithObjectArg(WinRtAsyncOperationVftblSlots.PutCompleted, handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        invokeNullableObjectMethod(WinRtAsyncOperationVftblSlots.GetCompleted)?.let { reference ->
            ComObjectReference(reference.pointer, completedHandlerInterfaceId)
        }

    open fun getResults(): T = resultReader(this)

    open fun whenCompleted(callback: (WinRtAsyncOperationReference<T>, WinRtAsyncStatus) -> Unit): WinRtDelegateHandle {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = completedHandlerInterfaceId,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRtAsyncStatus.fromAbi(args[1] as Int))
        }
        registerCompletedHandler(handle)
        return handle
    }

    open fun toCompletableFuture(): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        completeAsyncOperation(future, status())
        if (future.isDone) {
            return future
        }

        val handle = whenCompleted { _, completedStatus ->
            completeAsyncOperation(future, completedStatus)
        }

        future.whenComplete { _, _ ->
            if (future.isCancelled) {
                runCatching(::cancel)
            }
        }
        future.whenCompleteAsync { _, _ ->
            handle.close()
        }

        if (!future.isDone) {
            completeAsyncOperation(future, status())
        }

        return future
    }

    protected open fun registerCompletedHandler(handle: WinRtDelegateHandle) {
        handle.createReference().use(::setCompletedHandler)
    }

    protected open fun completeAsyncOperation(
        future: CompletableFuture<T>,
        currentStatus: WinRtAsyncStatus,
    ) {
        when (currentStatus) {
            WinRtAsyncStatus.Started -> Unit
            WinRtAsyncStatus.Completed -> future.complete(getResults())
            WinRtAsyncStatus.Canceled -> future.completeExceptionally(
                WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
            )

            WinRtAsyncStatus.Error -> future.completeExceptionally(
                WinRtExceptionTranslator.exceptionFor(errorCode(), "WinRT async operation"),
            )
        }
    }

    companion object {
        fun interfaceId(resultSignature: WinRtTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.IAsyncOperationGeneric,
                resultSignature,
            )

        fun completedHandlerInterfaceId(resultSignature: WinRtTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.AsyncOperationCompletedHandlerGeneric,
                resultSignature,
            )
    }
}

fun WinRtAsyncActionReference.await() {
    toCompletableFuture().join()
}

fun <T> WinRtAsyncOperationReference<T>.await(): T =
    toCompletableFuture().join()

fun WinRtAsyncActionReference.ensureCompleted(status: WinRtAsyncStatus = this.status()) {
    when (status) {
        WinRtAsyncStatus.Started -> throw IllegalStateException("Async action is still running.")
        WinRtAsyncStatus.Completed -> getResults()
        WinRtAsyncStatus.Canceled -> throw CancellationException("WinRT async action was canceled.")
        WinRtAsyncStatus.Error -> throw WinRtExceptionTranslator.exceptionFor(errorCode(), "WinRT async action")
    }
}

fun <T> WinRtAsyncOperationReference<T>.completeOperation(status: WinRtAsyncStatus = this.status()): T =
    when (status) {
        WinRtAsyncStatus.Started -> throw IllegalStateException("Async operation is still running.")
        WinRtAsyncStatus.Completed -> getResults()
        WinRtAsyncStatus.Canceled -> throw CancellationException("WinRT async operation was canceled.")
        WinRtAsyncStatus.Error -> throw WinRtExceptionTranslator.exceptionFor(errorCode(), "WinRT async operation")
    }

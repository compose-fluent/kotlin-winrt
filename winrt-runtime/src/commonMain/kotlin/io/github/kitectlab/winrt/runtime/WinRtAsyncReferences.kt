package io.github.kitectlab.winrt.runtime

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class WinRtAsyncReferenceBase(
    pointer: NativePointer,
    interfaceId: Guid,
) : ComObjectReference(pointer, interfaceId) {
    override fun invokeInt32Method(slot: Int): Int =
        RawAbiResultSupport.int32Result { resultOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
        }

    protected fun invokeHResultMethod(slot: Int): HResult = HResult(invokeInt32Method(slot))

    protected fun invokeNullableObjectMethod(slot: Int): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    resultOut,
                )
            },
            wrap = ::IUnknownReference,
        )
}

open class WinRtAsyncInfoReference(
    pointer: NativePointer,
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
    pointer: NativePointer,
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

    protected open fun registerCompletedHandler(handle: WinRtDelegateHandle) {
        handle.createReference().use(::setCompletedHandler)
    }

    internal open fun completeAsyncAction(
        currentStatus: WinRtAsyncStatus,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        when (currentStatus) {
            WinRtAsyncStatus.Started -> Unit
            WinRtAsyncStatus.Completed -> {
                getResults()
                onCompleted()
            }
            WinRtAsyncStatus.Canceled ->
                onCancelled()
            WinRtAsyncStatus.Error ->
                onError(platformExceptionFor(errorCode(), "WinRT async action"))
        }
    }
}

open class WinRtAsyncOperationReference<T>(
    pointer: NativePointer,
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

    protected open fun registerCompletedHandler(handle: WinRtDelegateHandle) {
        handle.createReference().use(::setCompletedHandler)
    }

    internal open fun completeAsyncOperation(
        currentStatus: WinRtAsyncStatus,
        onCompleted: (T) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        when (currentStatus) {
            WinRtAsyncStatus.Started -> Unit
            WinRtAsyncStatus.Completed -> onCompleted(getResults())
            WinRtAsyncStatus.Canceled ->
                onCancelled()
            WinRtAsyncStatus.Error ->
                onError(platformExceptionFor(errorCode(), "WinRT async operation"))
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

suspend fun WinRtAsyncActionReference.await() {
    suspendCancellableCoroutine { continuation ->
        completeAsyncAction(
            currentStatus = status(),
            onCompleted = { continuation.resume(Unit) },
            onCancelled = {
                continuation.resumeWithException(
                    WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                )
            },
            onError = continuation::resumeWithException,
        )
        if (continuation.isCompleted) {
            return@suspendCancellableCoroutine
        }

        val handle = whenCompleted { _, completedStatus ->
            completeAsyncAction(
                currentStatus = completedStatus,
                onCompleted = { continuation.resume(Unit) },
                onCancelled = {
                    continuation.resumeWithException(
                        WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                },
                onError = continuation::resumeWithException,
            )
        }
        continuation.invokeOnCancellation {
            runCatching(::cancel)
            handle.close()
        }
        if (!continuation.isCompleted) {
            completeAsyncAction(
                currentStatus = status(),
                onCompleted = { continuation.resume(Unit) },
                onCancelled = {
                    continuation.resumeWithException(
                        WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                },
                onError = continuation::resumeWithException,
            )
        }
        if (continuation.isCompleted) {
            handle.close()
        }
    }
}

suspend fun <T> WinRtAsyncOperationReference<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        completeAsyncOperation(
            currentStatus = status(),
            onCompleted = continuation::resume,
            onCancelled = {
                continuation.resumeWithException(
                    WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                )
            },
            onError = continuation::resumeWithException,
        )
        if (continuation.isCompleted) {
            return@suspendCancellableCoroutine
        }

        val handle = whenCompleted { _, completedStatus ->
            completeAsyncOperation(
                currentStatus = completedStatus,
                onCompleted = continuation::resume,
                onCancelled = {
                    continuation.resumeWithException(
                        WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                },
                onError = continuation::resumeWithException,
            )
        }
        continuation.invokeOnCancellation {
            runCatching(::cancel)
            handle.close()
        }
        if (!continuation.isCompleted) {
            completeAsyncOperation(
                currentStatus = status(),
                onCompleted = continuation::resume,
                onCancelled = {
                    continuation.resumeWithException(
                        WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                },
                onError = continuation::resumeWithException,
            )
        }
        if (continuation.isCompleted) {
            handle.close()
        }
    }

fun WinRtAsyncActionReference.ensureCompleted(status: WinRtAsyncStatus = this.status()) {
    when (status) {
        WinRtAsyncStatus.Started -> throw IllegalStateException("Async action is still running.")
        WinRtAsyncStatus.Completed -> getResults()
        WinRtAsyncStatus.Canceled -> throw WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED)
        WinRtAsyncStatus.Error -> throw platformExceptionFor(errorCode(), "WinRT async action")
    }
}

fun <T> WinRtAsyncOperationReference<T>.completeOperation(status: WinRtAsyncStatus = this.status()): T =
    when (status) {
        WinRtAsyncStatus.Started -> throw IllegalStateException("Async operation is still running.")
        WinRtAsyncStatus.Completed -> getResults()
        WinRtAsyncStatus.Canceled ->
            throw WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED)
        WinRtAsyncStatus.Error -> throw platformExceptionFor(errorCode(), "WinRT async operation")
    }

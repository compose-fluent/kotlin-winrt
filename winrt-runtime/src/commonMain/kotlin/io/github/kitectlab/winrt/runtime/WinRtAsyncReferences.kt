package io.github.kitectlab.winrt.runtime

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class WinRtAsyncReferenceBase(
    pointer: RawAddress,
    interfaceId: Guid,
) : ComObjectReference(pointer.asRawComPtr(), interfaceId) {
    private val asyncInfoView: WinRtAsyncInfoView = WinRtAsyncInfoView(comPtr)

    internal fun asAsyncInfoView(): WinRtAsyncInfoView = asyncInfoView
}

open class WinRtAsyncInfoReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRtAsyncInterfaceIds.IAsyncInfo,
) : WinRtAsyncReferenceBase(pointer, interfaceId) {
    open fun id(): UInt = asAsyncInfoView().id()

    open fun status(): WinRtAsyncStatus =
        asAsyncInfoView().status()

    open fun errorCode(): HResult = asAsyncInfoView().errorCode()

    open fun cancel() {
        asAsyncInfoView().cancel()
    }

    override fun close() {
        if (isDisposed) {
            return
        }
        try {
            asAsyncInfoView().closeAsyncInfo()
        } finally {
            super.close()
        }
    }
}

open class WinRtAsyncActionReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRtAsyncInterfaceIds.IAsyncAction,
) : WinRtAsyncInfoReference(pointer, interfaceId) {
    private val asyncActionView: WinRtAsyncActionView = WinRtAsyncActionView(comPtr)

    internal fun asAsyncActionView(): WinRtAsyncActionView = asyncActionView

    open fun setCompletedHandler(handler: ComObjectReference) {
        asAsyncActionView().setCompletedHandler(handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        asAsyncActionView().getCompletedHandler()

    open fun getResults() {
        asAsyncActionView().getResults()
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
                onError(ExceptionHelpers.exceptionFor(errorCode(), "WinRT async action"))
        }
    }
}

open class WinRtAsyncOperationReference<T>(
    pointer: RawAddress,
    interfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
    private val resultReader: (WinRtAsyncOperationReference<T>) -> T,
) : WinRtAsyncInfoReference(pointer, interfaceId) {
    private val asyncOperationView: WinRtAsyncOperationView = WinRtAsyncOperationView(comPtr, completedHandlerInterfaceId)

    internal fun asAsyncOperationView(): WinRtAsyncOperationView = asyncOperationView

    open fun setCompletedHandler(handler: ComObjectReference) {
        asAsyncOperationView().setCompletedHandler(handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        asAsyncOperationView().getCompletedHandler()

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
                onError(ExceptionHelpers.exceptionFor(errorCode(), "WinRT async operation"))
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
        WinRtAsyncStatus.Error -> throw ExceptionHelpers.exceptionFor(errorCode(), "WinRT async action")
    }
}

fun <T> WinRtAsyncOperationReference<T>.completeOperation(status: WinRtAsyncStatus = this.status()): T =
    when (status) {
        WinRtAsyncStatus.Started -> throw IllegalStateException("Async operation is still running.")
        WinRtAsyncStatus.Completed -> getResults()
        WinRtAsyncStatus.Canceled ->
            throw WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED)
        WinRtAsyncStatus.Error -> throw ExceptionHelpers.exceptionFor(errorCode(), "WinRT async operation")
    }

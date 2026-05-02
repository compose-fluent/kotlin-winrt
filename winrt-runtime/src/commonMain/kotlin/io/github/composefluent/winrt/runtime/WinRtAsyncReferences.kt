package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class WinRtAsyncReferenceBase internal constructor(
    comPtr: ComPtr,
) : ComObjectReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

    private val asyncInfoView: WinRtAsyncInfoView = WinRtAsyncInfoView(comPtr)

    internal fun asAsyncInfoView(): WinRtAsyncInfoView = asyncInfoView
}

open class WinRtAsyncInfoReference internal constructor(
    comPtr: ComPtr,
) : WinRtAsyncReferenceBase(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = WinRtAsyncInterfaceIds.IAsyncInfo,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

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

open class WinRtAsyncActionReference internal constructor(
    comPtr: ComPtr,
) : WinRtAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = WinRtAsyncInterfaceIds.IAsyncAction,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

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

open class WinRtAsyncOperationReference<T> internal constructor(
    comPtr: ComPtr,
    private val completedHandlerInterfaceId: Guid,
    private val resultReader: (WinRtAsyncOperationReference<T>) -> T,
) : WinRtAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        resultReader: (WinRtAsyncOperationReference<T>) -> T,
    ) : this(
        comPtr = ComPtr.create(pointer.asRawComPtr(), interfaceId),
        completedHandlerInterfaceId = completedHandlerInterfaceId,
        resultReader = resultReader,
    )

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

open class WinRtAsyncActionWithProgressReference<TProgress> internal constructor(
    comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) : WinRtAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
    ) : this(
        comPtr = ComPtr.create(pointer.asRawComPtr(), interfaceId),
        progressHandlerInterfaceId = progressHandlerInterfaceId,
        completedHandlerInterfaceId = completedHandlerInterfaceId,
    )

    private val asyncActionWithProgressView: WinRtAsyncActionWithProgressView =
        WinRtAsyncActionWithProgressView(comPtr, progressHandlerInterfaceId, completedHandlerInterfaceId)

    open fun setProgressHandler(handler: ComObjectReference) {
        asyncActionWithProgressView.setProgressHandler(handler)
    }

    open fun getProgressHandler(): ComObjectReference? =
        asyncActionWithProgressView.getProgressHandler()

    open fun setCompletedHandler(handler: ComObjectReference) {
        asyncActionWithProgressView.setCompletedHandler(handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        asyncActionWithProgressView.getCompletedHandler()

    open fun getResults() {
        asyncActionWithProgressView.getResults()
    }

    open fun whenCompleted(callback: (WinRtAsyncActionWithProgressReference<TProgress>, WinRtAsyncStatus) -> Unit): WinRtDelegateHandle {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = completedHandlerInterfaceId,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRtAsyncStatus.fromAbi(args[1] as Int))
        }
        handle.createReference().use(::setCompletedHandler)
        return handle
    }

    companion object {
        fun interfaceId(progressSignature: WinRtTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.IAsyncActionWithProgressGeneric,
                progressSignature,
            )

        fun progressHandlerInterfaceId(progressSignature: WinRtTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.AsyncActionProgressHandlerGeneric,
                progressSignature,
            )

        fun completedHandlerInterfaceId(progressSignature: WinRtTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.AsyncActionWithProgressCompletedHandlerGeneric,
                progressSignature,
            )
    }
}

open class WinRtAsyncOperationWithProgressReference<T, TProgress> internal constructor(
    comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
    private val resultReader: (WinRtAsyncOperationWithProgressReference<T, TProgress>) -> T,
) : WinRtAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        resultReader: (WinRtAsyncOperationWithProgressReference<T, TProgress>) -> T,
    ) : this(
        comPtr = ComPtr.create(pointer.asRawComPtr(), interfaceId),
        progressHandlerInterfaceId = progressHandlerInterfaceId,
        completedHandlerInterfaceId = completedHandlerInterfaceId,
        resultReader = resultReader,
    )

    private val asyncOperationWithProgressView: WinRtAsyncOperationWithProgressView =
        WinRtAsyncOperationWithProgressView(comPtr, progressHandlerInterfaceId, completedHandlerInterfaceId)

    open fun setProgressHandler(handler: ComObjectReference) {
        asyncOperationWithProgressView.setProgressHandler(handler)
    }

    open fun getProgressHandler(): ComObjectReference? =
        asyncOperationWithProgressView.getProgressHandler()

    open fun setCompletedHandler(handler: ComObjectReference) {
        asyncOperationWithProgressView.setCompletedHandler(handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        asyncOperationWithProgressView.getCompletedHandler()

    open fun getResults(): T = resultReader(this)

    open fun whenCompleted(callback: (WinRtAsyncOperationWithProgressReference<T, TProgress>, WinRtAsyncStatus) -> Unit): WinRtDelegateHandle {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = completedHandlerInterfaceId,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRtAsyncStatus.fromAbi(args[1] as Int))
        }
        handle.createReference().use(::setCompletedHandler)
        return handle
    }

    companion object {
        fun interfaceId(
            resultSignature: WinRtTypeSignature,
            progressSignature: WinRtTypeSignature,
        ): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.IAsyncOperationWithProgressGeneric,
                resultSignature,
                progressSignature,
            )

        fun progressHandlerInterfaceId(
            resultSignature: WinRtTypeSignature,
            progressSignature: WinRtTypeSignature,
        ): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.AsyncOperationProgressHandlerGeneric,
                resultSignature,
                progressSignature,
            )

        fun completedHandlerInterfaceId(
            resultSignature: WinRtTypeSignature,
            progressSignature: WinRtTypeSignature,
        ): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtAsyncInterfaceIds.AsyncOperationWithProgressCompletedHandlerGeneric,
                resultSignature,
                progressSignature,
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

suspend fun <TProgress> WinRtAsyncActionWithProgressReference<TProgress>.await() {
    suspendCancellableCoroutine { continuation ->
        fun complete(status: WinRtAsyncStatus) {
            when (status) {
                WinRtAsyncStatus.Started -> Unit
                WinRtAsyncStatus.Completed -> {
                    getResults()
                    continuation.resume(Unit)
                }
                WinRtAsyncStatus.Canceled ->
                    continuation.resumeWithException(
                        WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                WinRtAsyncStatus.Error ->
                    continuation.resumeWithException(ExceptionHelpers.exceptionFor(errorCode(), "WinRT async action"))
            }
        }

        complete(status())
        if (continuation.isCompleted) {
            return@suspendCancellableCoroutine
        }
        val handle = whenCompleted { _, completedStatus -> complete(completedStatus) }
        continuation.invokeOnCancellation {
            runCatching(::cancel)
            handle.close()
        }
        if (!continuation.isCompleted) {
            complete(status())
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

suspend fun <T, TProgress> WinRtAsyncOperationWithProgressReference<T, TProgress>.await(): T =
    suspendCancellableCoroutine { continuation ->
        fun complete(status: WinRtAsyncStatus) {
            when (status) {
                WinRtAsyncStatus.Started -> Unit
                WinRtAsyncStatus.Completed -> continuation.resume(getResults())
                WinRtAsyncStatus.Canceled ->
                    continuation.resumeWithException(
                        WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                WinRtAsyncStatus.Error ->
                    continuation.resumeWithException(ExceptionHelpers.exceptionFor(errorCode(), "WinRT async operation"))
            }
        }

        complete(status())
        if (continuation.isCompleted) {
            return@suspendCancellableCoroutine
        }
        val handle = whenCompleted { _, completedStatus -> complete(completedStatus) }
        continuation.invokeOnCancellation {
            runCatching(::cancel)
            handle.close()
        }
        if (!continuation.isCompleted) {
            complete(status())
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

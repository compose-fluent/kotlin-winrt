@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
                try {
                    getResults()
                    onCompleted()
                } catch (error: Throwable) {
                    onError(error)
                }
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
            WinRtAsyncStatus.Completed ->
                try {
                    onCompleted(getResults())
                } catch (error: Throwable) {
                    onError(error)
                }
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

object WinRtAsyncProjectionInterop {
    fun <T> operation(
        pointer: RawAddress,
        resultSignature: WinRtTypeSignature,
        resultOut: (NativeScope) -> RawAddress,
        resultReader: (RawAddress) -> T,
    ): WinRtAsyncOperationReference<T> =
        WinRtAsyncOperationReference(
            pointer = pointer,
            interfaceId = WinRtAsyncOperationReference.interfaceId(resultSignature),
            completedHandlerInterfaceId = WinRtAsyncOperationReference.completedHandlerInterfaceId(resultSignature),
            resultReader = { operation ->
                PlatformAbi.confinedScope().use { scope ->
                    val operationResultOut = resultOut(scope)
                    val operationHr = ComVtableInvoker.invokeArgs(
                        operation.pointer,
                        WinRtAsyncOperationVftblSlots.GetResults,
                        operationResultOut,
                    )
                    WinRtPlatformApi.checkSucceededRaw(operationHr)
                    resultReader(operationResultOut)
                }
            },
        )

    fun <T, TProgress> operationWithProgress(
        pointer: RawAddress,
        resultSignature: WinRtTypeSignature,
        progressSignature: WinRtTypeSignature,
        resultOut: (NativeScope) -> RawAddress,
        resultReader: (RawAddress) -> T,
    ): WinRtAsyncOperationWithProgressReference<T, TProgress> =
        WinRtAsyncOperationWithProgressReference(
            pointer = pointer,
            interfaceId = WinRtAsyncOperationWithProgressReference.interfaceId(resultSignature, progressSignature),
            progressHandlerInterfaceId = WinRtAsyncOperationWithProgressReference.progressHandlerInterfaceId(resultSignature, progressSignature),
            completedHandlerInterfaceId = WinRtAsyncOperationWithProgressReference.completedHandlerInterfaceId(resultSignature, progressSignature),
            resultReader = { operation ->
                PlatformAbi.confinedScope().use { scope ->
                    val operationResultOut = resultOut(scope)
                    val operationHr = ComVtableInvoker.invokeArgs(
                        operation.pointer,
                        WinRtAsyncOperationWithProgressVftblSlots.GetResults,
                        operationResultOut,
                    )
                    WinRtPlatformApi.checkSucceededRaw(operationHr)
                    resultReader(operationResultOut)
                }
            },
        )

    fun <TProgress> actionWithProgress(
        pointer: RawAddress,
        progressSignature: WinRtTypeSignature,
    ): WinRtAsyncActionWithProgressReference<TProgress> =
        WinRtAsyncActionWithProgressReference(
            pointer = pointer,
            interfaceId = WinRtAsyncActionWithProgressReference.interfaceId(progressSignature),
            progressHandlerInterfaceId = WinRtAsyncActionWithProgressReference.progressHandlerInterfaceId(progressSignature),
            completedHandlerInterfaceId = WinRtAsyncActionWithProgressReference.completedHandlerInterfaceId(progressSignature),
        )
}

private class WinRtAsyncAwaitState<T>(
    private val continuation: CancellableContinuation<T>,
    private val cancelAsyncInfo: () -> Unit,
) {
    private val terminal = AtomicInt(0)
    private val handleCloseRequested = AtomicInt(0)
    private val completedHandle = AtomicReference<WinRtDelegateHandle?>(null)

    val isTerminal: Boolean
        get() = terminal.load() != 0 || continuation.isCompleted

    fun installCancellation() {
        continuation.invokeOnCancellation {
            if (terminal.compareAndSet(0, 1)) {
                runCatching(cancelAsyncInfo)
                closeCompletedHandle()
            }
        }
    }

    fun attachCompletedHandle(handle: WinRtDelegateHandle) {
        completedHandle.store(handle)
        if (handleCloseRequested.load() != 0 || isTerminal) {
            closeCompletedHandle()
        }
    }

    fun resume(value: T): Boolean =
        complete {
            continuation.resume(value)
        }

    fun resumeWithException(error: Throwable): Boolean =
        complete {
            continuation.resumeWithException(error)
        }

    private fun complete(block: () -> Unit): Boolean {
        if (!terminal.compareAndSet(0, 1)) {
            return false
        }
        try {
            block()
        } finally {
            closeCompletedHandle()
        }
        return true
    }

    private fun closeCompletedHandle() {
        handleCloseRequested.store(1)
        while (true) {
            val handle = completedHandle.load() ?: return
            if (completedHandle.compareAndSet(handle, null)) {
                handle.close()
                return
            }
        }
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
        val awaitState = WinRtAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRtAsyncStatus) {
            completeAsyncAction(
                currentStatus = status,
                onCompleted = { awaitState.resume(Unit) },
                onCancelled = {
                    awaitState.resumeWithException(
                        WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                },
                onError = awaitState::resumeWithException,
            )
        }

        complete(status())
        if (awaitState.isTerminal) {
            return@suspendCancellableCoroutine
        }

        val handle = whenCompleted { _, completedStatus ->
            complete(completedStatus)
        }
        awaitState.attachCompletedHandle(handle)
        if (!awaitState.isTerminal) {
            complete(status())
        }
    }
}

suspend fun <TProgress> WinRtAsyncActionWithProgressReference<TProgress>.await() {
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRtAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRtAsyncStatus) {
            when (status) {
                WinRtAsyncStatus.Started -> Unit
                WinRtAsyncStatus.Completed -> {
                    try {
                        getResults()
                        awaitState.resume(Unit)
                    } catch (error: Throwable) {
                        awaitState.resumeWithException(error)
                    }
                }
                WinRtAsyncStatus.Canceled ->
                    awaitState.resumeWithException(
                        WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                WinRtAsyncStatus.Error ->
                    awaitState.resumeWithException(ExceptionHelpers.exceptionFor(errorCode(), "WinRT async action"))
            }
        }

        complete(status())
        if (awaitState.isTerminal) {
            return@suspendCancellableCoroutine
        }
        val handle = whenCompleted { _, completedStatus -> complete(completedStatus) }
        awaitState.attachCompletedHandle(handle)
        if (!awaitState.isTerminal) {
            complete(status())
        }
    }
}

suspend fun <T> WinRtAsyncOperationReference<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRtAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRtAsyncStatus) {
            completeAsyncOperation(
                currentStatus = status,
                onCompleted = awaitState::resume,
                onCancelled = {
                    awaitState.resumeWithException(
                        WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                },
                onError = awaitState::resumeWithException,
            )
        }

        complete(status())
        if (awaitState.isTerminal) {
            return@suspendCancellableCoroutine
        }

        val handle = whenCompleted { _, completedStatus ->
            complete(completedStatus)
        }
        awaitState.attachCompletedHandle(handle)
        if (!awaitState.isTerminal) {
            complete(status())
        }
    }

suspend fun <T, TProgress> WinRtAsyncOperationWithProgressReference<T, TProgress>.await(): T =
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRtAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRtAsyncStatus) {
            when (status) {
                WinRtAsyncStatus.Started -> Unit
                WinRtAsyncStatus.Completed ->
                    try {
                        awaitState.resume(getResults())
                    } catch (error: Throwable) {
                        awaitState.resumeWithException(error)
                    }
                WinRtAsyncStatus.Canceled ->
                    awaitState.resumeWithException(
                        WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                    )
                WinRtAsyncStatus.Error ->
                    awaitState.resumeWithException(ExceptionHelpers.exceptionFor(errorCode(), "WinRT async operation"))
            }
        }

        complete(status())
        if (awaitState.isTerminal) {
            return@suspendCancellableCoroutine
        }
        val handle = whenCompleted { _, completedStatus -> complete(completedStatus) }
        awaitState.attachCompletedHandle(handle)
        if (!awaitState.isTerminal) {
            complete(status())
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

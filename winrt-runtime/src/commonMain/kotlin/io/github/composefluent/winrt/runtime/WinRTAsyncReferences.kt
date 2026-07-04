@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class WinRTAsyncReferenceBase internal constructor(
    comPtr: ComPtr,
) : ComObjectReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

    private val asyncInfoView: WinRTAsyncInfoView = WinRTAsyncInfoView(comPtr)

    internal fun asAsyncInfoView(): WinRTAsyncInfoView = asyncInfoView
}

open class WinRTAsyncInfoReference internal constructor(
    comPtr: ComPtr,
) : WinRTAsyncReferenceBase(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = WinRTAsyncInterfaceIds.IAsyncInfo,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

    open fun id(): UInt = asAsyncInfoView().id()

    open fun status(): WinRTAsyncStatus =
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

open class WinRTAsyncActionReference internal constructor(
    comPtr: ComPtr,
) : WinRTAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = WinRTAsyncInterfaceIds.IAsyncAction,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

    private val asyncActionView: WinRTAsyncActionView = WinRTAsyncActionView(comPtr)

    internal fun asAsyncActionView(): WinRTAsyncActionView = asyncActionView

    open fun setCompletedHandler(handler: ComObjectReference) {
        asAsyncActionView().setCompletedHandler(handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        asAsyncActionView().getCompletedHandler()

    open fun getResults() {
        asAsyncActionView().getResults()
    }

    open fun whenCompleted(callback: (WinRTAsyncActionReference, WinRTAsyncStatus) -> Unit): WinRTDelegateHandle {
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = WinRTAsyncInterfaceIds.AsyncActionCompletedHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRTAsyncStatus.fromAbi(args[1] as Int))
        }
        return handle.registerOrClose(::registerCompletedHandler)
    }

    protected open fun registerCompletedHandler(handle: WinRTDelegateHandle) {
        handle.createReference().use(::setCompletedHandler)
    }

    internal open fun completeAsyncAction(
        currentStatus: WinRTAsyncStatus,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        when (currentStatus) {
            WinRTAsyncStatus.Started -> Unit
            WinRTAsyncStatus.Completed -> {
                try {
                    getResults()
                    onCompleted()
                } catch (error: Throwable) {
                    onError(error)
                }
            }
            WinRTAsyncStatus.Canceled ->
                onCancelled()
            WinRTAsyncStatus.Error ->
                onError(winRTAsyncErrorException(errorCode(), "action"))
        }
    }
}

open class WinRTAsyncOperationReference<T> internal constructor(
    comPtr: ComPtr,
    private val completedHandlerInterfaceId: Guid,
    private val resultReader: (WinRTAsyncOperationReference<T>) -> T,
) : WinRTAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        resultReader: (WinRTAsyncOperationReference<T>) -> T,
    ) : this(
        comPtr = ComPtr.create(pointer.asRawComPtr(), interfaceId),
        completedHandlerInterfaceId = completedHandlerInterfaceId,
        resultReader = resultReader,
    )

    private val asyncOperationView: WinRTAsyncOperationView = WinRTAsyncOperationView(comPtr, completedHandlerInterfaceId)

    internal fun asAsyncOperationView(): WinRTAsyncOperationView = asyncOperationView

    open fun setCompletedHandler(handler: ComObjectReference) {
        asAsyncOperationView().setCompletedHandler(handler)
    }

    open fun getCompletedHandler(): ComObjectReference? =
        asAsyncOperationView().getCompletedHandler()

    open fun getResults(): T = resultReader(this)

    open fun whenCompleted(callback: (WinRTAsyncOperationReference<T>, WinRTAsyncStatus) -> Unit): WinRTDelegateHandle {
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = completedHandlerInterfaceId,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRTAsyncStatus.fromAbi(args[1] as Int))
        }
        return handle.registerOrClose(::registerCompletedHandler)
    }

    protected open fun registerCompletedHandler(handle: WinRTDelegateHandle) {
        handle.createReference().use(::setCompletedHandler)
    }

    internal open fun completeAsyncOperation(
        currentStatus: WinRTAsyncStatus,
        onCompleted: (T) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        when (currentStatus) {
            WinRTAsyncStatus.Started -> Unit
            WinRTAsyncStatus.Completed ->
                try {
                    onCompleted(getResults())
                } catch (error: Throwable) {
                    onError(error)
                }
            WinRTAsyncStatus.Canceled ->
                onCancelled()
            WinRTAsyncStatus.Error ->
                onError(winRTAsyncErrorException(errorCode(), "operation"))
        }
    }

    companion object {
        fun interfaceId(resultSignature: WinRTTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.IAsyncOperationGeneric,
                resultSignature,
            )

        fun completedHandlerInterfaceId(resultSignature: WinRTTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.AsyncOperationCompletedHandlerGeneric,
                resultSignature,
            )
    }
}

open class WinRTAsyncActionWithProgressReference<TProgress> internal constructor(
    comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) : WinRTAsyncInfoReference(comPtr) {
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

    private val asyncActionWithProgressView: WinRTAsyncActionWithProgressView =
        WinRTAsyncActionWithProgressView(comPtr, progressHandlerInterfaceId, completedHandlerInterfaceId)

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

    open fun whenCompleted(callback: (WinRTAsyncActionWithProgressReference<TProgress>, WinRTAsyncStatus) -> Unit): WinRTDelegateHandle {
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = completedHandlerInterfaceId,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRTAsyncStatus.fromAbi(args[1] as Int))
        }
        return handle.registerOrClose { registeredHandle ->
            registeredHandle.createReference().use(::setCompletedHandler)
        }
    }

    companion object {
        fun interfaceId(progressSignature: WinRTTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.IAsyncActionWithProgressGeneric,
                progressSignature,
            )

        fun progressHandlerInterfaceId(progressSignature: WinRTTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.AsyncActionProgressHandlerGeneric,
                progressSignature,
            )

        fun completedHandlerInterfaceId(progressSignature: WinRTTypeSignature): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.AsyncActionWithProgressCompletedHandlerGeneric,
                progressSignature,
            )
    }
}

object WinRTAsyncProjectionInterop {
    fun <T> operation(
        pointer: RawAddress,
        resultSignature: WinRTTypeSignature,
        resultOut: (NativeScope) -> RawAddress,
        resultReader: (RawAddress) -> T,
    ): WinRTAsyncOperationReference<T> =
        WinRTAsyncOperationReference(
            pointer = pointer,
            interfaceId = WinRTAsyncOperationReference.interfaceId(resultSignature),
            completedHandlerInterfaceId = WinRTAsyncOperationReference.completedHandlerInterfaceId(resultSignature),
            resultReader = { operation ->
                PlatformAbi.confinedScope().use { scope ->
                    val operationResultOut = resultOut(scope)
                    val operationHr = ComVtableInvoker.invokeArgs(
                        operation.pointer,
                        WinRTAsyncOperationVftblSlots.GetResults,
                        operationResultOut,
                    )
                    WinRTPlatformApi.checkSucceededRaw(operationHr)
                    resultReader(operationResultOut)
                }
            },
        )

    fun <T, TProgress> operationWithProgress(
        pointer: RawAddress,
        resultSignature: WinRTTypeSignature,
        progressSignature: WinRTTypeSignature,
        resultOut: (NativeScope) -> RawAddress,
        resultReader: (RawAddress) -> T,
    ): WinRTAsyncOperationWithProgressReference<T, TProgress> =
        WinRTAsyncOperationWithProgressReference(
            pointer = pointer,
            interfaceId = WinRTAsyncOperationWithProgressReference.interfaceId(resultSignature, progressSignature),
            progressHandlerInterfaceId = WinRTAsyncOperationWithProgressReference.progressHandlerInterfaceId(resultSignature, progressSignature),
            completedHandlerInterfaceId = WinRTAsyncOperationWithProgressReference.completedHandlerInterfaceId(resultSignature, progressSignature),
            resultReader = { operation ->
                PlatformAbi.confinedScope().use { scope ->
                    val operationResultOut = resultOut(scope)
                    val operationHr = ComVtableInvoker.invokeArgs(
                        operation.pointer,
                        WinRTAsyncOperationWithProgressVftblSlots.GetResults,
                        operationResultOut,
                    )
                    WinRTPlatformApi.checkSucceededRaw(operationHr)
                    resultReader(operationResultOut)
                }
            },
        )

    fun <TProgress> actionWithProgress(
        pointer: RawAddress,
        progressSignature: WinRTTypeSignature,
    ): WinRTAsyncActionWithProgressReference<TProgress> =
        WinRTAsyncActionWithProgressReference(
            pointer = pointer,
            interfaceId = WinRTAsyncActionWithProgressReference.interfaceId(progressSignature),
            progressHandlerInterfaceId = WinRTAsyncActionWithProgressReference.progressHandlerInterfaceId(progressSignature),
            completedHandlerInterfaceId = WinRTAsyncActionWithProgressReference.completedHandlerInterfaceId(progressSignature),
        )
}

private inline fun WinRTDelegateHandle.registerOrClose(
    register: (WinRTDelegateHandle) -> Unit,
): WinRTDelegateHandle =
    try {
        register(this)
        this
    } catch (error: Throwable) {
        close()
        throw error
    }

private class WinRTAsyncAwaitState<T>(
    private val continuation: CancellableContinuation<T>,
    private val cancelAsyncInfo: () -> Unit,
) {
    private val terminal = AtomicInt(0)
    private val handleCloseRequested = AtomicInt(0)
    private val completedHandle = AtomicReference<WinRTDelegateHandle?>(null)

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

    fun attachCompletedHandle(handle: WinRTDelegateHandle) {
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

internal fun winRTAsyncCancellationException(kind: String): CancellationException =
    CancellationException("WinRT async $kind was canceled.")

internal fun winRTAsyncErrorException(
    hResult: HResult,
    kind: String,
): Throwable =
    if (hResult == KnownHResults.ERROR_CANCELLED) {
        winRTAsyncCancellationException(kind)
    } else {
        ExceptionHelpers.exceptionFor(hResult, "WinRT async $kind")
    }

open class WinRTAsyncOperationWithProgressReference<T, TProgress> internal constructor(
    comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
    private val resultReader: (WinRTAsyncOperationWithProgressReference<T, TProgress>) -> T,
) : WinRTAsyncInfoReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        resultReader: (WinRTAsyncOperationWithProgressReference<T, TProgress>) -> T,
    ) : this(
        comPtr = ComPtr.create(pointer.asRawComPtr(), interfaceId),
        progressHandlerInterfaceId = progressHandlerInterfaceId,
        completedHandlerInterfaceId = completedHandlerInterfaceId,
        resultReader = resultReader,
    )

    private val asyncOperationWithProgressView: WinRTAsyncOperationWithProgressView =
        WinRTAsyncOperationWithProgressView(comPtr, progressHandlerInterfaceId, completedHandlerInterfaceId)

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

    open fun whenCompleted(callback: (WinRTAsyncOperationWithProgressReference<T, TProgress>, WinRTAsyncStatus) -> Unit): WinRTDelegateHandle {
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = completedHandlerInterfaceId,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.INT32),
        ) { args ->
            callback(this, WinRTAsyncStatus.fromAbi(args[1] as Int))
        }
        return handle.registerOrClose { registeredHandle ->
            registeredHandle.createReference().use(::setCompletedHandler)
        }
    }

    companion object {
        fun interfaceId(
            resultSignature: WinRTTypeSignature,
            progressSignature: WinRTTypeSignature,
        ): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.IAsyncOperationWithProgressGeneric,
                resultSignature,
                progressSignature,
            )

        fun progressHandlerInterfaceId(
            resultSignature: WinRTTypeSignature,
            progressSignature: WinRTTypeSignature,
        ): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.AsyncOperationProgressHandlerGeneric,
                resultSignature,
                progressSignature,
            )

        fun completedHandlerInterfaceId(
            resultSignature: WinRTTypeSignature,
            progressSignature: WinRTTypeSignature,
        ): Guid =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRTAsyncInterfaceIds.AsyncOperationWithProgressCompletedHandlerGeneric,
                resultSignature,
                progressSignature,
            )
    }
}

suspend fun WinRTAsyncActionReference.await() {
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRTAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRTAsyncStatus) {
            completeAsyncAction(
                currentStatus = status,
                onCompleted = { awaitState.resume(Unit) },
                onCancelled = {
                    awaitState.resumeWithException(
                        winRTAsyncCancellationException("action"),
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

suspend fun <TProgress> WinRTAsyncActionWithProgressReference<TProgress>.await() {
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRTAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRTAsyncStatus) {
            when (status) {
                WinRTAsyncStatus.Started -> Unit
                WinRTAsyncStatus.Completed -> {
                    try {
                        getResults()
                        awaitState.resume(Unit)
                    } catch (error: Throwable) {
                        awaitState.resumeWithException(error)
                    }
                }
                WinRTAsyncStatus.Canceled ->
                    awaitState.resumeWithException(
                        winRTAsyncCancellationException("action"),
                    )
                WinRTAsyncStatus.Error ->
                    awaitState.resumeWithException(winRTAsyncErrorException(errorCode(), "action"))
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

suspend fun <T> WinRTAsyncOperationReference<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRTAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRTAsyncStatus) {
            completeAsyncOperation(
                currentStatus = status,
                onCompleted = awaitState::resume,
                onCancelled = {
                    awaitState.resumeWithException(
                        winRTAsyncCancellationException("operation"),
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

suspend fun <T, TProgress> WinRTAsyncOperationWithProgressReference<T, TProgress>.await(): T =
    suspendCancellableCoroutine { continuation ->
        val awaitState = WinRTAsyncAwaitState(continuation, ::cancel)
        awaitState.installCancellation()
        fun complete(status: WinRTAsyncStatus) {
            when (status) {
                WinRTAsyncStatus.Started -> Unit
                WinRTAsyncStatus.Completed ->
                    try {
                        awaitState.resume(getResults())
                    } catch (error: Throwable) {
                        awaitState.resumeWithException(error)
                    }
                WinRTAsyncStatus.Canceled ->
                    awaitState.resumeWithException(
                        winRTAsyncCancellationException("operation"),
                    )
                WinRTAsyncStatus.Error ->
                    awaitState.resumeWithException(winRTAsyncErrorException(errorCode(), "operation"))
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

fun WinRTAsyncActionReference.ensureCompleted(status: WinRTAsyncStatus = this.status()) {
    when (status) {
        WinRTAsyncStatus.Started -> throw IllegalStateException("Async action is still running.")
        WinRTAsyncStatus.Completed -> getResults()
        WinRTAsyncStatus.Canceled -> throw winRTAsyncCancellationException("action")
        WinRTAsyncStatus.Error -> throw winRTAsyncErrorException(errorCode(), "action")
    }
}

fun <T> WinRTAsyncOperationReference<T>.completeOperation(status: WinRTAsyncStatus = this.status()): T =
    when (status) {
        WinRTAsyncStatus.Started -> throw IllegalStateException("Async operation is still running.")
        WinRTAsyncStatus.Completed -> getResults()
        WinRTAsyncStatus.Canceled ->
            throw winRTAsyncCancellationException("operation")
        WinRTAsyncStatus.Error -> throw winRTAsyncErrorException(errorCode(), "operation")
    }

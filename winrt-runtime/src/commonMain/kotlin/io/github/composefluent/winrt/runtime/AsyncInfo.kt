package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

fun interface WinRTAsyncResultWriter<T> {
    fun writeResult(value: T, resultOut: RawAddress)
}

class WinRTAsyncCancellation internal constructor(
    private val adapter: WinRTTaskToAsyncInfoAdapter<*>,
) {
    val isCancellationRequested: Boolean
        get() = adapter.status() == WinRTAsyncStatus.Canceled
}

class WinRTAsyncProgressReporter<T> internal constructor(
    private val adapter: WinRTTaskToAsyncInfoAdapter<*>,
    private val progressValueKind: WinRTDelegateValueKind,
) {
    fun report(value: T) {
        adapter.reportProgress(value, progressValueKind)
    }
}

object AsyncInfo {
    fun completedAction(): WinRTAsyncActionReference =
        actionReference(WinRTTaskToAsyncInfoAdapter.completed(Unit))

    fun canceledAction(): WinRTAsyncActionReference =
        actionReference(WinRTTaskToAsyncInfoAdapter.canceled())

    fun actionFromException(error: Throwable): WinRTAsyncActionReference =
        actionReference(WinRTTaskToAsyncInfoAdapter.failed(error))

    fun runAction(
        scope: CoroutineScope,
        block: suspend (WinRTAsyncCancellation) -> Unit,
    ): WinRTAsyncActionReference {
        val adapter = WinRTTaskToAsyncInfoAdapter.started<Unit>()
        adapter.attachJob(
            scope.launch {
                block(WinRTAsyncCancellation(adapter))
            },
        )
        return actionReference(adapter)
    }

    fun <TProgress> completedActionWithProgress(
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
    ): WinRTAsyncActionWithProgressReference<TProgress> =
        actionWithProgressReference(
            adapter = WinRTTaskToAsyncInfoAdapter.completed(Unit),
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
        )

    fun <TProgress> canceledActionWithProgress(
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
    ): WinRTAsyncActionWithProgressReference<TProgress> =
        actionWithProgressReference(
            adapter = WinRTTaskToAsyncInfoAdapter.canceled(),
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
        )

    fun <TProgress> actionWithProgressFromException(
        error: Throwable,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
    ): WinRTAsyncActionWithProgressReference<TProgress> =
        actionWithProgressReference(
            adapter = WinRTTaskToAsyncInfoAdapter.failed(error),
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
        )

    fun <TProgress> runActionWithProgress(
        scope: CoroutineScope,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
        block: suspend (WinRTAsyncCancellation, WinRTAsyncProgressReporter<TProgress>) -> Unit,
    ): WinRTAsyncActionWithProgressReference<TProgress> {
        val adapter = WinRTTaskToAsyncInfoAdapter.started<Unit>()
        adapter.attachJob(
            scope.launch {
                block(
                    WinRTAsyncCancellation(adapter),
                    WinRTAsyncProgressReporter(adapter, progressValueKind),
                )
            },
        )
        return actionWithProgressReference(adapter, progressSignature, progressValueKind)
    }

    fun <T> fromResult(
        result: T,
        resultSignature: WinRTTypeSignature,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationReference<T> =
        operationReference(
            adapter = WinRTTaskToAsyncInfoAdapter.completed(result),
            resultSignature = resultSignature,
            resultWriter = resultWriter,
        )

    fun <T> operationFromException(
        error: Throwable,
        resultSignature: WinRTTypeSignature,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationReference<T> =
        operationReference(
            adapter = WinRTTaskToAsyncInfoAdapter.failed(error),
            resultSignature = resultSignature,
            resultWriter = resultWriter,
        )

    fun <T> canceledOperation(
        resultSignature: WinRTTypeSignature,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationReference<T> =
        operationReference(
            adapter = WinRTTaskToAsyncInfoAdapter.canceled(),
            resultSignature = resultSignature,
            resultWriter = resultWriter,
        )

    fun <T, TProgress> fromResultWithProgress(
        result: T,
        resultSignature: WinRTTypeSignature,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationWithProgressReference<T, TProgress> =
        operationWithProgressReference(
            adapter = WinRTTaskToAsyncInfoAdapter.completed(result),
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
            resultWriter = resultWriter,
        )

    fun <T, TProgress> operationWithProgressFromException(
        error: Throwable,
        resultSignature: WinRTTypeSignature,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationWithProgressReference<T, TProgress> =
        operationWithProgressReference(
            adapter = WinRTTaskToAsyncInfoAdapter.failed(error),
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
            resultWriter = resultWriter,
        )

    fun <T, TProgress> canceledOperationWithProgress(
        resultSignature: WinRTTypeSignature,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationWithProgressReference<T, TProgress> =
        operationWithProgressReference(
            adapter = WinRTTaskToAsyncInfoAdapter.canceled(),
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
            resultWriter = resultWriter,
        )

    private fun actionReference(
        adapter: WinRTTaskToAsyncInfoAdapter<Unit>,
    ): WinRTAsyncActionReference {
        val host = createAsyncHost(
            adapter = adapter,
            defaultInterfaceId = WinRTAsyncInterfaceIds.IAsyncAction,
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncActionInterfaceDefinition(),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncAction",
        )
        adapter.selfReference = { host.createReference(WinRTAsyncInterfaceIds.IAsyncAction) }
        return WinRTAsyncActionReference(host.createReference(WinRTAsyncInterfaceIds.IAsyncAction).comPtr)
    }

    private fun <TProgress> actionWithProgressReference(
        adapter: WinRTTaskToAsyncInfoAdapter<Unit>,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
    ): WinRTAsyncActionWithProgressReference<TProgress> {
        val interfaceId = WinRTAsyncActionWithProgressReference.interfaceId(progressSignature)
        val progressHandlerInterfaceId = WinRTAsyncActionWithProgressReference.progressHandlerInterfaceId(progressSignature)
        val completedHandlerInterfaceId = WinRTAsyncActionWithProgressReference.completedHandlerInterfaceId(progressSignature)
        val host = createAsyncHost(
            adapter = adapter,
            defaultInterfaceId = interfaceId,
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncActionWithProgressInterfaceDefinition(
                    interfaceId = interfaceId,
                    progressHandlerInterfaceId = progressHandlerInterfaceId,
                    completedHandlerInterfaceId = completedHandlerInterfaceId,
                    progressValueKind = progressValueKind,
                ),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncActionWithProgress",
        )
        adapter.selfReference = { host.createReference(interfaceId) }
        return WinRTAsyncActionWithProgressReference(
            comPtr = host.createReference(interfaceId).comPtr,
            progressHandlerInterfaceId = progressHandlerInterfaceId,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
        )
    }

    private fun <T> operationReference(
        adapter: WinRTTaskToAsyncInfoAdapter<T>,
        resultSignature: WinRTTypeSignature,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationReference<T> {
        val interfaceId = WinRTAsyncOperationReference.interfaceId(resultSignature)
        val completedHandlerInterfaceId = WinRTAsyncOperationReference.completedHandlerInterfaceId(resultSignature)
        val host = createAsyncHost(
            adapter = adapter,
            defaultInterfaceId = interfaceId,
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncOperationInterfaceDefinition(
                    interfaceId = interfaceId,
                    completedHandlerInterfaceId = completedHandlerInterfaceId,
                    resultWriter = resultWriter,
                ),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncOperation",
        )
        adapter.selfReference = { host.createReference(interfaceId) }
        return WinRTAsyncOperationReference(
            comPtr = host.createReference(interfaceId).comPtr,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
            resultReader = { adapter.result() },
        )
    }

    private fun <T, TProgress> operationWithProgressReference(
        adapter: WinRTTaskToAsyncInfoAdapter<T>,
        resultSignature: WinRTTypeSignature,
        progressSignature: WinRTTypeSignature,
        progressValueKind: WinRTDelegateValueKind,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTAsyncOperationWithProgressReference<T, TProgress> {
        val interfaceId = WinRTAsyncOperationWithProgressReference.interfaceId(resultSignature, progressSignature)
        val progressHandlerInterfaceId =
            WinRTAsyncOperationWithProgressReference.progressHandlerInterfaceId(resultSignature, progressSignature)
        val completedHandlerInterfaceId =
            WinRTAsyncOperationWithProgressReference.completedHandlerInterfaceId(resultSignature, progressSignature)
        val host = createAsyncHost(
            adapter = adapter,
            defaultInterfaceId = interfaceId,
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncOperationWithProgressInterfaceDefinition(
                    interfaceId = interfaceId,
                    progressHandlerInterfaceId = progressHandlerInterfaceId,
                    completedHandlerInterfaceId = completedHandlerInterfaceId,
                    progressValueKind = progressValueKind,
                    resultWriter = resultWriter,
                ),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncOperationWithProgress",
        )
        adapter.selfReference = { host.createReference(interfaceId) }
        return WinRTAsyncOperationWithProgressReference(
            comPtr = host.createReference(interfaceId).comPtr,
            progressHandlerInterfaceId = progressHandlerInterfaceId,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
            resultReader = { adapter.result() },
        )
    }

    private fun createAsyncHost(
        adapter: WinRTTaskToAsyncInfoAdapter<*>,
        defaultInterfaceId: Guid,
        interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
        runtimeClassName: String,
    ): WinRTInspectableComObject {
        val definition = InteropRuntimeHooks.augmentInspectableDefinition(
            value = adapter,
            definition = WinRTCcwDefinition(
                interfaceDefinitions = interfaceDefinitions,
                defaultInterfaceId = defaultInterfaceId,
                runtimeClassName = runtimeClassName,
            ),
        )
        return WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = adapter,
        )
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal class WinRTTaskToAsyncInfoAdapter<T> private constructor(
    initialStatus: WinRTAsyncStatus,
    initialResult: T?,
    initialError: Throwable?,
) {
    private val state = AtomicInt(initialStatus.ordinal)
    private val terminalReached = AtomicInt(if (initialStatus == WinRTAsyncStatus.Started) 0 else 1)
    private val completedInvoked = AtomicInt(0)
    private val completedHandlerAssigned = AtomicInt(0)
    private val closed = AtomicInt(0)
    private var resultValue: T? = initialResult
    private var errorValue: Throwable? = initialError
    private var job: Job? = null
    private var completedHandler: ComObjectReference? = null
    private var progressHandler: ComObjectReference? = null
    internal var selfReference: (() -> ComObjectReference)? = null

    private val idValue: UInt = nextId()

    fun id(): UInt {
        ensureNotClosed()
        return idValue
    }

    fun status(): WinRTAsyncStatus {
        ensureNotClosed()
        return WinRTAsyncStatus.entries[state.load()]
    }

    fun errorCode(): HResult {
        ensureNotClosed()
        return errorValue?.let(ExceptionHelpers::hResultFromException) ?: KnownHResults.S_OK
    }

    fun cancel() {
        if (closed.load() != 0) {
            return
        }
        if (state.compareAndSet(WinRTAsyncStatus.Started.ordinal, WinRTAsyncStatus.Canceled.ordinal)) {
            job?.cancel()
        }
    }

    fun close() {
        if (closed.load() != 0) {
            return
        }
        if (!isTerminalForClose()) {
            throw WinRTIllegalStateException(
                "Cannot close a non-terminal WinRT async operation.",
                KnownHResults.E_ILLEGAL_STATE_CHANGE,
            )
        }
        if (!closed.compareAndSet(0, 1)) {
            return
        }
        completedHandler?.close()
        completedHandler = null
        progressHandler?.close()
        progressHandler = null
        job?.cancel()
    }

    fun setCompletedHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid) {
        ensureNotClosed()
        if (PlatformAbi.isNull(handlerPointer)) {
            if (completedHandlerAssigned.load() != 0) {
                throw WinRTIllegalStateException(
                    "Cannot set WinRT async completion handler more than once.",
                    KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
                )
            }
            return
        }

        val newHandler = cloneCompletedHandler(handlerPointer, handlerInterfaceId)
        if (!completedHandlerAssigned.compareAndSet(0, 1)) {
            newHandler.close()
            throw WinRTIllegalStateException(
                "Cannot set WinRT async completion handler more than once.",
                KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
            )
        }
        completedHandler = newHandler
        if (terminalReached.load() != 0) {
            invokeCompletedHandlerOnce(currentStatus())
        }
    }

    private fun cloneCompletedHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid): ComObjectReference {
        val borrowed = ComObjectReference(
            pointer = handlerPointer.asRawComPtr(),
            interfaceId = handlerInterfaceId,
            preventReleaseOnDispose = true,
        )
        try {
            borrowed.addRef()
        } finally {
            borrowed.close()
        }
        return ComObjectReference(handlerPointer.asRawComPtr(), handlerInterfaceId)
    }

    fun getCompletedHandler(resultOut: RawAddress) {
        ensureNotClosed()
        PlatformAbi.writePointer(
            resultOut,
            completedHandler?.getRefPointer()?.asRawAddress() ?: PlatformAbi.nullPointer,
        )
    }

    fun setProgressHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid) {
        ensureNotClosed()
        progressHandler?.close()
        progressHandler = null
        if (!PlatformAbi.isNull(handlerPointer)) {
            val borrowed = ComObjectReference(
                pointer = handlerPointer.asRawComPtr(),
                interfaceId = handlerInterfaceId,
                preventReleaseOnDispose = true,
            )
            try {
                borrowed.addRef()
            } finally {
                borrowed.close()
            }
            progressHandler = ComObjectReference(handlerPointer.asRawComPtr(), handlerInterfaceId)
        }
    }

    fun getProgressHandler(resultOut: RawAddress) {
        ensureNotClosed()
        PlatformAbi.writePointer(
            resultOut,
            progressHandler?.getRefPointer()?.asRawAddress() ?: PlatformAbi.nullPointer,
        )
    }

    fun reportProgress(value: Any?, progressValueKind: WinRTDelegateValueKind) {
        val handler = progressHandler ?: return
        if (closed.load() != 0 || !isRunningForProgress()) {
            return
        }
        val self = selfReference?.invoke()
        val lease = WinRTDelegateAbiMarshaller.encodeArgumentsLease(
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, progressValueKind),
            abiArguments = listOf(self?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer, value),
        )
        try {
            val hResult = ComVtableInvoker.invokeGeneric(
                instance = handler.pointer,
                slot = WinRTDelegateVftblSlots.Invoke,
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer, abiKindForDelegateValue(progressValueKind)),
                args = lease.values.map(::delegateAbiWord).toLongArray(),
            )
            WinRTPlatformApi.checkSucceededRaw(hResult)
        } finally {
            lease.close()
            self?.close()
        }
    }

    fun result(): T {
        ensureNotClosed()
        return when (currentStatus()) {
            WinRTAsyncStatus.Started ->
                throw WinRTIllegalStateException("Cannot get results from an incomplete async operation.", KnownHResults.E_ILLEGAL_METHOD_CALL)
            WinRTAsyncStatus.Canceled ->
                if (terminalReached.load() == 0) {
                    throw WinRTIllegalStateException(
                        "Cannot get results from an incomplete async operation.",
                        KnownHResults.E_ILLEGAL_METHOD_CALL,
                    )
                } else {
                    throw winRTAsyncCancellationException("operation")
                }
            WinRTAsyncStatus.Error ->
                throw errorValue ?: winRTAsyncErrorException(errorCode(), "operation")
            WinRTAsyncStatus.Completed ->
                @Suppress("UNCHECKED_CAST")
                resultValue as T
        }
    }

    private fun currentStatus(): WinRTAsyncStatus = WinRTAsyncStatus.entries[state.load()]

    private fun isTerminalForClose(): Boolean =
        when (currentStatus()) {
            WinRTAsyncStatus.Started -> false
            WinRTAsyncStatus.Canceled -> terminalReached.load() != 0
            WinRTAsyncStatus.Completed,
            WinRTAsyncStatus.Error,
            -> true
        }

    private fun isRunningForProgress(): Boolean =
        currentStatus() == WinRTAsyncStatus.Started ||
            (currentStatus() == WinRTAsyncStatus.Canceled && terminalReached.load() == 0)

    private fun ensureNotClosed() {
        if (closed.load() != 0) {
            throw WinRTIllegalStateException(
                "WinRT async operation is closed.",
                KnownHResults.E_ILLEGAL_METHOD_CALL,
            )
        }
    }

    fun attachJob(job: Job) {
        this.job = job
        job.invokeOnCompletion { cause ->
            when (cause) {
                null -> {
                    @Suppress("UNCHECKED_CAST")
                    complete(Unit as T)
                }
                is CancellationException -> completeCanceled()
                else -> completeError(cause)
            }
        }
    }

    fun createAsyncInfoInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = WinRTAsyncInterfaceIds.IAsyncInfo,
            methods = asyncInfoMethods(),
        )

    fun createAsyncActionInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = WinRTAsyncInterfaceIds.IAsyncAction,
            methods = asyncInfoMethods() + listOf(
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, WinRTAsyncInterfaceIds.AsyncActionCompletedHandler)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature()) {
                    result()
                    KnownHResults.S_OK.value
                },
            ),
        )

    fun createAsyncOperationInterfaceDefinition(
        interfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = asyncInfoMethods() + listOf(
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, completedHandlerInterfaceId)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    resultWriter.writeResult(result(), args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
            ),
        )

    fun createAsyncActionWithProgressInterfaceDefinition(
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        progressValueKind: WinRTDelegateValueKind,
    ): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = asyncInfoMethods() + progressMethods(progressHandlerInterfaceId) + listOf(
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, completedHandlerInterfaceId)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature()) {
                    result()
                    KnownHResults.S_OK.value
                },
            ),
        )

    fun createAsyncOperationWithProgressInterfaceDefinition(
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        progressValueKind: WinRTDelegateValueKind,
        resultWriter: WinRTAsyncResultWriter<T>,
    ): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = asyncInfoMethods() + progressMethods(progressHandlerInterfaceId) + listOf(
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, completedHandlerInterfaceId)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    resultWriter.writeResult(result(), args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun progressMethods(
        progressHandlerInterfaceId: Guid,
    ): List<WinRTInspectableMethodDefinition> =
        listOf(
            WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                setProgressHandler(args[0] as RawAddress, progressHandlerInterfaceId)
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                getProgressHandler(args[0] as RawAddress)
                KnownHResults.S_OK.value
            },
        )

    private fun asyncInfoMethods(): List<WinRTInspectableMethodDefinition> =
        listOf(
            WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                PlatformAbi.writeInt32(args[0] as RawAddress, id().toInt())
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                PlatformAbi.writeInt32(args[0] as RawAddress, status().abiValue)
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                PlatformAbi.writeInt32(args[0] as RawAddress, errorCode().value)
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(ComMethodSignature()) {
                cancel()
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(ComMethodSignature()) {
                close()
                KnownHResults.S_OK.value
            },
        )

    private fun complete(value: T) {
        if (terminalReached.compareAndSet(0, 1)) {
            resultValue = value
            errorValue = null
            state.store(WinRTAsyncStatus.Completed.ordinal)
            invokeCompletedHandlerOnce(WinRTAsyncStatus.Completed)
        }
    }

    private fun completeCanceled() {
        if (terminalReached.compareAndSet(0, 1)) {
            resultValue = null
            errorValue = null
            state.store(WinRTAsyncStatus.Canceled.ordinal)
            invokeCompletedHandlerOnce(WinRTAsyncStatus.Canceled)
        }
    }

    private fun completeError(error: Throwable) {
        if (terminalReached.compareAndSet(0, 1)) {
            resultValue = null
            errorValue = error
            state.store(WinRTAsyncStatus.Error.ordinal)
            invokeCompletedHandlerOnce(WinRTAsyncStatus.Error)
        }
    }

    private fun invokeCompletedHandlerOnce(completedStatus: WinRTAsyncStatus) {
        val handler = completedHandler ?: return
        if (!completedInvoked.compareAndSet(0, 1)) {
            return
        }
        val self = selfReference?.invoke()
        try {
            val asyncInfoPointer = self?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer
            val hResult = ComVtableInvoker.invokeGeneric(
                instance = handler.pointer,
                slot = WinRTDelegateVftblSlots.Invoke,
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Int32),
                args = longArrayOf(asyncInfoPointer.value, completedStatus.abiValue.toLong()),
            )
            WinRTPlatformApi.checkSucceededRaw(hResult)
        } finally {
            self?.close()
        }
    }

    companion object {
        private val idSource = AtomicInt(1)

        fun <T> started(): WinRTTaskToAsyncInfoAdapter<T> =
            WinRTTaskToAsyncInfoAdapter(WinRTAsyncStatus.Started, null, null)

        fun <T> completed(result: T): WinRTTaskToAsyncInfoAdapter<T> =
            WinRTTaskToAsyncInfoAdapter(WinRTAsyncStatus.Completed, result, null)

        fun <T> canceled(): WinRTTaskToAsyncInfoAdapter<T> =
            WinRTTaskToAsyncInfoAdapter(WinRTAsyncStatus.Canceled, null, null)

        fun <T> failed(error: Throwable): WinRTTaskToAsyncInfoAdapter<T> =
            WinRTTaskToAsyncInfoAdapter(WinRTAsyncStatus.Error, null, error)

        private fun nextId(): UInt {
            while (true) {
                val current = idSource.load()
                val next = if (current == Int.MAX_VALUE) 1 else current + 1
                if (idSource.compareAndSet(current, next)) {
                    return current.toUInt()
                }
            }
        }
    }
}

private fun abiKindForDelegateValue(kind: WinRTDelegateValueKind): ComAbiValueKind =
    when (kind) {
        WinRTDelegateValueKind.UNIT -> error("UNIT is not a valid progress ABI value kind.")
        WinRTDelegateValueKind.BOOLEAN -> ComAbiValueKind.Int8
        WinRTDelegateValueKind.OBJECT,
        WinRTDelegateValueKind.HSTRING,
        WinRTDelegateValueKind.IUNKNOWN,
        WinRTDelegateValueKind.IINSPECTABLE,
        WinRTDelegateValueKind.UINT8_ARRAY,
        -> ComAbiValueKind.Pointer
        WinRTDelegateValueKind.INT8,
        WinRTDelegateValueKind.UINT8,
        -> ComAbiValueKind.Int8
        WinRTDelegateValueKind.INT16,
        WinRTDelegateValueKind.UINT16,
        WinRTDelegateValueKind.CHAR16,
        -> ComAbiValueKind.Int16
        WinRTDelegateValueKind.INT32,
        WinRTDelegateValueKind.UINT32,
        -> ComAbiValueKind.Int32
        WinRTDelegateValueKind.INT64,
        WinRTDelegateValueKind.UINT64,
        -> ComAbiValueKind.Int64
        WinRTDelegateValueKind.FLOAT -> ComAbiValueKind.Float
        WinRTDelegateValueKind.DOUBLE -> ComAbiValueKind.Double
        WinRTDelegateValueKind.GUID -> ComAbiValueKind.Struct(NativeAbiLayout.GUID)
        WinRTDelegateValueKind.STRUCT -> error("STRUCT progress ABI value kind requires a typed adapter.")
    }

private fun delegateAbiWord(value: Any?): Long =
    when (value) {
        null -> 0L
        is RawAddress -> value.value
        is RawComPtr -> value.value
        is Byte -> value.toLong()
        is UByte -> value.toLong()
        is Short -> value.toLong()
        is UShort -> value.toLong()
        is Int -> value.toLong()
        is UInt -> value.toLong()
        is Char -> value.code.toLong()
        is Long -> value
        is ULong -> value.toLong()
        is Float -> value.toRawBits().toLong()
        is Double -> value.toRawBits()
        else -> error("Unsupported encoded delegate ABI word: ${value::class.qualifiedName}")
    }

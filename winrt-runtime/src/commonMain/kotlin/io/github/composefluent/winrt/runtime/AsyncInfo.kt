package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

fun interface WinRtAsyncResultWriter<T> {
    fun writeResult(value: T, resultOut: RawAddress)
}

class WinRtAsyncCancellation internal constructor(
    private val adapter: WinRtTaskToAsyncInfoAdapter<*>,
) {
    val isCancellationRequested: Boolean
        get() = adapter.status() == WinRtAsyncStatus.Canceled
}

class WinRtAsyncProgressReporter<T> internal constructor(
    private val adapter: WinRtTaskToAsyncInfoAdapter<*>,
    private val progressValueKind: WinRtDelegateValueKind,
) {
    fun report(value: T) {
        adapter.reportProgress(value, progressValueKind)
    }
}

object AsyncInfo {
    fun completedAction(): WinRtAsyncActionReference =
        actionReference(WinRtTaskToAsyncInfoAdapter.completed(Unit))

    fun canceledAction(): WinRtAsyncActionReference =
        actionReference(WinRtTaskToAsyncInfoAdapter.canceled())

    fun actionFromException(error: Throwable): WinRtAsyncActionReference =
        actionReference(WinRtTaskToAsyncInfoAdapter.failed(error))

    fun runAction(
        scope: CoroutineScope,
        block: suspend (WinRtAsyncCancellation) -> Unit,
    ): WinRtAsyncActionReference {
        val adapter = WinRtTaskToAsyncInfoAdapter.started<Unit>()
        adapter.attachJob(
            scope.launch {
                block(WinRtAsyncCancellation(adapter))
            },
        )
        return actionReference(adapter)
    }

    fun <TProgress> completedActionWithProgress(
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
    ): WinRtAsyncActionWithProgressReference<TProgress> =
        actionWithProgressReference(
            adapter = WinRtTaskToAsyncInfoAdapter.completed(Unit),
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
        )

    fun <TProgress> canceledActionWithProgress(
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
    ): WinRtAsyncActionWithProgressReference<TProgress> =
        actionWithProgressReference(
            adapter = WinRtTaskToAsyncInfoAdapter.canceled(),
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
        )

    fun <TProgress> actionWithProgressFromException(
        error: Throwable,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
    ): WinRtAsyncActionWithProgressReference<TProgress> =
        actionWithProgressReference(
            adapter = WinRtTaskToAsyncInfoAdapter.failed(error),
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
        )

    fun <TProgress> runActionWithProgress(
        scope: CoroutineScope,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
        block: suspend (WinRtAsyncCancellation, WinRtAsyncProgressReporter<TProgress>) -> Unit,
    ): WinRtAsyncActionWithProgressReference<TProgress> {
        val adapter = WinRtTaskToAsyncInfoAdapter.started<Unit>()
        adapter.attachJob(
            scope.launch {
                block(
                    WinRtAsyncCancellation(adapter),
                    WinRtAsyncProgressReporter(adapter, progressValueKind),
                )
            },
        )
        return actionWithProgressReference(adapter, progressSignature, progressValueKind)
    }

    fun <T> fromResult(
        result: T,
        resultSignature: WinRtTypeSignature,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationReference<T> =
        operationReference(
            adapter = WinRtTaskToAsyncInfoAdapter.completed(result),
            resultSignature = resultSignature,
            resultWriter = resultWriter,
        )

    fun <T> operationFromException(
        error: Throwable,
        resultSignature: WinRtTypeSignature,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationReference<T> =
        operationReference(
            adapter = WinRtTaskToAsyncInfoAdapter.failed(error),
            resultSignature = resultSignature,
            resultWriter = resultWriter,
        )

    fun <T> canceledOperation(
        resultSignature: WinRtTypeSignature,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationReference<T> =
        operationReference(
            adapter = WinRtTaskToAsyncInfoAdapter.canceled(),
            resultSignature = resultSignature,
            resultWriter = resultWriter,
        )

    fun <T, TProgress> fromResultWithProgress(
        result: T,
        resultSignature: WinRtTypeSignature,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationWithProgressReference<T, TProgress> =
        operationWithProgressReference(
            adapter = WinRtTaskToAsyncInfoAdapter.completed(result),
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
            resultWriter = resultWriter,
        )

    fun <T, TProgress> operationWithProgressFromException(
        error: Throwable,
        resultSignature: WinRtTypeSignature,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationWithProgressReference<T, TProgress> =
        operationWithProgressReference(
            adapter = WinRtTaskToAsyncInfoAdapter.failed(error),
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
            resultWriter = resultWriter,
        )

    fun <T, TProgress> canceledOperationWithProgress(
        resultSignature: WinRtTypeSignature,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationWithProgressReference<T, TProgress> =
        operationWithProgressReference(
            adapter = WinRtTaskToAsyncInfoAdapter.canceled(),
            resultSignature = resultSignature,
            progressSignature = progressSignature,
            progressValueKind = progressValueKind,
            resultWriter = resultWriter,
        )

    private fun actionReference(
        adapter: WinRtTaskToAsyncInfoAdapter<Unit>,
    ): WinRtAsyncActionReference {
        val host = createAsyncHost(
            adapter = adapter,
            defaultInterfaceId = WinRtAsyncInterfaceIds.IAsyncAction,
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncActionInterfaceDefinition(),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncAction",
        )
        adapter.selfReference = { host.createReference(WinRtAsyncInterfaceIds.IAsyncAction) }
        return WinRtAsyncActionReference(host.createReference(WinRtAsyncInterfaceIds.IAsyncAction).comPtr)
    }

    private fun <TProgress> actionWithProgressReference(
        adapter: WinRtTaskToAsyncInfoAdapter<Unit>,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
    ): WinRtAsyncActionWithProgressReference<TProgress> {
        val interfaceId = WinRtAsyncActionWithProgressReference.interfaceId(progressSignature)
        val progressHandlerInterfaceId = WinRtAsyncActionWithProgressReference.progressHandlerInterfaceId(progressSignature)
        val completedHandlerInterfaceId = WinRtAsyncActionWithProgressReference.completedHandlerInterfaceId(progressSignature)
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
        return WinRtAsyncActionWithProgressReference(
            comPtr = host.createReference(interfaceId).comPtr,
            progressHandlerInterfaceId = progressHandlerInterfaceId,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
        )
    }

    private fun <T> operationReference(
        adapter: WinRtTaskToAsyncInfoAdapter<T>,
        resultSignature: WinRtTypeSignature,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationReference<T> {
        val interfaceId = WinRtAsyncOperationReference.interfaceId(resultSignature)
        val completedHandlerInterfaceId = WinRtAsyncOperationReference.completedHandlerInterfaceId(resultSignature)
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
        return WinRtAsyncOperationReference(
            comPtr = host.createReference(interfaceId).comPtr,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
            resultReader = { adapter.result() },
        )
    }

    private fun <T, TProgress> operationWithProgressReference(
        adapter: WinRtTaskToAsyncInfoAdapter<T>,
        resultSignature: WinRtTypeSignature,
        progressSignature: WinRtTypeSignature,
        progressValueKind: WinRtDelegateValueKind,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationWithProgressReference<T, TProgress> {
        val interfaceId = WinRtAsyncOperationWithProgressReference.interfaceId(resultSignature, progressSignature)
        val progressHandlerInterfaceId =
            WinRtAsyncOperationWithProgressReference.progressHandlerInterfaceId(resultSignature, progressSignature)
        val completedHandlerInterfaceId =
            WinRtAsyncOperationWithProgressReference.completedHandlerInterfaceId(resultSignature, progressSignature)
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
        return WinRtAsyncOperationWithProgressReference(
            comPtr = host.createReference(interfaceId).comPtr,
            progressHandlerInterfaceId = progressHandlerInterfaceId,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
            resultReader = { adapter.result() },
        )
    }

    private fun createAsyncHost(
        adapter: WinRtTaskToAsyncInfoAdapter<*>,
        defaultInterfaceId: Guid,
        interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
        runtimeClassName: String,
    ): WinRtInspectableComObject {
        val definition = InteropRuntimeHooks.augmentInspectableDefinition(
            value = adapter,
            definition = WinRtCcwDefinition(
                interfaceDefinitions = interfaceDefinitions,
                defaultInterfaceId = defaultInterfaceId,
                runtimeClassName = runtimeClassName,
            ),
        )
        return WinRtInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = adapter,
        )
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal class WinRtTaskToAsyncInfoAdapter<T> private constructor(
    initialStatus: WinRtAsyncStatus,
    initialResult: T?,
    initialError: Throwable?,
) {
    private val state = AtomicInt(initialStatus.ordinal)
    private val completedInvoked = AtomicInt(0)
    private val completedHandlerAssigned = AtomicInt(0)
    private var resultValue: T? = initialResult
    private var errorValue: Throwable? = initialError
    private var job: Job? = null
    private var completedHandler: ComObjectReference? = null
    private var progressHandler: ComObjectReference? = null
    internal var selfReference: (() -> ComObjectReference)? = null

    private val idValue: UInt = nextId()

    fun id(): UInt = idValue

    fun status(): WinRtAsyncStatus = WinRtAsyncStatus.entries[state.load()]

    fun errorCode(): HResult =
        errorValue?.let(ExceptionHelpers::hResultFromException) ?: KnownHResults.S_OK

    fun cancel() {
        val current = status()
        if (current == WinRtAsyncStatus.Started) {
            state.store(WinRtAsyncStatus.Canceled.ordinal)
            job?.cancel()
            invokeCompletedHandlerOnce(WinRtAsyncStatus.Canceled)
        }
    }

    fun close() {
        completedHandler?.close()
        completedHandler = null
        progressHandler?.close()
        progressHandler = null
        job?.cancel()
    }

    fun setCompletedHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid) {
        if (PlatformAbi.isNull(handlerPointer)) {
            if (completedHandlerAssigned.load() != 0) {
                throw WinRtIllegalStateException(
                    "Cannot set WinRT async completion handler more than once.",
                    KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
                )
            }
            return
        }

        val newHandler = cloneCompletedHandler(handlerPointer, handlerInterfaceId)
        if (!completedHandlerAssigned.compareAndSet(0, 1)) {
            newHandler.close()
            throw WinRtIllegalStateException(
                "Cannot set WinRT async completion handler more than once.",
                KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
            )
        }
        completedHandler = newHandler
        status().takeIf { it != WinRtAsyncStatus.Started }?.let(::invokeCompletedHandlerOnce)
    }

    private fun cloneCompletedHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid): ComObjectReference {
        val borrowed = ComObjectReference(
            pointer = handlerPointer.asRawComPtr(),
            interfaceId = handlerInterfaceId,
            preventReleaseOnDispose = true,
        )
        borrowed.addRef()
        return ComObjectReference(handlerPointer.asRawComPtr(), handlerInterfaceId)
    }

    fun getCompletedHandler(resultOut: RawAddress) {
        PlatformAbi.writePointer(
            resultOut,
            completedHandler?.getRefPointer()?.asRawAddress() ?: PlatformAbi.nullPointer,
        )
    }

    fun setProgressHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid) {
        progressHandler?.close()
        progressHandler = null
        if (!PlatformAbi.isNull(handlerPointer)) {
            val borrowed = ComObjectReference(
                pointer = handlerPointer.asRawComPtr(),
                interfaceId = handlerInterfaceId,
                preventReleaseOnDispose = true,
            )
            borrowed.addRef()
            progressHandler = ComObjectReference(handlerPointer.asRawComPtr(), handlerInterfaceId)
        }
    }

    fun getProgressHandler(resultOut: RawAddress) {
        PlatformAbi.writePointer(
            resultOut,
            progressHandler?.getRefPointer()?.asRawAddress() ?: PlatformAbi.nullPointer,
        )
    }

    fun reportProgress(value: Any?, progressValueKind: WinRtDelegateValueKind) {
        val handler = progressHandler ?: return
        if (status() != WinRtAsyncStatus.Started) {
            return
        }
        val self = selfReference?.invoke()
        val lease = WinRtDelegateAbiMarshaller.encodeArgumentsLease(
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, progressValueKind),
            abiArguments = listOf(self?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer, value),
        )
        try {
            val hResult = ComVtableInvoker.invokeGeneric(
                instance = handler.pointer,
                slot = WinRtDelegateVftblSlots.Invoke,
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer, abiKindForDelegateValue(progressValueKind)),
                args = lease.values.map(::delegateAbiWord).toLongArray(),
            )
            WinRtPlatformApi.checkSucceededRaw(hResult)
        } finally {
            lease.close()
            self?.close()
        }
    }

    fun result(): T {
        return when (status()) {
            WinRtAsyncStatus.Started ->
                throw WinRtIllegalStateException("Cannot get results from an incomplete async operation.", KnownHResults.E_ILLEGAL_METHOD_CALL)
            WinRtAsyncStatus.Canceled ->
                throw WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED)
            WinRtAsyncStatus.Error ->
                throw errorValue ?: ExceptionHelpers.exceptionFor(errorCode(), "WinRT async operation")
            WinRtAsyncStatus.Completed ->
                @Suppress("UNCHECKED_CAST")
                resultValue as T
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

    fun createAsyncInfoInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = WinRtAsyncInterfaceIds.IAsyncInfo,
            methods = asyncInfoMethods(),
        )

    fun createAsyncActionInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = WinRtAsyncInterfaceIds.IAsyncAction,
            methods = asyncInfoMethods() + listOf(
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, WinRtAsyncInterfaceIds.AsyncActionCompletedHandler)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature()) {
                    result()
                    KnownHResults.S_OK.value
                },
            ),
        )

    fun createAsyncOperationInterfaceDefinition(
        interfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = asyncInfoMethods() + listOf(
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, completedHandlerInterfaceId)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    resultWriter.writeResult(result(), args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
            ),
        )

    fun createAsyncActionWithProgressInterfaceDefinition(
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        progressValueKind: WinRtDelegateValueKind,
    ): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = asyncInfoMethods() + progressMethods(progressHandlerInterfaceId) + listOf(
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, completedHandlerInterfaceId)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature()) {
                    result()
                    KnownHResults.S_OK.value
                },
            ),
        )

    fun createAsyncOperationWithProgressInterfaceDefinition(
        interfaceId: Guid,
        progressHandlerInterfaceId: Guid,
        completedHandlerInterfaceId: Guid,
        progressValueKind: WinRtDelegateValueKind,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = asyncInfoMethods() + progressMethods(progressHandlerInterfaceId) + listOf(
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    setCompletedHandler(args[0] as RawAddress, completedHandlerInterfaceId)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    getCompletedHandler(args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                    resultWriter.writeResult(result(), args[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun progressMethods(
        progressHandlerInterfaceId: Guid,
    ): List<WinRtInspectableMethodDefinition> =
        listOf(
            WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                setProgressHandler(args[0] as RawAddress, progressHandlerInterfaceId)
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                getProgressHandler(args[0] as RawAddress)
                KnownHResults.S_OK.value
            },
        )

    private fun asyncInfoMethods(): List<WinRtInspectableMethodDefinition> =
        listOf(
            WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                PlatformAbi.writeInt32(args[0] as RawAddress, id().toInt())
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                PlatformAbi.writeInt32(args[0] as RawAddress, status().abiValue)
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                PlatformAbi.writeInt32(args[0] as RawAddress, errorCode().value)
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(ComMethodSignature()) {
                cancel()
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(ComMethodSignature()) {
                close()
                KnownHResults.S_OK.value
            },
        )

    private fun complete(value: T) {
        if (state.compareAndSet(WinRtAsyncStatus.Started.ordinal, WinRtAsyncStatus.Completed.ordinal)) {
            resultValue = value
            errorValue = null
            invokeCompletedHandlerOnce(WinRtAsyncStatus.Completed)
        }
    }

    private fun completeCanceled() {
        if (state.compareAndSet(WinRtAsyncStatus.Started.ordinal, WinRtAsyncStatus.Canceled.ordinal)) {
            resultValue = null
            errorValue = null
            invokeCompletedHandlerOnce(WinRtAsyncStatus.Canceled)
        }
    }

    private fun completeError(error: Throwable) {
        if (state.compareAndSet(WinRtAsyncStatus.Started.ordinal, WinRtAsyncStatus.Error.ordinal)) {
            resultValue = null
            errorValue = error
            invokeCompletedHandlerOnce(WinRtAsyncStatus.Error)
        }
    }

    private fun invokeCompletedHandlerOnce(completedStatus: WinRtAsyncStatus) {
        val handler = completedHandler ?: return
        if (!completedInvoked.compareAndSet(0, 1)) {
            return
        }
        val self = selfReference?.invoke()
        try {
            val asyncInfoPointer = self?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer
            val hResult = ComVtableInvoker.invokeGeneric(
                instance = handler.pointer,
                slot = WinRtDelegateVftblSlots.Invoke,
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Int32),
                args = longArrayOf(asyncInfoPointer.value, completedStatus.abiValue.toLong()),
            )
            WinRtPlatformApi.checkSucceededRaw(hResult)
        } finally {
            self?.close()
        }
    }

    companion object {
        private val idSource = AtomicInt(1)

        fun <T> started(): WinRtTaskToAsyncInfoAdapter<T> =
            WinRtTaskToAsyncInfoAdapter(WinRtAsyncStatus.Started, null, null)

        fun <T> completed(result: T): WinRtTaskToAsyncInfoAdapter<T> =
            WinRtTaskToAsyncInfoAdapter(WinRtAsyncStatus.Completed, result, null)

        fun <T> canceled(): WinRtTaskToAsyncInfoAdapter<T> =
            WinRtTaskToAsyncInfoAdapter(WinRtAsyncStatus.Canceled, null, null)

        fun <T> failed(error: Throwable): WinRtTaskToAsyncInfoAdapter<T> =
            WinRtTaskToAsyncInfoAdapter(WinRtAsyncStatus.Error, null, error)

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

private fun abiKindForDelegateValue(kind: WinRtDelegateValueKind): ComAbiValueKind =
    when (kind) {
        WinRtDelegateValueKind.UNIT -> error("UNIT is not a valid progress ABI value kind.")
        WinRtDelegateValueKind.BOOLEAN -> ComAbiValueKind.Int8
        WinRtDelegateValueKind.OBJECT,
        WinRtDelegateValueKind.HSTRING,
        WinRtDelegateValueKind.IUNKNOWN,
        WinRtDelegateValueKind.IINSPECTABLE,
        -> ComAbiValueKind.Pointer
        WinRtDelegateValueKind.INT8,
        WinRtDelegateValueKind.UINT8,
        -> ComAbiValueKind.Int8
        WinRtDelegateValueKind.INT16,
        WinRtDelegateValueKind.UINT16,
        WinRtDelegateValueKind.CHAR16,
        -> ComAbiValueKind.Int16
        WinRtDelegateValueKind.INT32,
        WinRtDelegateValueKind.UINT32,
        -> ComAbiValueKind.Int32
        WinRtDelegateValueKind.INT64,
        WinRtDelegateValueKind.UINT64,
        -> ComAbiValueKind.Int64
        WinRtDelegateValueKind.FLOAT -> ComAbiValueKind.Float
        WinRtDelegateValueKind.DOUBLE -> ComAbiValueKind.Double
        WinRtDelegateValueKind.GUID -> ComAbiValueKind.Struct(NativeAbiLayout.GUID)
        WinRtDelegateValueKind.STRUCT -> error("STRUCT progress ABI value kind requires a typed adapter.")
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

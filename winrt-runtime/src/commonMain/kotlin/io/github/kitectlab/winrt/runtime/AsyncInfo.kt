package io.github.kitectlab.winrt.runtime

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

    private fun actionReference(
        adapter: WinRtTaskToAsyncInfoAdapter<Unit>,
    ): WinRtAsyncActionReference {
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncActionInterfaceDefinition(),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncAction",
            managedValue = adapter,
        )
        adapter.selfReference = { host.createReference(WinRtAsyncInterfaceIds.IAsyncAction) }
        return WinRtAsyncActionReference(host.createReference(WinRtAsyncInterfaceIds.IAsyncAction).comPtr)
    }

    private fun <T> operationReference(
        adapter: WinRtTaskToAsyncInfoAdapter<T>,
        resultSignature: WinRtTypeSignature,
        resultWriter: WinRtAsyncResultWriter<T>,
    ): WinRtAsyncOperationReference<T> {
        val interfaceId = WinRtAsyncOperationReference.interfaceId(resultSignature)
        val completedHandlerInterfaceId = WinRtAsyncOperationReference.completedHandlerInterfaceId(resultSignature)
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                adapter.createAsyncInfoInterfaceDefinition(),
                adapter.createAsyncOperationInterfaceDefinition(
                    interfaceId = interfaceId,
                    completedHandlerInterfaceId = completedHandlerInterfaceId,
                    resultWriter = resultWriter,
                ),
            ),
            runtimeClassName = "Windows.Foundation.IAsyncOperation",
            managedValue = adapter,
        )
        adapter.selfReference = { host.createReference(interfaceId) }
        return WinRtAsyncOperationReference(
            comPtr = host.createReference(interfaceId).comPtr,
            completedHandlerInterfaceId = completedHandlerInterfaceId,
            resultReader = { adapter.result() },
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
    private var resultValue: T? = initialResult
    private var errorValue: Throwable? = initialError
    private var job: Job? = null
    private var completedHandler: ComObjectReference? = null
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
        job?.cancel()
    }

    fun setCompletedHandler(handlerPointer: RawAddress, handlerInterfaceId: Guid) {
        completedHandler?.close()
        completedHandler = null
        if (!PlatformAbi.isNull(handlerPointer)) {
            val borrowed = ComObjectReference(
                pointer = handlerPointer.asRawComPtr(),
                interfaceId = handlerInterfaceId,
                preventReleaseOnDispose = true,
            )
            borrowed.addRef()
            completedHandler = ComObjectReference(handlerPointer.asRawComPtr(), handlerInterfaceId)
        }
        status().takeIf { it != WinRtAsyncStatus.Started }?.let(::invokeCompletedHandlerOnce)
    }

    fun getCompletedHandler(resultOut: RawAddress) {
        PlatformAbi.writePointer(
            resultOut,
            completedHandler?.getRefPointer()?.asRawAddress() ?: PlatformAbi.nullPointer,
        )
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

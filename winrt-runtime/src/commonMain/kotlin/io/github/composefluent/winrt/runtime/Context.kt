package io.github.composefluent.winrt.runtime

private object ContextCallbackVftbl {
    const val ContextCallback: Int = 3
}

private const val comCallDataSizeBytes = 16L
private const val comCallDataDispidOffset = 0L
private const val comCallDataReservedOffset = 4L
private const val comCallDataUserDefinedOffset = 8L

internal class ContextCallbackReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IContextCallback,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun contextCallback(
        callbackPointer: RawAddress,
        callDataPointer: RawAddress,
        interfaceId: Guid,
        methodIndex: Int,
    ) {
        PlatformAbi.confinedScope().use { scope ->
            val iidMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    ContextCallbackVftbl.ContextCallback,
                    callbackPointer,
                    callDataPointer,
                    iidMemory,
                    methodIndex,
                    PlatformAbi.nullPointer,
                ),
            ).requireSuccess("IContextCallback.ContextCallback")
        }
    }
}

internal object Context {
    private val callbackStates = ConcurrentCacheMap<Long, CallbackState>()
    private val callbackIdLock = PlatformLock()
    private var nextCallbackId = 1L
    private val contextCallbackStub: NativeCallbackHandle =
        ComAbiInteropBridge.createRawInt32Callback(
            parameterKinds = listOf(ComAbiValueKind.Pointer),
        ) { rawArguments ->
            val callDataPointer =
                rawArguments.singleOrNull() as? RawAddress
                    ?: return@createRawInt32Callback KnownHResults.E_POINTER.value
            contextCallbackBridge(callDataPointer)
        }

    fun getContextToken(): RawAddress {
        if (!PlatformRuntime.isWindows) {
            return PlatformAbi.nullPointer
        }
        val result = WinRtPlatformApi.coGetContextTokenRaw()
        HResult(result.hResultValue).requireSuccess("CoGetContextToken")
        return result.pointer
    }

    fun getContextCallback(): ContextCallbackReference? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        val result = WinRtPlatformApi.coGetObjectContextRaw(IID.IContextCallback)
        HResult(result.hResultValue).requireSuccess("CoGetObjectContext")
        return if (PlatformAbi.isNull(result.pointer)) {
            null
        } else {
            ContextCallbackReference(result.pointer, IID.IContextCallback)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callInContext(
        contextCallback: ContextCallbackReference?,
        contextToken: RawAddress,
        callback: (T) -> Unit,
        onFail: ((T) -> Unit)? = null,
        state: T,
    ) {
        if (contextCallback == null ||
            PlatformAbi.isNull(contextToken) ||
            PlatformAbi.pointerKey(getContextToken()) == PlatformAbi.pointerKey(contextToken)
        ) {
            callback(state)
            return
        }

        PlatformAbi.confinedScope().use { scope ->
            val callbackId = callbackIdLock.withLock { nextCallbackId++ }
            val userData = PlatformAbi.allocateInt64Slot(scope)
            PlatformAbi.writeInt64(userData, callbackId)
            val callData = PlatformAbi.allocateBytes(scope, comCallDataSizeBytes)
            PlatformAbi.writeInt32(callData, comCallDataDispidOffset, 0)
            PlatformAbi.writeInt32(callData, comCallDataReservedOffset, 0)
            PlatformAbi.writePointer(callData, comCallDataUserDefinedOffset, userData)
            callbackStates[callbackId] = CallbackState(
                callback = { value -> callback(value as T) },
                onFail = onFail?.let { handler -> { value -> handler(value as T) } },
                state = state,
            )
            try {
                runCatching {
                    contextCallback.contextCallback(
                        callbackPointer = contextCallbackStub.pointer,
                        callDataPointer = callData,
                        interfaceId = IID.ICallbackWithNoReentrancyToApplicationSTA,
                        methodIndex = 5,
                    )
                }.exceptionOrNull()?.let {
                    onFail?.invoke(state)
                }
            } finally {
                callbackStates.remove(callbackId)
            }
        }
    }

    fun disposeContextCallback(contextCallback: ContextCallbackReference?) {
        contextCallback?.close()
    }

    private fun contextCallbackBridge(callDataPointer: RawAddress): Int {
        val userData = PlatformAbi.readPointerAt(callDataPointer, 1)
        if (PlatformAbi.isNull(userData)) {
            return KnownHResults.E_POINTER.value
        }
        val callbackId = PlatformAbi.readInt64(userData)
        val state = callbackStates[callbackId] ?: return KnownHResults.RO_E_CLOSED.value
        return try {
            state.callback(state.state)
            KnownHResults.S_OK.value
        } catch (error: Throwable) {
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }
    }

    private data class CallbackState(
        val callback: (Any?) -> Unit,
        val onFail: ((Any?) -> Unit)?,
        val state: Any?,
    )
}

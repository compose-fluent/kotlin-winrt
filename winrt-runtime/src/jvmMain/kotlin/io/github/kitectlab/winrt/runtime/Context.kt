package io.github.kitectlab.winrt.runtime

private val contextCallbackDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
)

private const val comCallDataSizeBytes = 16L
private const val comCallDataDispidOffset = 0L
private const val comCallDataReservedOffset = 4L
private const val comCallDataUserDefinedOffset = 8L

internal class ContextCallbackReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IContextCallback,
) : IUnknownReference(pointer, interfaceId) {
    fun contextCallback(
        callbackPointer: NativePointer,
        callDataPointer: NativePointer,
        interfaceId: Guid,
        methodIndex: Int,
    ) {
        NativeInterop.confinedScope().use { scope ->
            val iidMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = contextCallbackDescriptor,
                    callbackPointer,
                    callDataPointer,
                    iidMemory,
                    methodIndex,
                    NativeInterop.nullPointer,
                ),
                operation = "IContextCallback.ContextCallback",
            )
        }
    }
}

internal object Context {
    private val callbackStates = ConcurrentCacheMap<Long, CallbackState>()
    private val callbackIdLock = PlatformLock()
    private var nextCallbackId = 1L
    private val contextCallbackStub: NativeCallbackHandle =
        NativeInterop.createCallback(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
        ) { rawArguments ->
            val callDataPointer = rawArguments.singleOrNull() as? NativePointer ?: return@createCallback KnownHResults.E_POINTER.value
            contextCallbackBridge(callDataPointer)
        }

    fun getContextToken(): NativePointer {
        if (!PlatformRuntime.isWindows) {
            return NativeInterop.nullPointer
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
        return if (NativeInterop.isNull(result.pointer)) {
            null
        } else {
            ContextCallbackReference(result.pointer, IID.IContextCallback)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callInContext(
        contextCallback: ContextCallbackReference?,
        contextToken: NativePointer,
        callback: (T) -> Unit,
        onFail: ((T) -> Unit)? = null,
        state: T,
    ) {
        if (contextCallback == null ||
            NativeInterop.isNull(contextToken) ||
            NativeInterop.pointerKey(getContextToken()) == NativeInterop.pointerKey(contextToken)
        ) {
            callback(state)
            return
        }

        NativeInterop.confinedScope().use { scope ->
            val callbackId = callbackIdLock.withLock { nextCallbackId++ }
            val userData = NativeInterop.allocateInt64Slot(scope)
            NativeInterop.writeInt64(userData, callbackId)
            val callData = NativeInterop.allocateBytes(scope, comCallDataSizeBytes)
            NativeInterop.writeInt32(callData, comCallDataDispidOffset, 0)
            NativeInterop.writeInt32(callData, comCallDataReservedOffset, 0)
            NativeInterop.writePointer(callData, comCallDataUserDefinedOffset, userData)
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

    private fun contextCallbackBridge(callDataPointer: NativePointer): Int {
        val userData = NativeInterop.readPointerAt(callDataPointer, 1)
        if (NativeInterop.isNull(userData)) {
            return KnownHResults.E_POINTER.value
        }
        val callbackId = NativeInterop.readInt64(userData)
        val state = callbackStates[callbackId] ?: return KnownHResults.RO_E_CLOSED.value
        return try {
            state.callback(state.state)
            KnownHResults.S_OK.value
        } catch (error: Throwable) {
            ExceptionHelpers.setErrorInfo(error)
            WinRtExceptionTranslator.hResultFromException(error).value
        }
    }

    private data class CallbackState(
        val callback: (Any?) -> Unit,
        val onFail: ((Any?) -> Unit)?,
        val state: Any?,
    )
}

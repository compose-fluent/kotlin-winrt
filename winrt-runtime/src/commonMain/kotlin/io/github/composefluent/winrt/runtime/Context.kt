package io.github.composefluent.winrt.runtime

private object ContextCallbackVftbl {
    const val ContextCallback: Int = 3
}

private const val comCallDataSizeBytes = 16L
private const val comCallDataDispidOffset = 0L
private const val comCallDataReservedOffset = 4L
private const val comCallDataUserDefinedOffset = 8L

internal class ContextCallbackReference(
    comPtr: ComPtr,
) : IUnknownReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = IID.IContextCallback,
    ) : this(
        ComPtr.create(
            raw = pointer.asRawComPtr(),
            interfaceId = interfaceId,
            trackContext = false,
        ),
    )

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
        val result = WinRTPlatformApi.coGetContextTokenRaw()
        HResult(result.hResultValue).requireSuccess("CoGetContextToken")
        return result.pointer
    }

    fun getContextCallback(): ContextCallbackReference? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        val result = WinRTPlatformApi.coGetObjectContextRaw(IID.IContextCallback)
        HResult(result.hResultValue).requireSuccess("CoGetObjectContext")
        return if (PlatformAbi.isNull(result.pointer)) {
            null
        } else {
            ContextCallbackReference(result.pointer, IID.IContextCallback)
        }
    }

    fun tryCapture(): CapturedContext? {
        if (!PlatformRuntime.isWindows) {
            return null
        }

        val tokenResult = WinRTPlatformApi.coGetContextTokenRaw()
        if (tokenResult.hResultValue == KnownHResults.CO_E_NOTINITIALIZED.value) {
            return null
        }
        HResult(tokenResult.hResultValue).requireSuccess("CoGetContextToken")

        val callbackResult = WinRTPlatformApi.coGetObjectContextRaw(IID.IContextCallback)
        if (callbackResult.hResultValue == KnownHResults.CO_E_NOTINITIALIZED.value) {
            return null
        }
        HResult(callbackResult.hResultValue).requireSuccess("CoGetObjectContext")
        if (PlatformAbi.isNull(callbackResult.pointer)) {
            return null
        }

        return CapturedContext(
            callback = ContextCallbackReference(callbackResult.pointer, IID.IContextCallback),
            token = tokenResult.pointer,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callInContext(
        contextCallback: ContextCallbackReference?,
        contextToken: RawAddress,
        callback: (T) -> Unit,
        onFail: ((T) -> Unit)? = null,
        state: T,
    ) {
        if (contextCallback == null || PlatformAbi.isNull(contextToken)) {
            callback(state)
            return
        }

        val currentTokenResult = WinRTPlatformApi.coGetContextTokenRaw()
        if (currentTokenResult.hResultValue == KnownHResults.CO_E_NOTINITIALIZED.value) {
            RuntimeScope.initializeMultithreaded().use {
                callInContext(contextCallback, contextToken, callback, onFail, state)
            }
            return
        }
        HResult(currentTokenResult.hResultValue).requireSuccess("CoGetContextToken")
        if (PlatformAbi.pointerKey(currentTokenResult.pointer) == PlatformAbi.pointerKey(contextToken)) {
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

internal data class CapturedContext(
    val callback: ContextCallbackReference,
    val token: RawAddress,
)

internal class ObjectReferenceContext private constructor(
    private val callback: ContextCallbackReference,
    private val token: RawAddress,
    private val originalPointer: RawComPtr,
    private val interfaceIdLowBits: Long,
    private val interfaceIdHighBits: Long,
    private val knownInterfaceId: Guid?,
    private val callsAreFreeThreaded: Boolean,
) : AutoCloseable {
    private val currentContextReferences = ConcurrentCacheMap<Long, IUnknownReference>()
    private val agileReferenceLock = PlatformLock()
    private var agileReferenceInitialized = false
    private var agileReference: AgileReference? = null

    fun pointerForCurrentContext(): RawComPtr {
        if (callsAreFreeThreaded) {
            return originalPointer
        }
        val currentToken = Context.getContextToken()
        if (PlatformAbi.pointerKey(currentToken) == PlatformAbi.pointerKey(token)) {
            return originalPointer
        }

        val contextKey = PlatformAbi.pointerKey(currentToken)
        currentContextReferences[contextKey]?.let { return it.pointer }
        val resolved = getAgileReference()?.getReference(interfaceId()) ?: return originalPointer
        val existing = currentContextReferences.putIfAbsent(contextKey, resolved)
        if (existing != null) {
            resolved.close()
            return existing.pointer
        }
        return resolved.pointer
    }

    fun callInOriginalContext(
        callbackAction: () -> Unit,
        fallbackAction: () -> Unit = callbackAction,
    ) {
        val actions = callbackAction to fallbackAction
        Context.callInContext(
            contextCallback = callback,
            contextToken = token,
            callback = { state: Pair<() -> Unit, () -> Unit> -> state.first() },
            onFail = { state: Pair<() -> Unit, () -> Unit> -> state.second() },
            state = actions,
        )
    }

    fun deferToOriginalContext(action: () -> Unit) {
        DeferredContextActions.enqueue(PlatformAbi.pointerKey(token), action)
    }

    override fun close() {
        val cachedReferences = currentContextReferences.values.toList()
        currentContextReferences.clear()
        cachedReferences.forEach(IUnknownReference::close)
        agileReferenceLock.withLock {
            agileReference?.close()
            agileReference = null
        }
        Context.disposeContextCallback(callback)
    }

    private fun getAgileReference(): AgileReference? =
        agileReferenceLock.withLock {
            if (!agileReferenceInitialized) {
                Context.callInContext(
                    contextCallback = callback,
                    contextToken = token,
                    callback = { state: ObjectReferenceContext ->
                        state.agileReference = AgileReference(state.originalPointer.asRawAddress())
                    },
                    state = this,
                )
                agileReferenceInitialized = true
            }
            agileReference
        }

    private fun interfaceId(): Guid =
        knownInterfaceId ?: Guid.fromAbiWords(interfaceIdLowBits, interfaceIdHighBits)

    companion object {
        fun capture(
            pointer: RawComPtr,
            interfaceIdLowBits: Long,
            interfaceIdHighBits: Long,
            knownInterfaceId: Guid?,
        ): ObjectReferenceContext? {
            if (ComThreadingSupport.isFreeThreaded(pointer)) {
                return null
            }
            return capture(
                pointer = pointer,
                interfaceIdLowBits = interfaceIdLowBits,
                interfaceIdHighBits = interfaceIdHighBits,
                knownInterfaceId = knownInterfaceId,
                callsAreFreeThreaded = false,
            )
        }

        /**
         * CsWinRT delegates agile tracker lifetime to CLR ComWrappers. Kotlin keeps calls agile but
         * returns final release to the creating apartment because it has no CLR tracker manager.
         */
        fun captureForReferenceTrackerRelease(
            pointer: RawComPtr,
            interfaceIdLowBits: Long,
            interfaceIdHighBits: Long,
            knownInterfaceId: Guid?,
        ): ObjectReferenceContext? = capture(
            pointer = pointer,
            interfaceIdLowBits = interfaceIdLowBits,
            interfaceIdHighBits = interfaceIdHighBits,
            knownInterfaceId = knownInterfaceId,
            callsAreFreeThreaded = true,
        )

        private fun capture(
            pointer: RawComPtr,
            interfaceIdLowBits: Long,
            interfaceIdHighBits: Long,
            knownInterfaceId: Guid?,
            callsAreFreeThreaded: Boolean,
        ): ObjectReferenceContext? {
            val captured = Context.tryCapture() ?: return null
            return ObjectReferenceContext(
                callback = captured.callback,
                token = captured.token,
                originalPointer = pointer,
                interfaceIdLowBits = interfaceIdLowBits,
                interfaceIdHighBits = interfaceIdHighBits,
                knownInterfaceId = knownInterfaceId,
                callsAreFreeThreaded = callsAreFreeThreaded,
            )
        }
    }
}

private object DeferredContextActions {
    private val lock = PlatformLock()
    private val actionsByContext = mutableMapOf<Long, MutableList<() -> Unit>>()

    fun enqueue(contextKey: Long, action: () -> Unit) {
        lock.withLock {
            actionsByContext.getOrPut(contextKey, ::mutableListOf).add(action)
        }
    }

    fun drainCurrentContext() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        val result = WinRTPlatformApi.coGetContextTokenRaw()
        if (result.hResultValue < 0 || PlatformAbi.isNull(result.pointer)) {
            return
        }
        val actions = lock.withLock {
            actionsByContext.remove(PlatformAbi.pointerKey(result.pointer))?.toList().orEmpty()
        }
        actions.forEach { action -> action() }
    }
}

internal fun drainDeferredComReleasesForCurrentContext() {
    DeferredContextActions.drainCurrentContext()
}

private object ComThreadingSupport {
    private val inProcFreeThreadedMarshaler = guidOf("0000033A-0000-0000-C000-000000000046")

    fun isFreeThreaded(pointer: RawComPtr): Boolean {
        if (!PlatformRuntime.isWindows || !hasQueryInterface(pointer)) {
            return true
        }

        val agileResult = WinRTPlatformApi.queryInterfaceRaw(pointer.asRawAddress(), IID.IAgileObject)
        if (agileResult.hResultValue >= 0 && !PlatformAbi.isNull(agileResult.pointer)) {
            WinRTPlatformApi.releaseRaw(agileResult.pointer)
            return true
        }

        val marshalResult = WinRTPlatformApi.queryInterfaceRaw(pointer.asRawAddress(), IID.IMarshal)
        if (marshalResult.hResultValue < 0 || PlatformAbi.isNull(marshalResult.pointer)) {
            return false
        }

        return try {
            PlatformAbi.confinedScope().use { scope ->
                val iidMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
                IID.IUnknown.writeTo(iidMemory)
                val unmarshalClassOut = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        marshalResult.pointer.asRawComPtr(),
                        MarshalInterfaceVftbl.GetUnmarshalClass,
                        iidMemory,
                        PlatformAbi.nullPointer,
                        WinRTMarshalingContext.InProc,
                        PlatformAbi.nullPointer,
                        WinRTMarshalingFlags.Normal,
                        unmarshalClassOut,
                    ),
                ).requireSuccess("IMarshal.GetUnmarshalClass")
                PlatformAbi.readGuid(unmarshalClassOut) == inProcFreeThreadedMarshaler
            }
        } finally {
            WinRTPlatformApi.releaseRaw(marshalResult.pointer)
        }
    }

    private fun hasQueryInterface(pointer: RawComPtr): Boolean {
        val vtable = PlatformAbi.readPointerAt(pointer.asRawAddress(), 0)
        return !PlatformAbi.isNull(vtable) &&
            !PlatformAbi.isNull(PlatformAbi.readPointerAt(vtable, IUnknownVftblSlots.QueryInterface))
    }
}

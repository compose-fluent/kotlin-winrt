package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class ContextCallbackReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IContextCallback,
) : IUnknownReference(pointer, interfaceId) {
    fun contextCallback(
        callbackPointer: MemorySegment,
        callDataPointer: MemorySegment,
        interfaceId: Guid,
        methodIndex: Int,
    ) {
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(AbiLayouts.GUID)
            interfaceId.writeTo(iidMemory)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                    ),
                    callbackPointer,
                    callDataPointer,
                    iidMemory,
                    methodIndex,
                    MemorySegment.NULL,
                ),
                operation = "IContextCallback.ContextCallback",
            )
        }
    }
}

internal object Context {
    private val linker: Linker = Linker.nativeLinker()
    private val lookup = MethodHandles.lookup()
    private val sharedArena = Arena.global()
    private val callbackStates = ConcurrentHashMap<Long, CallbackState>()
    private val nextCallbackId = AtomicLong(1)
    private val contextCallbackStub: MemorySegment = linker.upcallStub(
        lookup.findStatic(
            Context::class.java,
            "contextCallbackBridge",
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                MemorySegment::class.java,
            ),
        ),
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ),
        sharedArena,
    )

    fun getContextToken(): MemorySegment {
        if (!PlatformRuntime.isWindows) {
            return MemorySegment.NULL
        }
        val result = WindowsRuntimePlatform.coGetContextToken()
        result.hResult.requireSuccess("CoGetContextToken")
        return result.pointer
    }

    fun getContextCallback(): ContextCallbackReference? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        val result = WindowsRuntimePlatform.coGetObjectContext(IID.IContextCallback)
        result.hResult.requireSuccess("CoGetObjectContext")
        return if (result.pointer == MemorySegment.NULL) {
            null
        } else {
            ContextCallbackReference(result.pointer, IID.IContextCallback)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callInContext(
        contextCallback: ContextCallbackReference?,
        contextToken: MemorySegment,
        callback: (T) -> Unit,
        onFail: ((T) -> Unit)? = null,
        state: T,
    ) {
        if (contextCallback == null || contextToken == MemorySegment.NULL || getContextToken().address() == contextToken.address()) {
            callback(state)
            return
        }

        Arena.ofConfined().use { arena ->
            val callbackId = nextCallbackId.getAndIncrement()
            val userData = arena.allocate(ValueLayout.JAVA_LONG)
            userData.set(ValueLayout.JAVA_LONG, 0, callbackId)
            val callData = arena.allocate(COM_CALL_DATA)
            callData.set(ValueLayout.JAVA_INT, COM_CALL_DATA_DISPID_OFFSET, 0)
            callData.set(ValueLayout.JAVA_INT, COM_CALL_DATA_RESERVED_OFFSET, 0)
            callData.set(ValueLayout.ADDRESS, COM_CALL_DATA_USER_DEFINED_OFFSET, userData)
            callbackStates[callbackId] = CallbackState(
                callback = { value -> callback(value as T) },
                onFail = onFail?.let { handler -> { value -> handler(value as T) } },
                state = state,
            )
            try {
                runCatching {
                    contextCallback.contextCallback(
                        callbackPointer = contextCallbackStub,
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

    @JvmStatic
    private fun contextCallbackBridge(callDataPointer: MemorySegment): Int {
        val userData = callDataPointer.get(ValueLayout.ADDRESS, COM_CALL_DATA_USER_DEFINED_OFFSET)
        if (userData == MemorySegment.NULL) {
            return KnownHResults.E_POINTER.value
        }
        val callbackId = userData.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0)
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

    private val COM_CALL_DATA: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("dwDispid"),
        ValueLayout.JAVA_INT.withName("dwReserved"),
        ValueLayout.ADDRESS.withName("pUserDefined"),
    )
    private const val COM_CALL_DATA_DISPID_OFFSET = 0L
    private const val COM_CALL_DATA_RESERVED_OFFSET = 4L
    private const val COM_CALL_DATA_USER_DEFINED_OFFSET = 8L
}

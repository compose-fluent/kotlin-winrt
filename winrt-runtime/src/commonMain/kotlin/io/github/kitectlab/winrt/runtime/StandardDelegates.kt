package io.github.kitectlab.winrt.runtime

private val addEventHandlerDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

private val removeEventHandlerDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_LONG,
)

/**
 * Common standard-delegate ABI helpers corresponding to the event add/remove delegates
 * in `.cswinrt/src/WinRT.Runtime/Interop/StandardDelegates.cs`.
 */
internal object StandardDelegates {
    fun addEventHandler(
        objectReference: ComObjectReference,
        addHandlerSlot: Int,
        handler: ComObjectReference,
    ): EventRegistrationToken =
        NativeInterop.confinedScope().use { scope ->
            val tokenOut = NativeInterop.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            HResult(
                objectReference.invokeAbi(
                    slot = addHandlerSlot,
                    descriptor = addEventHandlerDescriptor,
                    handler.pointer,
                    tokenOut,
                ),
            ).requireSuccess("WinRT event add handler")
            EventRegistrationToken.fromAbi(tokenOut)
        }

    fun removeEventHandler(
        objectReference: ComObjectReference,
        removeHandlerSlot: Int,
        token: EventRegistrationToken,
    ) {
        HResult(
            objectReference.invokeAbi(
                slot = removeHandlerSlot,
                descriptor = removeEventHandlerDescriptor,
                token.value,
            ),
        ).requireSuccess("WinRT event remove handler")
    }
}

package io.github.kitectlab.winrt.runtime

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
        PlatformAbi.confinedScope().use { scope ->
            val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = objectReference.pointer,
                    slot = addHandlerSlot,
                    arg0 = handler.pointer.asRawAddress(),
                    arg1 = tokenOut,
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
            ComVtableInvoker.invokeArgs(
                instance = objectReference.pointer,
                slot = removeHandlerSlot,
                arg0 = token.value,
            ),
        ).requireSuccess("WinRT event remove handler")
    }
}

package io.github.composefluent.winrt.runtime

import windows.foundation.EventRegistrationToken

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
        acquireNativeScalarScratchFrame().use { tokenOut ->
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = objectReference.pointer,
                    slot = addHandlerSlot,
                    arg0 = handler.pointer.asRawAddress(),
                    arg1 = tokenOut.pointer,
                ),
            ).requireSuccess("WinRT event add handler")
            EventRegistrationToken.fromAbiValue(tokenOut.readInt64())
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

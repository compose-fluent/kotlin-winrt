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
    ): EventRegistrationToken = try {
        addEventHandler(objectReference, addHandlerSlot, handler.pointer.asRawAddress())
    } finally {
        winRTKeepAlive(handler)
    }

    /** The caller owns the handler ABI reference for the duration of this call. */
    fun addEventHandler(
        objectReference: ComObjectReference,
        addHandlerSlot: Int,
        handler: RawAddress,
    ): EventRegistrationToken = try {
        acquireNativeScalarScratchFrame().use { tokenOut ->
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = objectReference.pointer,
                    slot = addHandlerSlot,
                    arg0 = handler,
                    arg1 = tokenOut.pointer,
                ),
            ).requireSuccess("WinRT event add handler")
            EventRegistrationToken.fromAbiValue(tokenOut.readInt64())
        }
    } finally {
        winRTKeepAlive(objectReference)
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

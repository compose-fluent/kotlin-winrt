package io.github.composefluent.winrt.runtime

internal class WinRTAsyncInfoView(
    private val comPtr: ComPtr,
) {
    fun id(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeArgs(WinRTAsyncInfoVftblSlots.Id, resultOut)
        }

    fun status(): WinRTAsyncStatus =
        RawAbiResultSupport.int32Result { resultOut ->
            invokeArgs(WinRTAsyncInfoVftblSlots.Status, resultOut)
        }.let(WinRTAsyncStatus::fromAbi)

    fun errorCode(): HResult =
        HResult(
            RawAbiResultSupport.int32Result { resultOut ->
                invokeArgs(WinRTAsyncInfoVftblSlots.ErrorCode, resultOut)
            },
        )

    fun cancel() {
        val hr = invoke(WinRTAsyncInfoVftblSlots.Cancel)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun closeAsyncInfo() {
        val hr = invoke(WinRTAsyncInfoVftblSlots.Close)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    private fun invoke(slot: Int): Int {
        return comPtr.withCallPointer { activePointer -> ComVtableInvoker.invoke(activePointer, slot) }
    }

    private fun invokeArgs(
        slot: Int,
        arg0: RawAddress,
    ): Int {
        return comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, slot, arg0) }
    }
}

internal class WinRTAsyncActionView(
    private val comPtr: ComPtr,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        val hr = handler.comPtr.withCallPointer { handlerPointer -> comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncActionVftblSlots.PutCompleted, handlerPointer) } }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncActionVftblSlots.GetCompleted, resultOut) }
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), WinRTAsyncInterfaceIds.AsyncActionCompletedHandler) },
        )

    fun getResults() {
        val hr = comPtr.withCallPointer { activePointer -> ComVtableInvoker.invoke(activePointer, WinRTAsyncActionVftblSlots.GetResults) }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRTAsyncOperationView(
    private val comPtr: ComPtr,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        val hr = handler.comPtr.withCallPointer { handlerPointer -> comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncOperationVftblSlots.PutCompleted, handlerPointer) } }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncOperationVftblSlots.GetCompleted, resultOut) }
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )
}

internal class WinRTAsyncActionWithProgressView(
    private val comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setProgressHandler(handler: ComObjectReference) {
        val hr = handler.comPtr.withCallPointer { handlerPointer -> comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncActionWithProgressVftblSlots.PutProgress, handlerPointer) } }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncActionWithProgressVftblSlots.GetProgress, resultOut) }
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        val hr = handler.comPtr.withCallPointer { handlerPointer -> comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncActionWithProgressVftblSlots.PutCompleted, handlerPointer) } }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncActionWithProgressVftblSlots.GetCompleted, resultOut) }
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )

    fun getResults() {
        val hr = comPtr.withCallPointer { activePointer -> ComVtableInvoker.invoke(activePointer, WinRTAsyncActionWithProgressVftblSlots.GetResults) }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRTAsyncOperationWithProgressView(
    private val comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setProgressHandler(handler: ComObjectReference) {
        val hr = handler.comPtr.withCallPointer { handlerPointer -> comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncOperationWithProgressVftblSlots.PutProgress, handlerPointer) } }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncOperationWithProgressVftblSlots.GetProgress, resultOut) }
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        val hr = handler.comPtr.withCallPointer { handlerPointer -> comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncOperationWithProgressVftblSlots.PutCompleted, handlerPointer) } }
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(activePointer, WinRTAsyncOperationWithProgressVftblSlots.GetCompleted, resultOut) }
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )
}

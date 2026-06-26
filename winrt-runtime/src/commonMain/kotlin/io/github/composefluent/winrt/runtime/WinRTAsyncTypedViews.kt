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
        comPtr.throwIfDisposed()
        return ComVtableInvoker.invoke(comPtr.raw, slot)
    }

    private fun invokeArgs(
        slot: Int,
        arg0: RawAddress,
    ): Int {
        comPtr.throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0)
    }
}

internal class WinRTAsyncActionView(
    private val comPtr: ComPtr,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncActionVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncActionVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), WinRTAsyncInterfaceIds.AsyncActionCompletedHandler) },
        )

    fun getResults() {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invoke(comPtr.raw, WinRTAsyncActionVftblSlots.GetResults)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRTAsyncOperationView(
    private val comPtr: ComPtr,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncOperationVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncOperationVftblSlots.GetCompleted, resultOut)
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
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncActionWithProgressVftblSlots.PutProgress, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncActionWithProgressVftblSlots.GetProgress, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncActionWithProgressVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncActionWithProgressVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )

    fun getResults() {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invoke(comPtr.raw, WinRTAsyncActionWithProgressVftblSlots.GetResults)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRTAsyncOperationWithProgressView(
    private val comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setProgressHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncOperationWithProgressVftblSlots.PutProgress, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncOperationWithProgressVftblSlots.GetProgress, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncOperationWithProgressVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRTAsyncOperationWithProgressVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )
}

package io.github.kitectlab.winrt.runtime

internal class WinRtAsyncInfoView(
    private val comPtr: ComPtr,
) {
    fun id(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeArgs(WinRtAsyncInfoVftblSlots.Id, resultOut)
        }

    fun status(): WinRtAsyncStatus =
        RawAbiResultSupport.int32Result { resultOut ->
            invokeArgs(WinRtAsyncInfoVftblSlots.Status, resultOut)
        }.let(WinRtAsyncStatus::fromAbi)

    fun errorCode(): HResult =
        HResult(
            RawAbiResultSupport.int32Result { resultOut ->
                invokeArgs(WinRtAsyncInfoVftblSlots.ErrorCode, resultOut)
            },
        )

    fun cancel() {
        val hr = invoke(WinRtAsyncInfoVftblSlots.Cancel)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun closeAsyncInfo() {
        val hr = invoke(WinRtAsyncInfoVftblSlots.Close)
        WinRtPlatformApi.checkSucceededRaw(hr)
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

internal class WinRtAsyncActionView(
    private val comPtr: ComPtr,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncActionVftblSlots.PutCompleted, handler.pointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncActionVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), WinRtAsyncInterfaceIds.AsyncActionCompletedHandler) },
        )

    fun getResults() {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invoke(comPtr.raw, WinRtAsyncActionVftblSlots.GetResults)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRtAsyncOperationView(
    private val comPtr: ComPtr,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncOperationVftblSlots.PutCompleted, handler.pointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncOperationVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )
}

internal class WinRtAsyncActionWithProgressView(
    private val comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setProgressHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncActionWithProgressVftblSlots.PutProgress, handler.pointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncActionWithProgressVftblSlots.GetProgress, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncActionWithProgressVftblSlots.PutCompleted, handler.pointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncActionWithProgressVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )

    fun getResults() {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invoke(comPtr.raw, WinRtAsyncActionWithProgressVftblSlots.GetResults)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRtAsyncOperationWithProgressView(
    private val comPtr: ComPtr,
    private val progressHandlerInterfaceId: Guid,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setProgressHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncOperationWithProgressVftblSlots.PutProgress, handler.pointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncOperationWithProgressVftblSlots.GetProgress, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncOperationWithProgressVftblSlots.PutCompleted, handler.pointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, WinRtAsyncOperationWithProgressVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )
}

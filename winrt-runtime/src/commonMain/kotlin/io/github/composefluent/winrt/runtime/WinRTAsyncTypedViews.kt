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
        return ComVtableInvoker.invoke(comPtr.resolvePointer(), slot)
    }

    private fun invokeArgs(
        slot: Int,
        arg0: RawAddress,
    ): Int {
        comPtr.throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), slot, arg0)
    }
}

internal class WinRTAsyncActionView(
    private val comPtr: ComPtr,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncActionVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncActionVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), WinRTAsyncInterfaceIds.AsyncActionCompletedHandler) },
        )

    fun getResults() {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invoke(comPtr.resolvePointer(), WinRTAsyncActionVftblSlots.GetResults)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}

internal class WinRTAsyncOperationView(
    private val comPtr: ComPtr,
    private val completedHandlerInterfaceId: Guid,
) {
    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncOperationVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncOperationVftblSlots.GetCompleted, resultOut)
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
        val hr = ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncActionWithProgressVftblSlots.PutProgress, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncActionWithProgressVftblSlots.GetProgress, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncActionWithProgressVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncActionWithProgressVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )

    fun getResults() {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invoke(comPtr.resolvePointer(), WinRTAsyncActionWithProgressVftblSlots.GetResults)
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
        val hr = ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncOperationWithProgressVftblSlots.PutProgress, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getProgressHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncOperationWithProgressVftblSlots.GetProgress, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), progressHandlerInterfaceId) },
        )

    fun setCompletedHandler(handler: ComObjectReference) {
        comPtr.throwIfDisposed()
        val hr = ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncOperationWithProgressVftblSlots.PutCompleted, handler.pointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    fun getCompletedHandler(): ComObjectReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.resolvePointer(), WinRTAsyncOperationWithProgressVftblSlots.GetCompleted, resultOut)
            },
            wrap = { pointer -> ComObjectReference(pointer.asRawComPtr(), completedHandlerInterfaceId) },
        )
}

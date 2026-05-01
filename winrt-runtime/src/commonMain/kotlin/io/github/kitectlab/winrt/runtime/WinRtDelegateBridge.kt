package io.github.kitectlab.winrt.runtime

object WinRtDelegateBridge {
    fun createUnitDelegate(
        iid: Guid,
        parameterKinds: List<WinRtDelegateValueKind>,
        callback: (List<Any?>) -> Unit,
    ): WinRtDelegateHandle =
        createDelegate(
            iid = iid,
            parameterKinds = parameterKinds,
            returnKind = WinRtDelegateValueKind.UNIT,
        ) { arguments ->
            callback(arguments)
            Unit
        }

    fun createDelegate(
        iid: Guid,
        parameterKinds: List<WinRtDelegateValueKind>,
        returnKind: WinRtDelegateValueKind,
        parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
        returnStructAdapter: NativeStructAdapter<*>? = null,
        runtimeClassName: String? = null,
        callback: (List<Any?>) -> Any?,
    ): WinRtDelegateHandle {
        val descriptor = WinRtDelegateDescriptor(
            interfaceId = iid,
            parameterKinds = parameterKinds,
            returnKind = returnKind,
            parameterStructAdapters = parameterStructAdapters,
            returnStructAdapter = returnStructAdapter,
            runtimeClassName = runtimeClassName,
        )
        val comObject = WinRtDelegateComObject(
            descriptor = descriptor,
            callback = callback,
        )
        return WinRtDelegateHandle(
            descriptor = descriptor,
            callback = callback,
            comObject = comObject,
            releaseAction = comObject::releaseManagedReference,
        )
    }
}

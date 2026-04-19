package io.github.kitectlab.winrt.runtime

object WinRtDelegateBridge {
    fun createUnitDelegate(
        iid: Guid,
        parameterKinds: List<WinRtDelegateValueKind>,
        callback: (List<Any?>) -> Unit,
    ): WinRtDelegateHandle {
        val descriptor = WinRtDelegateDescriptor(
            interfaceId = iid,
            parameterKinds = parameterKinds,
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

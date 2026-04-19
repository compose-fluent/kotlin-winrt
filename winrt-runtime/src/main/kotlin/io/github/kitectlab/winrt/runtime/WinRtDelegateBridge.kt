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
        return WinRtDelegateHandle(
            descriptor = descriptor,
            callback = callback,
        )
    }
}

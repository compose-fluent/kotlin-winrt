package io.github.composefluent.winrt.runtime

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

    fun createDelegateArgument(
        iid: Guid,
        parameterKinds: List<WinRtDelegateValueKind>,
        returnKind: WinRtDelegateValueKind,
        parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
        returnStructAdapter: NativeStructAdapter<*>? = null,
        runtimeClassName: String? = null,
        delegate: Any? = null,
        callback: ((List<Any?>) -> Any?)?,
    ): WinRtDelegateArgumentMarshaler {
        if (delegate is WinRtProjectedDelegate) {
            return createProjectedDelegateArgument(delegate)
        }
        val handle = callback?.let {
            createDelegate(
                iid = iid,
                parameterKinds = parameterKinds,
                returnKind = returnKind,
                parameterStructAdapters = parameterStructAdapters,
                returnStructAdapter = returnStructAdapter,
                runtimeClassName = runtimeClassName,
                callback = it,
            )
        }
        return WinRtDelegateArgumentMarshaler(handle = handle, reference = handle?.createReference())
    }

    fun createProjectedDelegateHandle(delegate: WinRtProjectedDelegate): WinRtDelegateHandle =
        delegate.createWinRtDelegateHandle()

    private fun createProjectedDelegateArgument(delegate: WinRtProjectedDelegate): WinRtDelegateArgumentMarshaler {
        ComWrappersSupport.tryUnwrapObject(delegate)?.let { reference ->
            return WinRtDelegateArgumentMarshaler(handle = null, reference = reference)
        }
        return WinRtDelegateArgumentMarshaler(
            handle = null,
            reference = ProjectedDelegateCcwCache.createReference(delegate),
        )
    }
}

class WinRtDelegateArgumentMarshaler internal constructor(
    private val handle: WinRtDelegateHandle?,
    private val reference: ComObjectReference?,
) : AutoCloseable {
    val abi: RawAddress =
        reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer

    override fun close() {
        reference?.close()
        handle?.close()
    }
}

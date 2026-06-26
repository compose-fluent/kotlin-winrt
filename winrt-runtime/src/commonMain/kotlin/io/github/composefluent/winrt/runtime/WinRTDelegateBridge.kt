package io.github.composefluent.winrt.runtime

object WinRTDelegateBridge {
    fun createUnitDelegate(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        callback: (List<Any?>) -> Unit,
    ): WinRTDelegateHandle =
        createDelegate(
            iid = iid,
            parameterKinds = parameterKinds,
            returnKind = WinRTDelegateValueKind.UNIT,
        ) { arguments ->
            callback(arguments)
            Unit
        }

    fun createDelegate(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        returnKind: WinRTDelegateValueKind,
        parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
        returnStructAdapter: NativeStructAdapter<*>? = null,
        runtimeClassName: String? = null,
        callback: (List<Any?>) -> Any?,
    ): WinRTDelegateHandle {
        val descriptor = WinRTDelegateDescriptor(
            interfaceId = iid,
            parameterKinds = parameterKinds,
            returnKind = returnKind,
            parameterStructAdapters = parameterStructAdapters,
            returnStructAdapter = returnStructAdapter,
            runtimeClassName = runtimeClassName,
        )
        val comObject = WinRTDelegateComObject(
            descriptor = descriptor,
            callback = callback,
        )
        return WinRTDelegateHandle(
            descriptor = descriptor,
            callback = callback,
            comObject = comObject,
            releaseAction = comObject::releaseManagedReference,
        )
    }

    fun createDelegateArgument(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        returnKind: WinRTDelegateValueKind,
        parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
        returnStructAdapter: NativeStructAdapter<*>? = null,
        runtimeClassName: String? = null,
        delegate: Any? = null,
        callback: ((List<Any?>) -> Any?)?,
    ): WinRTDelegateArgumentMarshaler {
        if (delegate is WinRTProjectedDelegate) {
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
        return WinRTDelegateArgumentMarshaler(handle = handle, reference = handle?.createReference())
    }

    fun createProjectedDelegateHandle(delegate: WinRTProjectedDelegate): WinRTDelegateHandle =
        delegate.createWinRTDelegateHandle()

    private fun createProjectedDelegateArgument(delegate: WinRTProjectedDelegate): WinRTDelegateArgumentMarshaler {
        ComWrappersSupport.tryUnwrapObject(delegate)?.let { reference ->
            return WinRTDelegateArgumentMarshaler(handle = null, reference = reference)
        }
        return WinRTDelegateArgumentMarshaler(
            handle = null,
            reference = ProjectedDelegateCcwCache.createReference(delegate),
        )
    }
}

class WinRTDelegateArgumentMarshaler internal constructor(
    private val handle: WinRTDelegateHandle?,
    private val reference: ComObjectReference?,
) : AutoCloseable {
    val abi: RawAddress =
        reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer

    override fun close() {
        reference?.close()
        handle?.close()
    }
}

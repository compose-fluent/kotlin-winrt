package io.github.composefluent.winrt.runtime

data class WinRTDelegateDescriptor(
    val interfaceId: Guid,
    val parameterKinds: List<WinRTDelegateValueKind>,
    val returnKind: WinRTDelegateValueKind = WinRTDelegateValueKind.UNIT,
    val parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
    val returnStructAdapter: NativeStructAdapter<*>? = null,
    val runtimeClassName: String? = null,
) {
    init {
        require(returnKind.isSupportedDelegateReturnKind()) {
            "Unsupported delegate return kind: $returnKind."
        }
        require(parameterStructAdapters.isEmpty() || parameterStructAdapters.size == parameterKinds.size) {
            "Delegate struct adapter count ${parameterStructAdapters.size} must match parameter kind count ${parameterKinds.size}."
        }
        parameterKinds.forEachIndexed { index, kind ->
            if (kind == WinRTDelegateValueKind.STRUCT) {
                require(parameterStructAdapter(index) != null) {
                    "Delegate STRUCT parameter at index $index requires a NativeStructAdapter."
                }
            }
        }
        if (returnKind == WinRTDelegateValueKind.STRUCT) {
            require(returnStructAdapter != null) {
                "Delegate STRUCT return requires a NativeStructAdapter."
            }
        }
    }

    fun parameterStructAdapter(index: Int): NativeStructAdapter<*>? =
        parameterStructAdapters.getOrNull(index)

    fun typedEventHandlerSignature(
        genericDelegateIid: Guid,
        vararg argumentSignatures: WinRTTypeSignature,
    ): WinRTTypeSignature {
        require(argumentSignatures.size == parameterKinds.size) {
            "Signature count ${argumentSignatures.size} must match parameter kind count ${parameterKinds.size}."
        }
        return WinRTTypeSignature.parameterizedInterface(genericDelegateIid, *argumentSignatures)
    }

    fun referenceInterfaceId(): Guid =
        ParameterizedInterfaceId.createFromParameterizedInterface(
            IID.IReference,
            WinRTTypeSignature.delegate(interfaceId),
        )
}

private fun WinRTDelegateValueKind.isSupportedDelegateReturnKind(): Boolean =
    when (this) {
        WinRTDelegateValueKind.UNIT,
        WinRTDelegateValueKind.BOOLEAN,
        WinRTDelegateValueKind.INT8,
        WinRTDelegateValueKind.UINT8,
        WinRTDelegateValueKind.INT16,
        WinRTDelegateValueKind.UINT16,
        WinRTDelegateValueKind.INT32,
        WinRTDelegateValueKind.UINT32,
        WinRTDelegateValueKind.INT64,
        WinRTDelegateValueKind.UINT64,
        WinRTDelegateValueKind.FLOAT,
        WinRTDelegateValueKind.DOUBLE,
        WinRTDelegateValueKind.CHAR16,
        WinRTDelegateValueKind.GUID,
        WinRTDelegateValueKind.STRUCT,
        WinRTDelegateValueKind.HSTRING,
        WinRTDelegateValueKind.OBJECT,
        WinRTDelegateValueKind.IUNKNOWN,
        WinRTDelegateValueKind.IINSPECTABLE,
        -> true
        WinRTDelegateValueKind.UINT8_ARRAY -> false
    }

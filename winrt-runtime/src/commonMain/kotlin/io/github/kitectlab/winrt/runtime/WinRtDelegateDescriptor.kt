package io.github.kitectlab.winrt.runtime

data class WinRtDelegateDescriptor(
    val interfaceId: Guid,
    val parameterKinds: List<WinRtDelegateValueKind>,
    val returnKind: WinRtDelegateValueKind = WinRtDelegateValueKind.UNIT,
) {
    init {
        require(returnKind.isSupportedDelegateReturnKind()) {
            "Unsupported delegate return kind: $returnKind."
        }
    }

    fun typedEventHandlerSignature(
        genericDelegateIid: Guid,
        vararg argumentSignatures: WinRtTypeSignature,
    ): WinRtTypeSignature {
        require(argumentSignatures.size == parameterKinds.size) {
            "Signature count ${argumentSignatures.size} must match parameter kind count ${parameterKinds.size}."
        }
        return WinRtTypeSignature.parameterizedInterface(genericDelegateIid, *argumentSignatures)
    }
}

private fun WinRtDelegateValueKind.isSupportedDelegateReturnKind(): Boolean =
    when (this) {
        WinRtDelegateValueKind.UNIT,
        WinRtDelegateValueKind.BOOLEAN,
        WinRtDelegateValueKind.INT8,
        WinRtDelegateValueKind.UINT8,
        WinRtDelegateValueKind.INT16,
        WinRtDelegateValueKind.UINT16,
        WinRtDelegateValueKind.INT32,
        WinRtDelegateValueKind.UINT32,
        WinRtDelegateValueKind.INT64,
        WinRtDelegateValueKind.UINT64,
        WinRtDelegateValueKind.FLOAT,
        WinRtDelegateValueKind.DOUBLE,
        WinRtDelegateValueKind.CHAR16,
        WinRtDelegateValueKind.HSTRING,
        WinRtDelegateValueKind.OBJECT,
        WinRtDelegateValueKind.IUNKNOWN,
        WinRtDelegateValueKind.IINSPECTABLE,
        -> true
    }

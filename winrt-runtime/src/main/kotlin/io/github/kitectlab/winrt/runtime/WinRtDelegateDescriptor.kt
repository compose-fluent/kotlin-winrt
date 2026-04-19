package io.github.kitectlab.winrt.runtime

data class WinRtDelegateDescriptor(
    val interfaceId: Guid,
    val parameterKinds: List<WinRtDelegateValueKind>,
) {
    init {
        require(parameterKinds.isNotEmpty()) {
            "Delegate descriptors must declare at least one parameter kind."
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

package io.github.composefluent.winrt.projections.support

import io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic

// Deterministic generator handoff for .cswinrt WinRTGenericAbiRegistry writer parity.

internal data class GenericAbiDelegateEntry(
    val name: String,
    val sourceGenericType: String,
    val operation: String,
    val declaration: String,
    val abiParameterTypes: List<String>,
    val typeArrayShape: List<String>,
)

internal object WinRTGenericAbiRegistry {
    fun delegateNamed(name: String): GenericAbiDelegateEntry? =
        WinRtGenericAbiSupportIntrinsic.delegateNamed(name) as? GenericAbiDelegateEntry

    @Suppress("UNCHECKED_CAST")
    fun delegatesForSourceType(sourceGenericType: String): List<GenericAbiDelegateEntry> =
        WinRtGenericAbiSupportIntrinsic.delegatesForSourceType(sourceGenericType) as List<GenericAbiDelegateEntry>

    fun isDerivedGenericInterface(typeName: String): Boolean =
        WinRtGenericAbiSupportIntrinsic.isDerivedGenericInterface(typeName)

    fun registerAbiDelegates(register: (typeArrayShape: List<String>, delegateName: String) -> Unit) {
        WinRtGenericAbiSupportIntrinsic.registerAbiDelegates(register)
    }
}

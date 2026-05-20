package io.github.composefluent.winrt.runtime

object WinRtGenericAbiSupportIntrinsic {
    fun delegateNamed(name: String): Any? =
        error("WinRtGenericAbiSupportIntrinsic.delegateNamed was not lowered by the kotlin-winrt compiler plugin: $name")

    fun delegatesForSourceType(sourceGenericType: String): List<Any> =
        error(
            "WinRtGenericAbiSupportIntrinsic.delegatesForSourceType was not lowered by the kotlin-winrt compiler plugin: " +
                sourceGenericType,
        )

    fun isDerivedGenericInterface(typeName: String): Boolean =
        error(
            "WinRtGenericAbiSupportIntrinsic.isDerivedGenericInterface was not lowered by the kotlin-winrt compiler plugin: " +
                typeName,
        )

    fun registerAbiDelegates(register: (List<String>, String) -> Unit): Unit =
        error("WinRtGenericAbiSupportIntrinsic.registerAbiDelegates was not lowered by the kotlin-winrt compiler plugin.")
}

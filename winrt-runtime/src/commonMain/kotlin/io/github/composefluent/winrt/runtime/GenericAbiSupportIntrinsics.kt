package io.github.composefluent.winrt.runtime

object WinRTGenericAbiSupportIntrinsic {
    fun delegateNamed(name: String): Any? =
        error("WinRTGenericAbiSupportIntrinsic.delegateNamed was not lowered by the kotlin-winrt compiler plugin: $name")

    fun delegatesForSourceType(sourceGenericType: String): List<Any> =
        error(
            "WinRTGenericAbiSupportIntrinsic.delegatesForSourceType was not lowered by the kotlin-winrt compiler plugin: " +
                sourceGenericType,
        )

    fun isDerivedGenericInterface(typeName: String): Boolean =
        error(
            "WinRTGenericAbiSupportIntrinsic.isDerivedGenericInterface was not lowered by the kotlin-winrt compiler plugin: " +
                typeName,
        )

    fun registerAbiDelegates(register: (List<String>, String) -> Unit): Unit =
        error("WinRTGenericAbiSupportIntrinsic.registerAbiDelegates was not lowered by the kotlin-winrt compiler plugin.")
}

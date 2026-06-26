package io.github.composefluent.winrt.runtime

object WinRTGenericTypeInstantiationSupportIntrinsic {
    fun initializeAll(): Unit =
        error("WinRTGenericTypeInstantiationSupportIntrinsic.initializeAll was not lowered by the kotlin-winrt compiler plugin.")

    fun initializeBySourceType(sourceType: String): Unit =
        error(
            "WinRTGenericTypeInstantiationSupportIntrinsic.initializeBySourceType was not lowered by the kotlin-winrt compiler plugin: " +
                sourceType,
        )
}

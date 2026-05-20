package io.github.composefluent.winrt.runtime

object WinRtGenericTypeInstantiationSupportIntrinsic {
    fun initializeAll(): Unit =
        error("WinRtGenericTypeInstantiationSupportIntrinsic.initializeAll was not lowered by the kotlin-winrt compiler plugin.")

    fun initializeBySourceType(sourceType: String): Unit =
        error(
            "WinRtGenericTypeInstantiationSupportIntrinsic.initializeBySourceType was not lowered by the kotlin-winrt compiler plugin: " +
                sourceType,
        )
}

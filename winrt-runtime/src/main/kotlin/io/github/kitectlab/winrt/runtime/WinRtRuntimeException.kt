package io.github.kitectlab.winrt.runtime

class WinRtRuntimeException(
    message: String,
    val hResult: HResult? = null,
) : RuntimeException(message)

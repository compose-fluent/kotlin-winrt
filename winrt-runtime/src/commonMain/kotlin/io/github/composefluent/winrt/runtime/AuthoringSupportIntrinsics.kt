package io.github.composefluent.winrt.runtime

object WinRTAuthoringSupportIntrinsic {
    fun ensureInitialized(): Nothing = error(
        "WinRTAuthoringSupportIntrinsic.ensureInitialized was not lowered. " +
            "Apply the kotlin-winrt compiler plugin to this compilation.",
    )
}

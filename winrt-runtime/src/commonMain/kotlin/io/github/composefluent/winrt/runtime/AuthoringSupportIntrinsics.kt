package io.github.composefluent.winrt.runtime

object WinRtAuthoringSupportIntrinsic {
    fun ensureInitialized(): Nothing = error(
        "WinRtAuthoringSupportIntrinsic.ensureInitialized was not lowered. " +
            "Apply the kotlin-winrt compiler plugin to this compilation.",
    )
}

package io.github.composefluent.winrt.runtime

object WinRtXamlProjectionSupportIntrinsic {
    fun prepareForApplicationExit() {
        XamlSystemProjectionRuntimeHooks.prepareForApplicationExit()
    }
}

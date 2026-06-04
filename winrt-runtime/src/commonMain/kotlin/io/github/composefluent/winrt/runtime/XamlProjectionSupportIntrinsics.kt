package io.github.composefluent.winrt.runtime

object WinRtXamlProjectionSupportIntrinsic {
    fun runWithApplicationStart(block: () -> Unit) {
        XamlSystemProjectionRuntimeHooks.runWithApplicationStart(block)
    }

    fun prepareForApplicationExit() {
        XamlSystemProjectionRuntimeHooks.prepareForApplicationExit()
    }

    fun completeApplicationExit() {
        XamlSystemProjectionRuntimeHooks.completeApplicationExit()
    }
}

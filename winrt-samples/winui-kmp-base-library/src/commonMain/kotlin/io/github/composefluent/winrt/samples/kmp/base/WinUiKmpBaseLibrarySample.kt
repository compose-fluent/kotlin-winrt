package io.github.composefluent.winrt.samples.kmp.base

import io.github.composefluent.winrt.runtime.WinRTUri
import windows.system.Launcher

object WinUiKmpBaseLibrarySample {
    @Suppress("unused")
    fun launcherProjectionCompileSmoke() {
        Launcher.launchUriAsync(WinRTUri("https://example.invalid/"))
    }
}

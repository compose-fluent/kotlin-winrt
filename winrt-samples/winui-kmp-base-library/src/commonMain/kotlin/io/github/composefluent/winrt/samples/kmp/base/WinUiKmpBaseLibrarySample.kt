package io.github.composefluent.winrt.samples.kmp.base

import io.github.composefluent.winrt.runtime.WinRtUri
import windows.system.Launcher

object WinUiKmpBaseLibrarySample {
    @Suppress("unused")
    fun launcherProjectionCompileSmoke() {
        Launcher.launchUriAsync(WinRtUri("https://example.invalid/"))
    }
}

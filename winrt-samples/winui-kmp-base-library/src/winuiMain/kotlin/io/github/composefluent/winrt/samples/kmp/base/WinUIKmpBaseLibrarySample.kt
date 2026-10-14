package io.github.composefluent.winrt.samples.kmp.base

import windows.foundation.Uri
import windows.system.Launcher

object WinUIKmpBaseLibrarySample {
    @Suppress("unused")
    fun launcherProjectionCompileSmoke() {
        Launcher.launchUriAsync(Uri("https://example.invalid/"))
    }
}

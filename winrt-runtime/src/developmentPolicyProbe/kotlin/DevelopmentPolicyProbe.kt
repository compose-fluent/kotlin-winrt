@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class, kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime.policy

import io.github.composefluent.winrt.runtime.*

/** Executes the same linked runtime in DEBUG and RELEASE; no WinUI installation is needed. */
fun main() {
    val debug = kotlin.native.Platform.isDebugBinary
    check(isWinUIXamlDevelopmentBuild() == debug)
    check(isNativeWinUIXamlPreviewRequested() == debug)
    check(isWinUIXamlPreviewHost() == debug)
    check(winUIXamlPreviewStartupTheme() == if (debug) "Dark" else "Default")
    var invoked = false
    configureWinUIXamlHotReload({ invoked = true; { it(); true } }, { _, value -> value })
    check(isWinUIXamlHotReloadEnabled() == debug)
    check(!invoked) // Configuration never touches the application dispatcher.
    println("Native development policy: " + if (debug) "DEBUG allowed" else "RELEASE denied")
}

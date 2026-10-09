package io.github.composefluent.winrt.runtime

/** The common property engine is portable. The IDE development transport is currently JVM-only;
 * Winsock/session-file parity remains an explicit item in winrt-compiler-plugin/ide/README.md. */
internal actual fun platformStartWinRTXamlHotReload(registry: WinRTXamlHotReloadRegistry): AutoCloseable? {
    check(platformGetWindowsEnvironmentVariable(WinRTXamlHotReloadProtocol.SESSION_DIRECTORY).isNullOrBlank()) {
        "XAML Hot Reload development sessions currently require Kotlin/JVM; the mingwX64 transport is not implemented."
    }
    return null
}

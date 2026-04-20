package io.github.kitectlab.winrt.runtime

expect object HostPlatformInfo {
    val osName: String
    val isWindows: Boolean
    val isLinux: Boolean
    val isMacOs: Boolean
}

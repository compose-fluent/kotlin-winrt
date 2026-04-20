package io.github.kitectlab.winrt.runtime

object PlatformRuntime {
    val osName: String
        get() = HostPlatformInfo.osName

    val isWindows: Boolean
        get() = HostPlatformInfo.isWindows

    val isLinux: Boolean
        get() = HostPlatformInfo.isLinux

    val isMacOs: Boolean
        get() = HostPlatformInfo.isMacOs
}

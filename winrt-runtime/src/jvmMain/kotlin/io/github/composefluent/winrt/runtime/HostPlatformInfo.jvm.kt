package io.github.composefluent.winrt.runtime

actual object HostPlatformInfo {
    private val normalizedOsName = System.getProperty("os.name").orEmpty().lowercase()

    actual val osName: String
        get() = System.getProperty("os.name").orEmpty()

    actual val isWindows: Boolean
        get() = normalizedOsName.contains("win")

    actual val isLinux: Boolean
        get() = normalizedOsName.contains("linux")

    actual val isMacOs: Boolean
        get() = normalizedOsName.contains("mac")
}

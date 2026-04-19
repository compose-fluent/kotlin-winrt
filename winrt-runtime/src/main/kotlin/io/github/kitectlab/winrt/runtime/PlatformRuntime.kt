package io.github.kitectlab.winrt.runtime

object PlatformRuntime {
    private val normalizedOsName = System.getProperty("os.name").orEmpty().lowercase()

    val osName: String
        get() = System.getProperty("os.name").orEmpty()

    val isWindows: Boolean
        get() = normalizedOsName.contains("win")

    val isLinux: Boolean
        get() = normalizedOsName.contains("linux")

    val isMacOs: Boolean
        get() = normalizedOsName.contains("mac")
}

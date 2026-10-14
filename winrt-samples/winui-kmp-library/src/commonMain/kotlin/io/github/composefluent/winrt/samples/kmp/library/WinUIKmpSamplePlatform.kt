package io.github.composefluent.winrt.samples.kmp.library

internal expect object WinUIKmpSamplePlatform {
    fun option(name: String): Boolean

    fun scheduleTimerTimeout(action: () -> Unit)
}

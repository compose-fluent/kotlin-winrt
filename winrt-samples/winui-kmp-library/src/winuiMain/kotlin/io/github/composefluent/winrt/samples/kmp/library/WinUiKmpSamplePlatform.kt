package io.github.composefluent.winrt.samples.kmp.library

internal expect object WinUiKmpSamplePlatform {
    fun option(name: String): Boolean

    fun scheduleTimerTimeout(action: () -> Unit)
}

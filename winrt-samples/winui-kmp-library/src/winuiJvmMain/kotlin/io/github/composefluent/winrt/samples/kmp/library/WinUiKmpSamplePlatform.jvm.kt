package io.github.composefluent.winrt.samples.kmp.library

internal actual object WinUiKmpSamplePlatform {
    actual fun option(name: String): Boolean =
        java.lang.Boolean.getBoolean(name)

    actual fun scheduleTimerTimeout(action: () -> Unit) {
        Thread {
            Thread.sleep(1_500)
            action()
        }.also { thread ->
            thread.name = "kotlin-winrt timer smoke timeout"
            thread.isDaemon = true
            thread.start()
        }
    }
}

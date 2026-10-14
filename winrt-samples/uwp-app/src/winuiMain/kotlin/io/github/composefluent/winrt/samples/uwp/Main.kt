package io.github.composefluent.winrt.samples.uwp

import windows.applicationmodel.Package
import windows.storage.ApplicationData
import windows.ui.xaml.Application

private var application: UWPApplication? = null

fun main() {
    try {
        sampleLog("Starting ${checkNotNull(checkNotNull(Package.current).id).familyName}")
        Application.start {
            try {
                application = UWPApplication()
            } catch (error: Throwable) {
                reportStartupFailure(error)
                throw error
            }
        }
    } catch (error: Throwable) {
        reportStartupFailure(error)
        throw error
    }
}

private fun reportStartupFailure(error: Throwable) {
    // Storage projection initialization can itself fail during early startup.
    // Keep the original exception available to the application host diagnostics.
    runCatching { sampleLog(error.stackTraceToString()) }
        .exceptionOrNull()?.let(error::addSuppressed)
}

internal fun sampleLog(message: String) {
    println("Kotlin UWP: $message")
    appendSampleLog(checkNotNull(checkNotNull(ApplicationData.current).localFolder).path + "/uwp-sample.log", message)
}

internal expect fun appendSampleLog(path: String, message: String)

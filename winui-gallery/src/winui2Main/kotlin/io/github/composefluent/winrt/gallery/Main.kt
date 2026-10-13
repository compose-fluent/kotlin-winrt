package io.github.composefluent.winrt.gallery

import windows.storage.ApplicationData
import windows.ui.xaml.Application

private var application: GalleryApplication? = null
internal expect val galleryTargetName: String
internal expect fun appendGalleryLog(path: String, message: String)

fun main(args: Array<String> = emptyArray()) {
    galleryLog("Starting WinUI 2 Gallery ($galleryTargetName)")
    try {
        Application.start { application = GalleryApplication() }
    } catch (error: Throwable) {
        runCatching { galleryLog(error.stackTraceToString()) }
        throw error
    }
}

internal fun galleryLog(message: String) {
    println("Kotlin WinUI 2 Gallery: $message")
    appendGalleryLog(checkNotNull(checkNotNull(ApplicationData.current).localFolder).path + "/gallery.log", message)
}

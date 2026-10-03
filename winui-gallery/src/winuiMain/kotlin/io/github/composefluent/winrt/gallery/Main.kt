package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.Application

private var application: GalleryApplication? = null
internal var processArguments: Array<String> = emptyArray()

fun main(args: Array<String> = emptyArray()) {
    // The JVM launcher passes command-line arguments to this entry point, while
    // Native receives the desktop command line through AppInstance's Launch
    // activation payload. Preserve both paths so packaged runs can select a
    // sample deterministically and normal protocol/notification activation is
    // unchanged.
    processArguments = args
    println("Kotlin WinUI Gallery: starting application")
    Application.start {
        println("Kotlin WinUI Gallery: creating application")
        application = GalleryApplication()
    }
    application = null
}


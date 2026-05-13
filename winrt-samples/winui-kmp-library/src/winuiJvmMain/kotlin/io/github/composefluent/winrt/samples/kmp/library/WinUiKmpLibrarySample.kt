package io.github.composefluent.winrt.samples.kmp.library

import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button

object WinUiKmpLibrarySample {
    private var activeWindow: Window? = null

    fun start() {
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui-kmp-library: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                Application.start {
                    println("winui-kmp-library: application callback invoked")
                    val app = Application()
                    val window = Window()
                    val button = Button()
                    button.content = "KMP library WinUI"
                    window.content = button
                    activeWindow = window
                    window.activate()
                    println("winui-kmp-library: window activated native")
                    if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
                        app.exit()
                    }
                }
            }
            activeWindow = null
        }
    }
}

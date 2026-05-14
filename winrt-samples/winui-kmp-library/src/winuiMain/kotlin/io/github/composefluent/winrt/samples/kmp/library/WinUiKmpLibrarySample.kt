package io.github.composefluent.winrt.samples.kmp.library

import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import io.github.composefluent.winrt.runtime.WinRtUri
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.TextBox
import windows.system.Launcher

object WinUiKmpLibrarySample {
    private var activeWindow: Window? = null
    private val activeEventTokens = mutableListOf<EventRegistrationToken>()

    fun start() {
        activeEventTokens.clear()
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui-kmp-library: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                Application.start {
                    println("winui-kmp-library: application callback invoked")
                    val app = Application()
                    println("winui-kmp-library: application created")
                    loadAppXamlResources(app)
                    val window = Window()
                    println("winui-kmp-library: window created")
                    val panel = Canvas()
                    println("winui-kmp-library: canvas created")
                    val button = Button()
                    println("winui-kmp-library: button created")
                    val textBox = TextBox()
                    println("winui-kmp-library: textBox created")

                    button.content = "KMP library WinUI"
                    println("winui-kmp-library: button content set")
                    check(button.content == "KMP library WinUI") {
                        "Button.content did not round-trip assigned string: ${button.content}"
                    }
                    println("winui-kmp-library: button content round-trip")
                    button.content = null
                    check(button.content == null) {
                        "Button.content did not round-trip cleared null content: ${button.content}"
                    }
                    println("winui-kmp-library: button null content round-trip")
                    button.content = "KMP library WinUI"
                    textBox.text = "initial"
                    println("winui-kmp-library: textBox initial text set")
                    panel.children.add(button)
                    println("winui-kmp-library: button added")
                    panel.children.add(textBox)
                    println("winui-kmp-library: textBox added")
                    check(panel.children[0] is Button) {
                        "Canvas.children[0] did not recover the Button runtime-class wrapper: ${panel.children[0]::class.qualifiedName}"
                    }
                    println("winui-kmp-library: child runtime class recovered")
                    Canvas.setLeft(button, 24.0)
                    Canvas.setTop(button, 12.0)
                    check(Canvas.getLeft(button) == 24.0 && Canvas.getTop(button) == 12.0) {
                        "Canvas attached positioning did not round-trip"
                    }
                    println("winui-kmp-library: canvas attached positioning set")
                    window.content = panel
                    println("winui-kmp-library: window content set")
                    activeWindow = window
                    registerCallbackSmoke(app, window, button, textBox)
                    println("winui-kmp-library: callbacks registered")
                    window.activate()
                    println("winui-kmp-library: window activated native")
                    textBox.focus(FocusState.Programmatic)
                    textBox.text = "changed"
                    if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
                        app.exit()
                    }
                }
            }
            activeWindow = null
            activeEventTokens.clear()
        }
    }

    @Suppress("unused")
    private fun launcherProjectionCompileSmoke() {
        Launcher.launchUriAsync(WinRtUri("https://example.invalid/"))
    }

    private fun loadAppXamlResources(app: Application) {
        Application.loadComponent(app, WinRtUri("ms-appx:///App.xaml"))
        println("winui-kmp-library: app xaml loaded")
    }

    private fun registerCallbackSmoke(
        app: Application,
        window: Window,
        button: Button,
        textBox: TextBox,
    ) {
        println("winui-kmp-library: registering window activated")
        activeEventTokens += window.activated.add { _, _ ->
            println("winui-kmp-library: window activated callback")
            textBox.text = "activated"
        }
        println("winui-kmp-library: registering window visibility")
        activeEventTokens += window.visibilityChanged.add { _, _ ->
            println("winui-kmp-library: window visibility callback")
        }
        println("winui-kmp-library: registering button loaded")
        activeEventTokens += button.loaded.add { _, _ ->
            println("winui-kmp-library: button loaded callback")
        }
        println("winui-kmp-library: registering button layout")
        activeEventTokens += button.layoutUpdated.add { _, _ ->
            println("winui-kmp-library: button layout callback")
        }
        println("winui-kmp-library: registering button focus")
        activeEventTokens += button.gotFocus.add { _, _ ->
            println("winui-kmp-library: button focus callback")
        }
        println("winui-kmp-library: registering button pointer")
        activeEventTokens += button.pointerPressed.add { _, _ ->
            println("winui-kmp-library: button pointer callback")
        }
        println("winui-kmp-library: registering text changed")
        activeEventTokens += textBox.textChanged.add { _, _ ->
            println("winui-kmp-library: text changed callback")
        }
        println("winui-kmp-library: registering text changing")
        activeEventTokens += textBox.textChanging.add { _, _ ->
            println("winui-kmp-library: text changing callback")
        }
        println("winui-kmp-library: registering before text changing")
        activeEventTokens += textBox.beforeTextChanging.add { _, _ ->
            println("winui-kmp-library: before text changing callback")
        }
        println("winui-kmp-library: registering text getting focus")
        activeEventTokens += textBox.gettingFocus.add { _, _ ->
            println("winui-kmp-library: text getting focus callback")
        }
        println("winui-kmp-library: registering text losing focus")
        activeEventTokens += textBox.losingFocus.add { _, _ ->
            println("winui-kmp-library: text losing focus callback")
        }
        println("winui-kmp-library: registering text unloaded")
        activeEventTokens += textBox.unloaded.add { _, _ ->
            println("winui-kmp-library: text unloaded callback")
            if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
                app.exit()
            }
        }
    }
}

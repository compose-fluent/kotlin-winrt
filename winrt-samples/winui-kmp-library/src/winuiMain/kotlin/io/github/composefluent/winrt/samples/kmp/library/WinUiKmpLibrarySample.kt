package io.github.composefluent.winrt.samples.kmp.library

import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import io.github.composefluent.winrt.samples.kmp.base.WinUiKmpBaseLibrarySample
import microsoft.ui.dispatching.DispatcherQueueHandler
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.ResourceDictionary
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.AccessibilityView
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.XamlControlsResources
import windows.system.display.DisplayRequest

object WinUiKmpLibrarySample {
    private var activeApplication: WinUiKmpLibraryApp? = null

    fun start() {
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui-kmp-library: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                Application.start {
                    println("winui-kmp-library: application callback invoked")
                    activeApplication = WinUiKmpLibraryApp()
                    println("winui-kmp-library: application created")
                }
            }
            activeApplication?.close()
            activeApplication = null
        }
    }

    @Suppress("unused")
    private fun launcherProjectionCompileSmoke() {
        WinUiKmpBaseLibrarySample.launcherProjectionCompileSmoke()
        DisplayRequest().requestActive()
        DisplayRequest().requestRelease()
    }
}

class WinUiKmpLibraryApp : Application(), AutoCloseable {
    private var activeWindow: Window? = null
    private val activeEventTokens = mutableListOf<EventRegistrationToken>()
    private var focusSmokeCompleted = false

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        launchWithResources()
    }

    override fun close() {
        activeWindow = null
        activeEventTokens.clear()
        focusSmokeCompleted = false
    }

    private fun launchWithResources() {
        installXamlControlsResources()
        launchCore()
    }

    private fun installXamlControlsResources() {
        println("winui-kmp-library: install resources dictionary")
        val resources = checkNotNull(this.resources) {
            "Application resources were not initialized."
        }
        println("winui-kmp-library: install resources merged dictionaries")
        val mergedDictionaries = checkNotNull(resources.mergedDictionaries) {
            "Application resources did not expose merged dictionaries."
        }
        println("winui-kmp-library: install resources create controls resources")
        val controlsResources = loadXamlControlsResources()
        println("winui-kmp-library: install resources add")
        mergedDictionaries.add(controlsResources)
        println("winui-kmp-library: install resources done")
    }

    private fun loadXamlControlsResources(): ResourceDictionary {
        return XamlControlsResources()
    }

    private fun launchCore() {
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
        val children = checkNotNull(panel.children) {
            "Canvas children collection was not initialized."
        }
        children.add(button)
        println("winui-kmp-library: button added")
        children.add(textBox)
        println("winui-kmp-library: textBox added")
        check(children[0] is Button) {
            "Canvas.children[0] did not recover the Button runtime-class wrapper: ${children[0]::class.qualifiedName}"
        }
        println("winui-kmp-library: child runtime class recovered")
        Canvas.setLeft(button, 24.0)
        Canvas.setTop(button, 12.0)
        check(Canvas.getLeft(button) == 24.0 && Canvas.getTop(button) == 12.0) {
            "Canvas attached positioning did not round-trip"
        }
        println("winui-kmp-library: canvas attached positioning set")
        AutomationProperties.setAccessibilityView(button, AccessibilityView.Raw)
        println("winui-kmp-library: detached automation accessibility view set")
        button.clearValue(checkNotNull(AutomationProperties.accessibilityViewProperty) {
            "AutomationProperties.accessibilityViewProperty was not available."
        })
        println("winui-kmp-library: detached automation accessibility view cleared")
        window.content = panel
        println("winui-kmp-library: window content set")
        activeWindow = window
        registerCallbackSmoke(window, button, textBox)
        println("winui-kmp-library: callbacks registered")
        window.activate()
        println("winui-kmp-library: window activated native")
    }

    private fun registerCallbackSmoke(
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
            if (!focusSmokeCompleted) {
                focusSmokeCompleted = true
                runFocusSmoke(window, button, textBox)
            }
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
                checkNotNull(window.dispatcherQueue) {
                    "Window dispatcher queue was not available."
                }.tryEnqueue(DispatcherQueueHandler {
                    println("winui-kmp-library: unloaded auto exit enqueued")
                    exit()
                })
            }
        }
    }

    private fun runFocusSmoke(
        window: Window,
        button: Button,
        textBox: TextBox,
    ) {
        button.isTabStop = true
        textBox.isTabStop = true
        println("winui-kmp-library: focus smoke starting")
        val buttonFocused = button.focus(FocusState.Programmatic)
        println("winui-kmp-library: button focus result=$buttonFocused state=${button.focusState}")
        val textBoxFocused = textBox.focus(FocusState.Programmatic)
        println("winui-kmp-library: textBox focus result=$textBoxFocused state=${textBox.focusState}")
        check(buttonFocused || textBoxFocused) {
            "WinUI controls rejected programmatic focus after layout"
        }
        textBox.text = "changed"
        println("winui-kmp-library: textBox changed after focus")
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
            checkNotNull(window.dispatcherQueue) {
                "Window dispatcher queue was not available."
            }.tryEnqueue(DispatcherQueueHandler {
                println("winui-kmp-library: auto exit enqueued")
                exit()
            })
        }
    }
}

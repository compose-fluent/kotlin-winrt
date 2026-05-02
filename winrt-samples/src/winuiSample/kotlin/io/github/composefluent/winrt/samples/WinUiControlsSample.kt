package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ComboBox
import microsoft.ui.xaml.controls.ListView
import microsoft.ui.xaml.controls.Slider
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToggleSwitch
import microsoft.ui.xaml.controls.XamlControlsResources
import microsoft.ui.xaml.media.MicaBackdrop

data class WinUiControlsSampleResult(
    val xamlResourcesInstalled: Boolean,
    val micaBackdropApplied: Boolean,
    val controlsComposed: Int,
)

object WinUiControlsSample {
    private var activeApplication: WinUiControlsApp? = null

    fun start() {
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui-controls: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                Application.start {
                    activeApplication = WinUiControlsApp()
                    println("winui-controls: application composed")
                }
            }
        }
    }

    fun launchForSmoke(): WinUiControlsSampleResult =
        RuntimeScope.initializeSingleThreaded().use {
            WinUiControlsApp().launchWithResources()
        }
}

class WinUiControlsApp : Application() {
    private var myWindow: Window? = null

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        println("winui-controls: onLaunched")
        launchWithResources()
        println("winui-controls: window activated")
    }

    fun launchWithResources(): WinUiControlsSampleResult {
        installXamlResources()
        return launchCore()
    }

    fun launchCore(): WinUiControlsSampleResult {
        println("winui-controls: launchCore")
        val window = Window()
        window.title = "Kotlin WinRT WinUI Controls"
        window.systemBackdrop = MicaBackdrop()
        println("winui-controls: creating controls surface")
        window.content = createControlsSurface()
        println("winui-controls: controls surface assigned")
        window.activate()
        myWindow = window

        return WinUiControlsSampleResult(
            xamlResourcesInstalled = true,
            micaBackdropApplied = true,
            controlsComposed = 7,
        )
    }

    private fun installXamlResources() {
        Application.current.resources.mergedDictionaries.add(XamlControlsResources())
    }

    private fun createControlsSurface(): UIElement {
        val root = StackPanel()
        root.padding = Thickness(32.0, 32.0, 32.0, 32.0)
        root.spacing = 16.0

        root.children.add(label("WinUI 3 controls"))
        root.children.add(TextBox().apply {
            text = "Kotlin WinRT"
            width = 320.0
        })
        root.children.add(ToggleSwitch().apply {
            isOn = true
        })
        root.children.add(Slider().apply {
            minimum = 0.0
            maximum = 100.0
            value = 42.0
            width = 320.0
        })
        root.children.add(ComboBox().apply {
            width = 320.0
            items.add("Compact")
            items.add("Comfortable")
            items.add("Expanded")
        })
        root.children.add(ListView().apply {
            width = 320.0
            height = 140.0
            items.add("TextBox")
            items.add("ToggleSwitch")
            items.add("Slider")
            items.add("ComboBox")
            items.add("ListView")
        })
        root.children.add(Button().apply {
            content = "Apply"
        })

        return root
    }

    private fun label(text: String): TextBlock =
        TextBlock().apply {
            this.text = text
            fontSize = 24.0
        }
}

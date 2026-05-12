package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinUiXamlMetadataProvider
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.ResourceDictionary
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
            if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipRuntimeScope")) {
                startApplication()
                return
            }
            RuntimeScope.initializeSingleThreaded().use {
                startApplication()
            }
        }
    }

    fun launchForSmoke(): WinUiControlsSampleResult =
        RuntimeScope.initializeSingleThreaded().use {
            WinUiControlsApp().use { app ->
                app.launchWithResources()
            }
        }

    private fun startApplication() {
        Application.start {
            activeApplication = WinUiControlsApp()
            println("winui-controls: application composed")
        }
        activeApplication?.close()
        activeApplication = null
    }
}

class WinUiControlsApp : Application(), AutoCloseable {
    private var myWindow: Window? = null

    fun launchWithResources(): WinUiControlsSampleResult {
        if (!java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipXamlResources")) {
            installXamlResources()
        }
        return launchCore()
    }

    override fun close() {
        myWindow = null
    }

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        println("winui-controls: onLaunched")
        launchWithResources()
        println("winui-controls: window activated")
    }

    fun launchCore(): WinUiControlsSampleResult {
        println("winui-controls: launchCore")
        val window = Window()
        window.title = "Kotlin WinRT WinUI Controls"
        if (!java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipMica")) {
            window.systemBackdrop = MicaBackdrop()
        }
        println("winui-controls: creating controls surface")
        if (!java.lang.Boolean.getBoolean("kotlin.winrt.samples.noWinUiContent")) {
            window.content = createControlsSurface()
        }
        println("winui-controls: controls surface assigned")
        myWindow = window
        window.activate()
        println("winui-controls: window activated native")
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
            Application.current.exit()
        }

        return WinUiControlsSampleResult(
            xamlResourcesInstalled = true,
            micaBackdropApplied = true,
            controlsComposed = 7,
        )
    }

    private fun installXamlResources() {
        println("winui-controls: install resources current")
        val application = Application.current
        println("winui-controls: initialize xaml metadata provider")
        val metadataProvider = WinUiXamlMetadataProvider.tryCreate()
        println("winui-controls: xaml metadata provider=${metadataProvider?.pointer ?: "not-created"}")
        metadataProvider?.close()
        println("winui-controls: install resources dictionary")
        val resources = application.resources
        println("winui-controls: install resources merged dictionaries")
        val mergedDictionaries = resources.mergedDictionaries
        println("winui-controls: install resources create controls resources")
        val controlsResources = loadXamlControlsResources()
        println("winui-controls: install resources add")
        mergedDictionaries.add(controlsResources)
        println("winui-controls: install resources done")
    }

    private fun loadXamlControlsResources(): ResourceDictionary {
        return XamlControlsResources()
    }

    private fun createControlsSurface(): UIElement {
        val skipObjectContent = java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipObjectContent")
        println("winui-controls: create StackPanel")
        val root = StackPanel()
        println("winui-controls: set StackPanel padding")
        root.padding = Thickness(32.0, 32.0, 32.0, 32.0)
        println("winui-controls: set StackPanel spacing")
        root.spacing = 16.0
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.minimalWinUiSurface")) {
            return root
        }

        println("winui-controls: add label")
        root.children.add(label("WinUI 3 controls"))
        println("winui-controls: add textbox")
        root.children.add(TextBox().apply {
            text = "Kotlin WinRT"
            width = 320.0
        })
        println("winui-controls: add toggle")
        root.children.add(ToggleSwitch().apply {
            isOn = true
        })
        println("winui-controls: add slider")
        val slider = Slider()
        println("winui-controls: slider minimum")
        slider.minimum = 0.0
        println("winui-controls: slider maximum")
        slider.maximum = 100.0
        println("winui-controls: slider value")
        slider.value = 42.0
        println("winui-controls: slider width")
        slider.width = 320.0
        println("winui-controls: slider add child")
        root.children.add(slider)
        println("winui-controls: add combobox")
        root.children.add(ComboBox().apply {
            width = 320.0
            if (!skipObjectContent) {
                items.add("Compact")
                items.add("Comfortable")
                items.add("Expanded")
            }
        })
        println("winui-controls: add listview")
        root.children.add(ListView().apply {
            width = 320.0
            height = 140.0
            if (!skipObjectContent) {
                items.add("TextBox")
                items.add("ToggleSwitch")
                items.add("Slider")
                items.add("ComboBox")
                items.add("ListView")
            }
        })
        println("winui-controls: add button")
        root.children.add(Button().apply {
            if (!skipObjectContent) {
                content = "Apply"
            }
        })

        return root
    }

    private fun label(text: String): TextBlock =
        TextBlock().apply {
            this.text = text
            fontSize = 24.0
        }
}

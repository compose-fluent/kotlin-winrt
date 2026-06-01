package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.ResourceDictionary
import microsoft.ui.xaml.RoutedEventHandler
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
import winui3package.SettingsCard
import winui3package.Shimmer

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
    private val deferredLoadingShimmers = mutableListOf<Shimmer>()
    private val deferredLoadingShimmerTokens = mutableMapOf<Shimmer, EventRegistrationToken>()

    fun launchWithResources(): WinUiControlsSampleResult {
        if (!java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipXamlResources")) {
            installXamlResources()
        }
        return launchCore()
    }

    override fun close() {
        deferredLoadingShimmerTokens.forEach { (shimmer, token) ->
            runCatching { shimmer.removeLoaded(token) }
        }
        deferredLoadingShimmerTokens.clear()
        deferredLoadingShimmers.clear()
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
            window.systemBackdrop = null
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
        val pendingDeferredLoading = applyDeferredShimmerLoading()
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi") && !pendingDeferredLoading) {
            checkNotNull(Application.current) { "Expected current WinUI application before auto-exit." }.exit()
        }

        return WinUiControlsSampleResult(
            xamlResourcesInstalled = true,
            micaBackdropApplied = true,
            controlsComposed = 9,
        )
    }

    private fun installXamlResources() {
        println("winui-controls: install resources current")
        val application = checkNotNull(Application.current) { "Expected current WinUI application while installing resources." }
        println("winui-controls: install resources dictionary")
        val resources = checkNotNull(application.resources) { "Expected WinUI application resources." }
        println("winui-controls: install resources merged dictionaries")
        val mergedDictionaries = checkNotNull(resources.mergedDictionaries) { "Expected merged resource dictionaries." }
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
        val skipSettingsCard = java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipSettingsCard")
        val skipShimmer = java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipShimmer")
        val enableShimmerLoading = java.lang.Boolean.getBoolean("kotlin.winrt.samples.enableShimmerLoading")
        val skipShimmerSizing = java.lang.Boolean.getBoolean("kotlin.winrt.samples.skipShimmerSizing")
        deferredLoadingShimmers.clear()
        println("winui-controls: create StackPanel")
        val root = StackPanel()
        println("winui-controls: set StackPanel padding")
        root.padding = Thickness(32.0, 32.0, 32.0, 32.0)
        println("winui-controls: set StackPanel spacing")
        root.spacing = 16.0
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.minimalWinUiSurface")) {
            return root
        }
        val rootChildren = checkNotNull(root.children) { "Expected StackPanel children collection." }

        println("winui-controls: add label")
        rootChildren.add(label("WinUI 3 controls"))
        println("winui-controls: add textbox")
        rootChildren.add(TextBox().apply {
            text = "Kotlin WinRT"
            width = 320.0
        })
        println("winui-controls: add toggle")
        rootChildren.add(ToggleSwitch().apply {
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
        rootChildren.add(slider)
        println("winui-controls: add combobox")
        rootChildren.add(ComboBox().apply {
            width = 320.0
            if (!skipObjectContent) {
                val comboBoxItems = checkNotNull(items) { "Expected ComboBox items collection." }
                comboBoxItems.add("Compact")
                comboBoxItems.add("Comfortable")
                comboBoxItems.add("Expanded")
            }
        })
        println("winui-controls: add listview")
        rootChildren.add(ListView().apply {
            width = 320.0
            height = 140.0
            if (!skipObjectContent) {
                val listViewItems = checkNotNull(items) { "Expected ListView items collection." }
                listViewItems.add("TextBox")
                listViewItems.add("ToggleSwitch")
                listViewItems.add("Slider")
                listViewItems.add("ComboBox")
                listViewItems.add("ListView")
            }
        })
        println("winui-controls: add button")
        rootChildren.add(Button().apply {
            if (!skipObjectContent) {
                content = "Apply"
            }
        })
        if (!skipSettingsCard) {
            println("winui-controls: add WinUIEssential SettingsCard")
            rootChildren.add(SettingsCard().apply {
                header = "WinUIEssential SettingsCard"
                description = "Projected from WinUIEssential.WinUI3"
                if (!skipObjectContent) {
                    content = ToggleSwitch().apply {
                        isOn = true
                    }
                }
            })
        }
        if (!skipShimmer) {
            println("winui-controls: add WinUIEssential Shimmer")
            rootChildren.add(Shimmer().apply {
                if (enableShimmerLoading) {
                    deferredLoadingShimmers.add(this)
                }
                if (!skipShimmerSizing) {
                    width = 320.0
                    height = 56.0
                }
                if (!skipObjectContent) {
                    content = TextBlock().apply {
                        text = "Loading projected WinUI content"
                    }
                }
            })
        }

        return root
    }

    private fun applyDeferredShimmerLoading(): Boolean {
        if (deferredLoadingShimmers.isEmpty()) {
            return false
        }
        deferredLoadingShimmers.toList().forEach { shimmer ->
            println("winui-controls: defer WinUIEssential Shimmer loading until Loaded")
            if (shimmer.isLoaded) {
                applyLoadedShimmerLoading(shimmer)
            } else {
                var token = EventRegistrationToken()
                token = shimmer.addLoaded(RoutedEventHandler { _, _ ->
                    shimmer.removeLoaded(token)
                    deferredLoadingShimmerTokens.remove(shimmer)
                    applyLoadedShimmerLoading(shimmer)
                    exitAfterDeferredShimmerLoadingIfReady()
                })
                deferredLoadingShimmerTokens[shimmer] = token
            }
        }
        deferredLoadingShimmers.clear()
        return deferredLoadingShimmerTokens.isNotEmpty()
    }

    private fun applyLoadedShimmerLoading(shimmer: Shimmer) {
        println("winui-controls: apply WinUIEssential Shimmer loading after Loaded")
        shimmer.applyTemplate()
        val container = shimmer.findName("Container")
        if (container == null) {
            println("winui-controls: skip WinUIEssential Shimmer loading because template Container is unavailable")
            return
        }
        shimmer.updateLayout()
        shimmer.isLoading = true
    }

    private fun exitAfterDeferredShimmerLoadingIfReady() {
        if (deferredLoadingShimmerTokens.isEmpty() && java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoExitWinUi")) {
            checkNotNull(Application.current) { "Expected current WinUI application after deferred Shimmer loading." }.exit()
        }
    }

    private fun label(text: String): TextBlock =
        TextBlock().apply {
            this.text = text
            fontSize = 24.0
        }
}

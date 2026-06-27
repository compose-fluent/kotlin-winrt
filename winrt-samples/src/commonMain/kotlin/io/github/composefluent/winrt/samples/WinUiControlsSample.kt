package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.projections.support.WinUiXamlComponentResources
import windows.foundation.EventRegistrationToken
import io.github.composefluent.winrt.runtime.WinRTWindowsAppSdkBootstrap
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
import microsoft.ui.xaml.controls.MenuFlyout
import microsoft.ui.xaml.controls.MenuFlyoutItem
import microsoft.ui.xaml.controls.Slider
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToggleSwitch
import microsoft.ui.xaml.controls.XamlControlsResources
import microsoft.ui.xaml.media.MicaBackdrop
import winui3package.SettingsCard
import winui3package.Shimmer
import windows.foundation.Point

data class WinUiControlsSampleResult(
    val xamlResourcesInstalled: Boolean,
    val micaBackdropApplied: Boolean,
    val controlsComposed: Int,
)

object WinUiControlsSample {
    private var activeApplication: WinUiControlsApp? = null

    fun start() {
        startApplication()
    }

    fun launchForSmoke(): WinUiControlsSampleResult =
        WinRTWindowsAppSdkBootstrap.initializeApplicationHost().use {
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
    private var deferredMenuFlyoutButton: Button? = null
    private var deferredMenuFlyoutToken: EventRegistrationToken? = null

    fun launchWithResources(): WinUiControlsSampleResult {
        if (!winRTSampleOption("kotlin.winrt.samples.skipXamlResources")) {
            installXamlResources()
        }
        return launchCore()
    }

    override fun close() {
        deferredLoadingShimmerTokens.forEach { (shimmer, token) ->
            runCatching { shimmer.removeLoaded(token) }
        }
        deferredMenuFlyoutToken?.let { token ->
            deferredMenuFlyoutButton?.let { button ->
                runCatching { button.removeLoaded(token) }
            }
        }
        deferredLoadingShimmerTokens.clear()
        deferredLoadingShimmers.clear()
        deferredMenuFlyoutToken = null
        deferredMenuFlyoutButton = null
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
        if (!winRTSampleOption("kotlin.winrt.samples.skipMica")) {
            window.systemBackdrop = MicaBackdrop()
            window.systemBackdrop = null
            window.systemBackdrop = MicaBackdrop()
        }
        println("winui-controls: creating controls surface")
        if (!winRTSampleOption("kotlin.winrt.samples.noWinUiContent")) {
            window.content = createControlsSurface()
        }
        println("winui-controls: controls surface assigned")
        myWindow = window
        window.activate()
        println("winui-controls: window activated native")
        val pendingDeferredLoading = applyDeferredShimmerLoading()
        exitIfDeferredWinUiWorkIsReady(pendingDeferredLoading)

        return WinUiControlsSampleResult(
            xamlResourcesInstalled = true,
            micaBackdropApplied = true,
            controlsComposed = 10,
        )
    }

    private fun installXamlResources() {
        println("winui-controls: install resources current")
        val application = checkNotNull(Application.current) { "Expected current WinUI application while installing resources." }
        println("winui-controls: install resources dictionary")
        val resources = application.resources
        println("winui-controls: install resources merged dictionaries")
        val mergedDictionaries = resources.mergedDictionaries
        println("winui-controls: install resources create controls resources")
        val controlsResources = loadXamlControlsResources()
        println("winui-controls: install resources add")
        mergedDictionaries.add(controlsResources)
        installComponentXamlResources(mergedDictionaries)
        println("winui-controls: install resources done")
    }

    private fun loadXamlControlsResources(): ResourceDictionary {
        return XamlControlsResources()
    }

    private fun installComponentXamlResources(mergedDictionaries: MutableList<ResourceDictionary>) {
        WinUiXamlComponentResources.installInto(mergedDictionaries)
        println("winui-controls: install component XAML resources")
    }

    private fun createControlsSurface(): UIElement {
        val skipObjectContent = winRTSampleOption("kotlin.winrt.samples.skipObjectContent")
        val skipSettingsCard = winRTSampleOption("kotlin.winrt.samples.skipSettingsCard")
        val skipShimmer = winRTSampleOption("kotlin.winrt.samples.skipShimmer")
        val enableShimmerLoading = winRTSampleOption("kotlin.winrt.samples.enableShimmerLoading")
        val skipShimmerSizing = winRTSampleOption("kotlin.winrt.samples.skipShimmerSizing")
        deferredLoadingShimmers.clear()
        deferredMenuFlyoutToken = null
        deferredMenuFlyoutButton = null
        println("winui-controls: create StackPanel")
        val root = StackPanel()
        println("winui-controls: set StackPanel padding")
        root.padding = Thickness(32.0, 32.0, 32.0, 32.0)
        println("winui-controls: set StackPanel spacing")
        root.spacing = 16.0
        if (winRTSampleOption("kotlin.winrt.samples.minimalWinUiSurface")) {
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
        println("winui-controls: add MenuFlyout.ShowAt button")
        rootChildren.add(menuFlyoutButton(skipObjectContent))
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

    private fun menuFlyoutButton(skipObjectContent: Boolean): Button {
        val button = Button()
        if (!skipObjectContent) {
            button.content = "Open menu flyout"
        }
        val menuFlyout = MenuFlyout()
        val menuItems = checkNotNull(menuFlyout.items) { "Expected MenuFlyout items collection." }
        menuItems.add(MenuFlyoutItem().apply {
            text = "MenuFlyout.ShowAt"
        })
        menuItems.add(MenuFlyoutItem().apply {
            text = "Projected WinUI item"
        })
        button.click.add(RoutedEventHandler { _, _ ->
            showMenuFlyoutWithOffset(menuFlyout, button)
        })
        scheduleAutoMenuFlyoutShow(menuFlyout, button)
        return button
    }

    private fun showMenuFlyoutWithOffset(menuFlyout: MenuFlyout, button: Button) {
        println("winui-controls: invoke MenuFlyout.ShowAt with offset")
        menuFlyout.showAt(button, Point(12.0f, 36.0f))
        println("winui-controls: invoked MenuFlyout.ShowAt with offset")
    }

    private fun scheduleAutoMenuFlyoutShow(menuFlyout: MenuFlyout, button: Button) {
        if (!winRTSampleOption("kotlin.winrt.samples.autoShowMenuFlyout")) {
            return
        }
        println("winui-controls: auto MenuFlyout.ShowAt requested")
        if (button.isLoaded) {
            showMenuFlyoutWithOffset(menuFlyout, button)
            return
        }
        var token = EventRegistrationToken()
        token = button.addLoaded(RoutedEventHandler { _, _ ->
            button.removeLoaded(token)
            deferredMenuFlyoutToken = null
            deferredMenuFlyoutButton = null
            showMenuFlyoutWithOffset(menuFlyout, button)
            exitAfterDeferredMenuFlyoutIfReady()
        })
        deferredMenuFlyoutToken = token
        deferredMenuFlyoutButton = button
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
        shimmer.updateLayout()
        shimmer.isLoading = true
        println("winui-controls: applied WinUIEssential Shimmer loading")
    }

    private fun exitAfterDeferredShimmerLoadingIfReady() {
        if (deferredLoadingShimmerTokens.isEmpty() && deferredMenuFlyoutToken == null &&
            winRTSampleOption("kotlin.winrt.samples.autoExitWinUi")
        ) {
            checkNotNull(Application.current) { "Expected current WinUI application after deferred Shimmer loading." }.exit()
        }
    }

    private fun exitAfterDeferredMenuFlyoutIfReady() {
        if (deferredLoadingShimmerTokens.isEmpty() && deferredMenuFlyoutToken == null &&
            winRTSampleOption("kotlin.winrt.samples.autoExitWinUi")
        ) {
            checkNotNull(Application.current) { "Expected current WinUI application after deferred MenuFlyout.ShowAt." }.exit()
        }
    }

    private fun exitIfDeferredWinUiWorkIsReady(pendingDeferredLoading: Boolean) {
        if (winRTSampleOption("kotlin.winrt.samples.autoExitWinUi") &&
            !pendingDeferredLoading &&
            deferredMenuFlyoutToken == null
        ) {
            checkNotNull(Application.current) { "Expected current WinUI application before auto-exit." }.exit()
        }
    }

    private fun label(text: String): TextBlock =
        TextBlock().apply {
            this.text = text
            fontSize = 24.0
        }
}

package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.Application
import microsoft.ui.xaml.DependencyProperty
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.VerticalAlignment
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.input.TappedEventHandler
import microsoft.ui.xaml.input.TappedRoutedEventArgs
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.WinRTWindowsAppSdkBootstrap

data class WinUiDesktopSampleResult(
    val dependencyPropertyUnsetValueAvailable: Boolean,
    val clickToken: EventRegistrationToken,
    val tappedHandlerRegistered: Boolean,
)

object WinUiDesktopSample {
    private var activeDesktopApplication: WinUiDesktopApp? = null

    fun start() {
        Application.start {
            println("winui: application callback invoked")
            val desktopApplication = WinUiDesktopApp()
            activeDesktopApplication = desktopApplication
            println("winui: application composed")
        }
    }

    fun launchForSmoke(): WinUiDesktopSampleResult =
        WinRTWindowsAppSdkBootstrap.initializeApplicationHost().use {
            WinUiDesktopApp().launchCore()
        }
}

class WinUiDesktopApp : Application() {
    private var myWindow: Window? = null

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        launchCore()
        println("winui: window activated")
    }

    fun launchCore(): WinUiDesktopSampleResult {
        println("winui: launch begin")
        val unsetValue = DependencyProperty.unsetValue
        println("winui: dependency property unset value acquired")
        val button = Button()
        println("winui: button created")
        button.content = "Click me to load MainPage"
        println("winui: button content set")
        button.horizontalAlignment = HorizontalAlignment.Center
        button.verticalAlignment = VerticalAlignment.Center
        println("winui: button alignment set")
        val clickToken = button.click.add(RoutedEventHandler { sender, args -> buttonClick(sender, args) })
        println("winui: click handler registered")

        val window = Window()
        println("winui: window created")
        window.content = button
        println("winui: window content set")
        window.activate()
        println("winui: window activated native")
        myWindow = window
        if (winRTSampleOption("kotlin.winrt.samples.autoNavigateWinUi")) {
            println("winui: auto navigation requested")
            showMainPage()
        }

        return WinUiDesktopSampleResult(
            dependencyPropertyUnsetValueAvailable = unsetValue != null,
            clickToken = clickToken,
            tappedHandlerRegistered = true,
        )
    }

    private fun buttonClick(sender: Any?, args: RoutedEventArgs) {
        println("winui: button click invoked")
        showMainPage()
    }

    private fun showMainPage() {
        val mainPage = WinUiDesktopMainPage.create()
        println("winui: main page created")
        myWindow?.content = mainPage.page
        println("winui: window content replaced")
    }
}

private data class WinUiDesktopMainPage(
    val page: Page,
    val tappedHandlerRegistered: Boolean,
) {
    companion object {
        fun create(): WinUiDesktopMainPage {
            println("winui: page create begin")
            val page = Page()
            println("winui: page created")
            val pageContent = Button()
            pageContent.content = "Hello from WinUI Desktop!"
            page.content = pageContent
            println("winui: page content initialized")
            page.addHandler(
                checkNotNull(UIElement.tappedEvent) { "Expected UIElement.Tapped routed event." },
                TappedEventHandler { sender, args -> pointerTapped(sender, args) },
                true,
            )
            println("winui: page tapped handler registered")
            return WinUiDesktopMainPage(page, tappedHandlerRegistered = true)
        }

        private fun pointerTapped(sender: Any?, args: TappedRoutedEventArgs) {
        }
    }
}

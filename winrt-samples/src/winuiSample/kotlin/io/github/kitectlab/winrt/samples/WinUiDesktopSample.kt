package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Application
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.DependencyProperty
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.FrameworkElement
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.HorizontalAlignment
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.LaunchActivatedEventArgs
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.RoutedEventArgs
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.RoutedEventHandler
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.UIElement
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.VerticalAlignment
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Window
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.controls.Button
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.controls.ContentControl
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.controls.Page
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.controls.primitives.ButtonBase
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.input.TappedEventHandler
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.input.TappedRoutedEventArgs
import io.github.kitectlab.winrt.runtime.IID
import io.github.kitectlab.winrt.runtime.IInspectableReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.EventRegistrationToken
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.RuntimeScope
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection
import io.github.kitectlab.winrt.runtime.WinRtWindowsAppSdkBootstrap
import io.github.kitectlab.winrt.runtime.WinRtWindowsMessageLoop

data class WinUiDesktopSampleResult(
    val dependencyPropertyUnsetValueAvailable: Boolean,
    val clickToken: EventRegistrationToken,
    val tappedHandlerRegistered: Boolean,
)

object WinUiDesktopSample {
    private var activeDesktopApplication: WinUiDesktopApp? = null

    fun start() {
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                var launched = false
                Application.Start {
                    println("winui: application callback invoked")
                    val desktopApplication = WinUiDesktopApp()
                    activeDesktopApplication = desktopApplication
                    launched = true
                    println("winui: application composed")
                }
                println("winui: Application.Start returned launched=$launched")
                if (launched) {
                    WinRtWindowsMessageLoop.run()
                }
            }
        }
    }

    fun launchForSmoke(): WinUiDesktopSampleResult =
        RuntimeScope.initializeSingleThreaded().use {
            WinUiDesktopApp().launchCore()
        }
}

private class WinUiDesktopApp : Application() {
    private var myWindow: Window? = null

    override fun OnLaunched(args: LaunchActivatedEventArgs) {
        launchCore()
        println("winui: window activated")
    }

    fun launchCore(): WinUiDesktopSampleResult {
        println("winui: launch begin")
        val unsetValue = DependencyProperty.unsetValue
        println("winui: dependency property unset value acquired")
        val button = Button()
        println("winui: button created")
        val buttonContent = ContentControl.Metadata.wrap(button.inspectable())
        println("winui: button content projected")
        val buttonElement = FrameworkElement.Metadata.wrap(button.inspectable())
        println("winui: button framework element projected")
        val buttonBase = ButtonBase.Metadata.wrap(button.inspectable())
        println("winui: button base projected")

        buttonContent.content = boxString("Click me to load MainPage")
        println("winui: button content set")
        buttonElement.horizontalAlignment = HorizontalAlignment.Center
        buttonElement.verticalAlignment = VerticalAlignment.Center
        println("winui: button alignment set")
        val clickToken = buttonBase.addClick(RoutedEventHandler { sender, args -> buttonClick(sender, args) })
        println("winui: click handler registered")

        val window = Window()
        println("winui: window created")
        window.content = UIElement.Metadata.wrap(button.inspectable())
        println("winui: window content set")
        window.Activate()
        println("winui: window activated native")
        myWindow = window
        if (java.lang.Boolean.getBoolean("kotlin.winrt.samples.autoNavigateWinUi")) {
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
            ContentControl.Metadata.wrap(pageContent.inspectable()).content = boxString("Hello from WinUI Desktop!")
            page.content = pageContent
            println("winui: page content initialized")
            page.AddHandler(UIElement.tappedEvent, TappedEventHandler { sender, args -> pointerTapped(sender, args) }, true)
            println("winui: page tapped handler registered")
            return WinUiDesktopMainPage(page, tappedHandlerRegistered = true)
        }

        private fun pointerTapped(sender: Any?, args: TappedRoutedEventArgs) {
        }
    }
}

private fun IWinRTObject.inspectable(): IInspectableReference =
    nativeObject.asInspectable()

private fun boxString(value: String): IInspectableReference =
    IInspectableReference(
        PlatformAbi.toRawComPtr(WinRtReferenceProjection.fromManaged(value, IID.NullableString)),
        IID.IInspectable,
    )

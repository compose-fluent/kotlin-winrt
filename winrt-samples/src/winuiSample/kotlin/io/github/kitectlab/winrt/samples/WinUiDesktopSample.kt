package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.authoring.WinRtAuthoring
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Application
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.DependencyProperty
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.FrameworkElement
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.HorizontalAlignment
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.IApplicationFactory
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.IApplicationOverrides
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
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.IID
import io.github.kitectlab.winrt.runtime.IInspectableReference
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.EventRegistrationToken
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.RuntimeScope
import io.github.kitectlab.winrt.runtime.WinRtComposableObjectReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
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
    private var activeComposableApplication: WinRtComposableObjectReference? = null

    fun start() {
        WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
            println("winui: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
            RuntimeScope.initializeSingleThreaded().use {
                var launched = false
                Application.Start {
                    println("winui: application callback invoked")
                    val desktopApplication = WinUiDesktopApp()
                    activeDesktopApplication = desktopApplication
                    activeComposableApplication?.close()
                    activeComposableApplication = createComposableApplication(desktopApplication)
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

    private fun createComposableApplication(app: WinUiDesktopApp): WinRtComposableObjectReference =
        Application.ComposableFactory.acquire().use { factory ->
            WinRtAuthoring.createComposableObjectWithFactory(
                value = app,
                outerInterfaceId = IApplicationOverrides.Metadata.IID,
                composableFactory = factory,
                createInstanceSlot = IApplicationFactory.Metadata.CREATEINSTANCE_SLOT,
            )
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
        val clickToken = buttonBase.click.add(RoutedEventHandler { sender, args -> buttonClick(sender, args) })
        println("winui: click handler registered")

        val window = Window()
        println("winui: window created")
        window.content = UIElement.Metadata.wrap(button.inspectable())
        println("winui: window content set")
        window.Activate()
        println("winui: window activated native")
        myWindow = window

        return WinUiDesktopSampleResult(
            dependencyPropertyUnsetValueAvailable = !unsetValue.isDisposed,
            clickToken = clickToken,
            tappedHandlerRegistered = false,
        )
    }

    private fun buttonClick(sender: IInspectableReference, args: RoutedEventArgs) {
        val mainPage = WinUiDesktopMainPage.create()
        myWindow?.content = UIElement.Metadata.wrap(mainPage.page.inspectable())
    }
}

private data class WinUiDesktopMainPage(
    val page: Page,
    val tappedHandlerRegistered: Boolean,
) {
    companion object {
        fun create(): WinUiDesktopMainPage {
            val page = Page()
            val element = UIElement.Metadata.wrap(page.inspectable())
            element.addTappedHandler(TappedEventHandler { sender, args -> pointerTapped(sender, args) })
            return WinUiDesktopMainPage(page, tappedHandlerRegistered = true)
        }

        private fun pointerTapped(sender: IInspectableReference, args: TappedRoutedEventArgs) {
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

private fun UIElement.addTappedHandler(handler: TappedEventHandler) {
    WinRtDelegateBridge.createDelegate(
        iid = TappedEventHandlerIid,
        parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
        returnKind = WinRtDelegateValueKind.UNIT,
    ) { args ->
        handler(
            (args[0] as IUnknownReference).asInspectable(),
            TappedRoutedEventArgs.Metadata.wrap((args[1] as IUnknownReference).asInspectable()),
        )
    }.use { handle ->
        handle.createReference().use { reference ->
            IInspectableReference(reference.getRefPointer(), TappedEventHandlerIid).use { handlerReference ->
                AddHandler(UIElement.tappedEvent, handlerReference, true)
            }
        }
    }
}

private val TappedEventHandlerIid = Guid("B60074F3-125B-534E-8F9C-9769BD3F0F64")

package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Application
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.DependencyProperty
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.FrameworkElement
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.HorizontalAlignment
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
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection

data class WinUiDesktopSampleResult(
    val dependencyPropertyUnsetValueAvailable: Boolean,
    val clickToken: EventRegistrationToken,
    val tappedHandlerRegistered: Boolean,
)

object WinUiDesktopSample {
    fun start() {
        RuntimeScope.initializeSingleThreaded().use {
            Application.Start {
                Application()
                WinUiDesktopApplication().onLaunched()
            }
        }
    }

    fun launchForSmoke(): WinUiDesktopSampleResult =
        RuntimeScope.initializeSingleThreaded().use {
            WinUiDesktopApplication().onLaunched()
        }
}

private class WinUiDesktopApplication {
    private var myWindow: Window? = null

    fun onLaunched(): WinUiDesktopSampleResult {
        val unsetValue = DependencyProperty.unsetValue
        val button = Button()
        val buttonContent = ContentControl.Metadata.wrap(button.inspectable())
        val buttonElement = FrameworkElement.Metadata.wrap(button.inspectable())
        val buttonBase = ButtonBase.Metadata.wrap(button.inspectable())

        buttonContent.content = boxString("Click me to load MainPage")
        buttonElement.horizontalAlignment = HorizontalAlignment.Center
        buttonElement.verticalAlignment = VerticalAlignment.Center
        val clickToken = buttonBase.click.add(RoutedEventHandler { sender, args -> buttonClick(sender, args) })

        val window = Window()
        window.content = UIElement.Metadata.wrap(button.inspectable())
        window.Activate()
        myWindow = window

        return WinUiDesktopSampleResult(
            dependencyPropertyUnsetValueAvailable = !unsetValue.isDisposed,
            clickToken = clickToken,
            tappedHandlerRegistered = WinUiDesktopMainPage.create().tappedHandlerRegistered,
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

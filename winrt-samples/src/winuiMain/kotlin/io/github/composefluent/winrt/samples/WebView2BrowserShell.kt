package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.GridLength
import microsoft.ui.xaml.GridUnitType
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.VerticalAlignment
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.controls.Border
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ColumnDefinition
import microsoft.ui.xaml.controls.Control
import microsoft.ui.xaml.controls.Grid
import microsoft.ui.xaml.controls.Panel
import microsoft.ui.xaml.controls.ProgressBar
import microsoft.ui.xaml.controls.RowDefinition
import microsoft.ui.xaml.controls.Symbol
import microsoft.ui.xaml.controls.SymbolIcon
import microsoft.ui.xaml.controls.TabView
import microsoft.ui.xaml.controls.TabViewItem
import microsoft.ui.xaml.controls.TabViewWidthMode
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToolTipService
import microsoft.ui.xaml.controls.Viewbox
import microsoft.ui.xaml.controls.WebView2
import windows.ui.Color

internal const val WEBVIEW2_HOME_MIN_WIDTH = 720.0
private const val WEBVIEW2_VISUAL_BRIDGE_BACKGROUND_RESOURCE = "BrushForThemeBackgroundColor"

internal fun webView2HomeVisibility(windowWidth: Double): Visibility =
    if (windowWidth >= WEBVIEW2_HOME_MIN_WIDTH) {
        Visibility.Visible
    } else {
        Visibility.Collapsed
    }

internal data class WebView2BrowserShell(
    val root: Grid,
    val titleBar: TabView,
    val titleBarDragRegion: Grid,
    val address: TextBox,
    val loading: ProgressBar,
    val status: TextBlock,
    val back: Button,
    val forward: Button,
    val reload: Button,
    val home: Button,
    val go: Button,
    val webView: WebView2,
)

internal fun createWebView2BrowserShell(
    subtleButtonStyle: Any,
    selectedSurfaceBrush: Any,
    cardStrokeBrush: Any,
    transparentBrush: Any,
): WebView2BrowserShell {
    val titleBarDragRegion =
        Grid().apply {
            minWidth = 188.0
            setValue(Panel.backgroundProperty, transparentBrush)
        }
    val titleBar =
        TabView().apply {
            isAddTabButtonVisible = false
            tabWidthMode = TabViewWidthMode.SizeToContent
            canDragTabs = false
            canReorderTabs = false
            allowDropTabs = false
            canTearOutTabs = false
            horizontalAlignment = HorizontalAlignment.Stretch
            verticalAlignment = VerticalAlignment.Stretch
            tabStripFooter = titleBarDragRegion
            tabItems.add(
                TabViewItem().apply {
                    resources["TabViewItemHeaderBackgroundSelected"] = selectedSurfaceBrush
                    header = "Kotlin WinRT"
                    isClosable = false
                },
            )
        }

    val back = navigationButton(Symbol.Back, "Back", subtleButtonStyle).apply { isEnabled = false }
    val forward = navigationButton(Symbol.Forward, "Forward", subtleButtonStyle).apply { isEnabled = false }
    val reload = navigationButton(Symbol.Refresh, "Reload", subtleButtonStyle).apply { isEnabled = false }
    val home = navigationButton(Symbol.Home, "Home", subtleButtonStyle).apply { isEnabled = false }
    val go = navigationButton(Symbol.Go, "Go", subtleButtonStyle).apply { isEnabled = false }
    val address =
        TextBox().apply {
            minWidth = 120.0
            height = 34.0
            placeholderText = "Enter HTTP or HTTPS address"
            horizontalAlignment = HorizontalAlignment.Stretch
            verticalAlignment = VerticalAlignment.Center
            verticalContentAlignment = VerticalAlignment.Center
            margin = Thickness(4.0, 0.0, 4.0, 0.0)
            padding = Thickness(10.0, 5.0, 6.0, 5.0)
            setValue(Control.backgroundProperty, selectedSurfaceBrush)
            setValue(Control.borderBrushProperty, cardStrokeBrush)
            borderThickness = Thickness(1.0, 1.0, 1.0, 1.0)
            resources["TextControlBackground"] = selectedSurfaceBrush
            resources["TextControlBackgroundPointerOver"] = selectedSurfaceBrush
            resources["TextControlBackgroundFocused"] = selectedSurfaceBrush
            resources["TextControlBorderBrush"] = cardStrokeBrush
            resources["TextControlBorderBrushPointerOver"] = cardStrokeBrush
        }

    val toolbar =
        Grid().apply {
            padding = Thickness(8.0, 4.0, 8.0, 4.0)
            columnSpacing = 4.0
            columnDefinitions.add(autoColumn())
            columnDefinitions.add(autoColumn())
            columnDefinitions.add(autoColumn())
            columnDefinitions.add(autoColumn())
            columnDefinitions.add(starColumn())
            columnDefinitions.add(autoColumn())

            addToolbarControl(back, 0)
            addToolbarControl(forward, 1)
            addToolbarControl(reload, 2)
            addToolbarControl(home, 3)
            addToolbarControl(address, 4)
            addToolbarControl(go, 5)
        }

    val loading =
        ProgressBar().apply {
            isIndeterminate = true
            visibility = Visibility.Visible
            horizontalAlignment = HorizontalAlignment.Stretch
            verticalAlignment = VerticalAlignment.Center
            margin = Thickness(12.0, 2.0, 12.0, 6.0)
        }
    val status =
        TextBlock().apply {
            visibility = Visibility.Collapsed
            margin = Thickness(12.0, 2.0, 12.0, 6.0)
        }
    val statusHost =
        Grid().apply {
            children.add(loading)
            children.add(status)
        }
    val transparentBackground = webView2TransparentColor()
    val webView =
        WebView2().apply {
            defaultBackgroundColor = transparentBackground
            resources[WEBVIEW2_VISUAL_BRIDGE_BACKGROUND_RESOURCE] = selectedSurfaceBrush
        }
    val webViewHost =
        Border().apply {
            setValue(Border.backgroundProperty, selectedSurfaceBrush)
            setValue(Border.borderBrushProperty, cardStrokeBrush)
            borderThickness = Thickness(0.0, 1.0, 0.0, 0.0)
            child = webView
        }

    val contentSurface =
        Grid().apply {
            rowDefinitions.add(row(1.0, GridUnitType.Auto))
            rowDefinitions.add(row(1.0, GridUnitType.Auto))
            rowDefinitions.add(row(1.0, GridUnitType.Star))
            setValue(Panel.backgroundProperty, selectedSurfaceBrush)

            addRootControl(toolbar, 0)
            addRootControl(statusHost, 1)
            addRootControl(webViewHost, 2)
        }

    val root =
        Grid().apply {
            rowDefinitions.add(row(40.0, GridUnitType.Pixel))
            rowDefinitions.add(row(1.0, GridUnitType.Star))

            addRootControl(titleBar, 0)
            addRootControl(contentSurface, 1)
        }

    return WebView2BrowserShell(
        root = root,
        titleBar = titleBar,
        titleBarDragRegion = titleBarDragRegion,
        address = address,
        loading = loading,
        status = status,
        back = back,
        forward = forward,
        reload = reload,
        home = home,
        go = go,
        webView = webView,
    )
}

private fun navigationButton(symbol: Symbol, label: String, subtleButtonStyle: Any): Button =
    Button().apply {
        content =
            Viewbox().apply {
                width = 14.0
                height = 14.0
                child = SymbolIcon(symbol)
            }
        setValue(FrameworkElement.styleProperty, subtleButtonStyle)
        width = 34.0
        height = 34.0
        padding = Thickness(0.0, 0.0, 0.0, 0.0)
        horizontalContentAlignment = HorizontalAlignment.Center
        verticalContentAlignment = VerticalAlignment.Center
        verticalAlignment = VerticalAlignment.Center
        shadow = null
        ToolTipService.setToolTip(this, label)
        AutomationProperties.setName(this, label)
    }

internal fun webView2TransparentColor(): Color =
    Color(
        a = 0u.toUByte(),
        r = 0u.toUByte(),
        g = 0u.toUByte(),
        b = 0u.toUByte(),
    )

private fun autoColumn(): ColumnDefinition =
    ColumnDefinition().apply {
        width = GridLength(1.0, GridUnitType.Auto)
    }

private fun starColumn(): ColumnDefinition =
    ColumnDefinition().apply {
        width = GridLength(1.0, GridUnitType.Star)
    }

private fun row(value: Double, unit: GridUnitType): RowDefinition =
    RowDefinition().apply {
        height = GridLength(value, unit)
    }

private fun Grid.addToolbarControl(control: microsoft.ui.xaml.FrameworkElement, column: Int) {
    Grid.setColumn(control, column)
    children.add(control)
}

private fun Grid.addRootControl(control: microsoft.ui.xaml.FrameworkElement, row: Int) {
    Grid.setRow(control, row)
    children.add(control)
}

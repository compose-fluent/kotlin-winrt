package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.GridLength
import microsoft.ui.xaml.GridUnitType
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.VerticalAlignment
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ColumnDefinition
import microsoft.ui.xaml.controls.Grid
import microsoft.ui.xaml.controls.RowDefinition
import microsoft.ui.xaml.controls.Symbol
import microsoft.ui.xaml.controls.SymbolIcon
import microsoft.ui.xaml.controls.TabView
import microsoft.ui.xaml.controls.TabViewItem
import microsoft.ui.xaml.controls.TabViewWidthMode
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToolTipService
import microsoft.ui.xaml.controls.WebView2

internal const val WEBVIEW2_HOME_MIN_WIDTH = 720.0

internal fun webView2HomeVisibility(windowWidth: Double): Visibility =
    if (windowWidth >= WEBVIEW2_HOME_MIN_WIDTH) {
        Visibility.Visible
    } else {
        Visibility.Collapsed
    }

internal data class WebView2BrowserShell(
    val root: Grid,
    val titleBar: TabView,
    val address: TextBox,
    val status: TextBlock,
    val back: Button,
    val forward: Button,
    val reload: Button,
    val home: Button,
    val go: Button,
    val webView: WebView2,
)

internal fun createWebView2BrowserShell(): WebView2BrowserShell {
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
            tabItems.add(
                TabViewItem().apply {
                    header = "Kotlin WinRT"
                    isClosable = false
                },
            )
        }

    val back = navigationButton(Symbol.Back, "Back").apply { isEnabled = false }
    val forward = navigationButton(Symbol.Forward, "Forward").apply { isEnabled = false }
    val reload = navigationButton(Symbol.Refresh, "Reload").apply { isEnabled = false }
    val home = navigationButton(Symbol.Home, "Home").apply { isEnabled = false }
    val go = navigationButton(Symbol.Go, "Go").apply { isEnabled = false }
    val address =
        TextBox().apply {
            minWidth = 120.0
            placeholderText = "Enter HTTP or HTTPS address"
            horizontalAlignment = HorizontalAlignment.Stretch
            verticalAlignment = VerticalAlignment.Center
            margin = Thickness(4.0, 0.0, 4.0, 0.0)
        }

    val toolbar =
        Grid().apply {
            padding = Thickness(8.0, 4.0, 8.0, 4.0)
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

    val status =
        TextBlock().apply {
            text = "Loading embedded page..."
            visibility = Visibility.Visible
            margin = Thickness(12.0, 2.0, 12.0, 6.0)
        }
    val webView = WebView2()

    val root =
        Grid().apply {
            rowDefinitions.add(row(48.0, GridUnitType.Pixel))
            rowDefinitions.add(row(1.0, GridUnitType.Auto))
            rowDefinitions.add(row(1.0, GridUnitType.Auto))
            rowDefinitions.add(row(1.0, GridUnitType.Star))

            addRootControl(titleBar, 0)
            addRootControl(toolbar, 1)
            addRootControl(status, 2)
            addRootControl(webView, 3)
        }

    return WebView2BrowserShell(
        root = root,
        titleBar = titleBar,
        address = address,
        status = status,
        back = back,
        forward = forward,
        reload = reload,
        home = home,
        go = go,
        webView = webView,
    )
}

private fun navigationButton(symbol: Symbol, label: String): Button =
    Button().apply {
        content = SymbolIcon(symbol)
        width = 40.0
        height = 40.0
        verticalAlignment = VerticalAlignment.Center
        ToolTipService.setToolTip(this, label)
        AutomationProperties.setName(this, label)
    }

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

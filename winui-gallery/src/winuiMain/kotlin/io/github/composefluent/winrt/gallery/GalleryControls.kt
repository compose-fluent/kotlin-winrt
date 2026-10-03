package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.media.Brush
import microsoft.ui.xaml.media.SolidColorBrush
import microsoft.ui.xaml.media.imaging.BitmapImage
import windows.foundation.Uri
import windows.ui.Color

internal fun inset(all: Double) = Thickness(all, all, all, all)
internal fun corners(all: Double) = CornerRadius(all, all, all, all)
internal fun autoRow() = RowDefinition().apply { height = GridLength(1.0, GridUnitType.Auto) }
internal fun starRow() = RowDefinition().apply { height = GridLength(1.0, GridUnitType.Star) }
internal fun column(value: Double, unit: GridUnitType) = ColumnDefinition().apply { width = GridLength(value, unit) }
internal fun label(value: String, size: Double = 14.0) = TextBlock().apply {
    text = value
    fontSize = size
    textWrapping = TextWrapping.Wrap
}
internal fun glyph(value: String, size: Double = 16.0) = FontIcon().apply {
    glyph = value
    fontSize = size
}
internal fun picture(path: String, size: Double) = Image().apply {
    width = size
    height = size
    source = BitmapImage(Uri(path))
}
internal fun stack(spacing: Double = 12.0, horizontal: Boolean = false, content: StackPanel.() -> Unit = {}) = StackPanel().apply {
    this.spacing = spacing
    if (horizontal) orientation = Orientation.Horizontal
    content()
}
internal class ExamplePage : StackPanel() {
    internal fun example(title: String, sample: UIElement, options: UIElement? = null, output: UIElement? = null) {
        children.add(renderExample(title, sample, options, output))
    }
}

internal fun ExamplePage(block: ExamplePage.() -> Unit): ExamplePage = ExamplePage().apply(block)

internal fun Button(text: String) = Button().apply {
    content = text
}
internal fun Button(text: String, action: () -> Unit) = Button(text).apply {
    click.add { _, _ -> action() }
}
internal fun option(title: String, initial: Boolean = false, changed: (Boolean) -> Unit) = CheckBox().apply {
    content = title
    isChecked = initial
    click.add { _, _ -> changed(isChecked == true) }
}
internal fun choices(title: String, values: List<String>, initial: Int = 0, changed: (Int) -> Unit) = RadioButtons().apply {
    header = title
    values.forEach { items.add(it) }
    selectedIndex = initial
    selectionChanged.add { _, _ ->
        val index = selectedIndex
        // A selection transition can first remove the previous item (-1).
        if (index in values.indices) changed(index)
    }
}
internal fun rgb(value: UInt) = windows.ui.Color(255u, (value shr 16).toUByte(), (value shr 8).toUByte(), value.toUByte())
private fun argb(value: UInt) = Color((value shr 24).toUByte(), (value shr 16).toUByte(), (value shr 8).toUByte(), value.toUByte())
internal fun brush(value: UInt) = microsoft.ui.xaml.media.SolidColorBrush(rgb(value))
internal fun transparentBrush() = SolidColorBrush(Color(0u, 0u, 0u, 0u))
internal fun select(title: String, values: List<String>, initial: Int = 0, changed: (Int) -> Unit) = ComboBox().apply {
    header = title
    values.forEach { items.add(it) }
    selectedIndex = initial
    selectionChanged.add { _, _ -> if (selectedIndex >= 0) changed(selectedIndex) }
}
internal fun range(title: String, initial: Double, minimum: Double, maximum: Double, changed: (Double) -> Unit) = Slider().apply {
    width = 196.0
    header = title; this.minimum = minimum; this.maximum = maximum; value = initial
    valueChanged.add { _, _ -> changed(value) }
}
internal fun scroll(element: UIElement) = ScrollViewer().apply {
    content = element
    verticalScrollBarVisibility = ScrollBarVisibility.Auto
    horizontalScrollBarVisibility = ScrollBarVisibility.Disabled
    horizontalContentAlignment = HorizontalAlignment.Stretch
    verticalContentAlignment = VerticalAlignment.Top
}
// ThemeResource has no projected code object. Only code-drawn sample chrome
// needs explicit colors; control templates follow their parent's RequestedTheme.
// Values match WinUI's Common_themeresources_any.xaml (Default/Dark and Light).
private fun sampleChromeBrush(isDark: Boolean, dark: UInt, light: UInt, highContrastKey: String): Brush = SolidColorBrush(
    if (GalleryTheme.highContrast) GalleryTheme.resource(highContrastKey) as Color
    else argb(if (isDark) dark else light)
)
internal fun named(element: DependencyObject, value: String) {
    AutomationProperties.setName(element, value)
    AutomationProperties.setAutomationId(element, value)
}

// Legacy construction retained until each page migrates. Source and responsive
// behavior are shared with XAML pages through GalleryExampleBindings.
private fun renderExample(title: String, sample: UIElement, options: UIElement?, output: UIElement?): StackPanel = stack(0.0) exampleRoot@ {
    margin = Thickness(0.0, 16.0, 0.0, 0.0)
    children.add(label(title).apply {
        margin = Thickness(0.0, 12.0, 0.0, 12.0)
        fontWeight = windows.ui.text.FontWeight(600u)
        AutomationProperties.setHeadingLevel(this, microsoft.ui.xaml.automation.peers.AutomationHeadingLevel.Level3)
    })
    val outputPanel = output?.let { outputElement -> stack(8.0) {
        Grid.setColumn(this, 1)
        padding = inset(16.0)
        maxWidth = 320.0
        margin = Thickness(0.0, 12.0, 12.0, 12.0)
        cornerRadius = corners(8.0)
        children.add(label("Output:"))
        children.add(outputElement)
    } }
    val optionsPanel = options?.let { optionsElement -> Border().apply {
        Grid.setColumn(this, 2)
        padding = inset(16.0)
        maxWidth = 320.0
        borderThickness = Thickness(1.0, 0.0, 0.0, 0.0)
        cornerRadius = CornerRadius(0.0, 8.0, 0.0, 0.0)
        child = optionsElement
        bindExampleOptions(this@exampleRoot, this)
    } }
    val body = Grid().apply {
        cornerRadius = CornerRadius(8.0, 8.0, 0.0, 0.0)
        borderThickness = inset(1.0)
        rowDefinitions.add(starRow())
        rowDefinitions.add(autoRow())
        columnDefinitions.add(column(1.0, GridUnitType.Star))
        columnDefinitions.add(column(1.0, GridUnitType.Auto))
        columnDefinitions.add(column(1.0, GridUnitType.Auto))
        children.add(Border().apply {
            padding = inset(12.0)
            minHeight = 80.0
            child = sample
        })
        outputPanel?.let { children.add(it) }
        optionsPanel?.let { children.add(it) }
    }
    GalleryTheme.sampleBeingConstructed?.sampleBodies?.add(body)
    children.add(body)
    val sourceExpander = Expander().apply {
        header = "Source code"
        horizontalAlignment = HorizontalAlignment.Stretch
        horizontalContentAlignment = HorizontalAlignment.Stretch
        cornerRadius = CornerRadius(0.0, 0.0, 8.0, 8.0)
        bindExampleSource(this, title)
    }
    children.add(sourceExpander)
    fun updateChrome() {
        val dark = body.actualTheme == ElementTheme.Dark
        body.background = sampleChromeBrush(dark, 0xFF202020u, 0xFFF3F3F3u, "SystemColorWindowColor")
        body.borderBrush = sampleChromeBrush(dark, 0x19000000u, 0x0F000000u, "SystemColorWindowTextColor")
        outputPanel?.background = sampleChromeBrush(dark, 0xFF202020u, 0xFFF3F3F3u, "SystemColorWindowColor")
        optionsPanel?.background = sampleChromeBrush(dark, 0x0DFFFFFFu, 0xB3FFFFFFu, "SystemColorWindowColor")
        optionsPanel?.borderBrush = sampleChromeBrush(dark, 0x15FFFFFFu, 0x0F000000u, "SystemColorWindowTextColor")
    }
    body.actualThemeChanged.add { _, _ -> updateChrome() }
    GalleryTheme.observe(body, ::updateChrome)
    updateChrome()
}

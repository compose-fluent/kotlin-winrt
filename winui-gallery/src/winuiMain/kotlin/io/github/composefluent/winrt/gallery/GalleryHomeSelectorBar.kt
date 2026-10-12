package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.controls.*

/**
 * Code-only equivalent of WinUI Gallery's TokenViewSelectorBarStyle.
 *
 * The stock SelectorBar template is supplied by the platform resource
 * dictionaries.  Gallery's home page replaces that template with a token
 * (pill) template, so the two visual items are composed directly here to
 * keep the same geometry without embedding XAML in the application.
 */
internal class GalleryHomeSelectorBar(
    private val onSelectionChanged: (favoritesSelected: Boolean) -> Unit,
) : StackPanel() {
    private val recent = GalleryHomeSelectorPill("Recent", Symbol.Clock)
    private val favorites = GalleryHomeSelectorPill("Favorites", Symbol.Favorite)

    init {
        orientation = Orientation.Horizontal
        spacing = 8.0
        horizontalAlignment = HorizontalAlignment.Center
        verticalAlignment = VerticalAlignment.Top
        AutomationProperties.setName(this, "Home view selector")
        AutomationProperties.setAutomationId(this, "HomeViewSelectorBar")
        children!!.add(recent.view)
        children!!.add(favorites.view)
        recent.onSelected = { selectFavorites(false) }
        favorites.onSelected = { selectFavorites(true) }
        selectFavorites(false, notify = false)
    }

    private fun selectFavorites(value: Boolean, notify: Boolean = true) {
        recent.setSelected(!value)
        favorites.setSelected(value)
        if (notify) onSelectionChanged(value)
    }
}

/** One Gallery token/pill item, rendered without the platform SelectorBar template. */
private class GalleryHomeSelectorPill(
    private val title: String,
    symbol: Symbol,
) {
    internal val view = Border()
    private val icon = SymbolIcon(symbol)
    private val textBlock = TextBlock().apply {
        text = title
        fontSize = 14.0
        fontWeight = windows.ui.text.FontWeight(400u)
        isHitTestVisible = false
    }
    private var selected = false
    private var pointerOver = false
    private var pressed = false
    internal var onSelected: (() -> Unit)? = null

    init {
        view.padding = Thickness(23.0, 5.0, 23.0, 6.0)
        view.minHeight = 36.0
        view.cornerRadius = corners(16.0)
        view.borderThickness = inset(1.0)
        view.horizontalAlignment = HorizontalAlignment.Left
        view.verticalAlignment = VerticalAlignment.Center
        AutomationProperties.setName(view, title)
        AutomationProperties.setAutomationId(view, "HomeViewSelectorItem$title")
        icon.isHitTestVisible = false
        // SymbolIcon's native glyph has a small overhang.  Giving the glyph a
        // fixed 16x16 layout slot clips its right and bottom pixels on the
        // projected SelectorBar template, so scale it inside a Viewbox instead.
        val iconBox = Viewbox().apply {
            width = 16.0
            height = 16.0
            isHitTestVisible = false
            child = icon
        }
        view.child = stack(8.0, horizontal = true) {
            verticalAlignment = VerticalAlignment.Center
            children!!.add(iconBox)
            children!!.add(textBlock)
        }
        view.pointerEntered.add { _, _ -> pointerOver = true; updateVisuals() }
        view.pointerExited.add { _, _ -> pointerOver = false; pressed = false; updateVisuals() }
        view.pointerPressed.add { _, _ -> pressed = true; updateVisuals() }
        view.pointerReleased.add { _, _ -> pressed = false; updateVisuals() }
        view.tapped.add { _, args ->
            onSelected?.invoke()
            args!!.handled = true
        }
        view.actualThemeChanged.add { _, _ -> updateVisuals() }
        view.loaded.add { _, _ -> updateVisuals() }
        updateVisuals()
    }

    internal fun setSelected(value: Boolean) {
        selected = value
        updateVisuals()
    }

    private fun updateVisuals() {
        val backgroundResource = when {
            selected && pressed -> "AccentFillColorTertiaryBrush"
            selected && pointerOver -> "AccentFillColorSecondaryBrush"
            selected -> "AccentFillColorDefaultBrush"
            pressed || pointerOver -> "ControlFillColorSecondaryBrush"
            else -> "ControlFillColorDefaultBrush"
        }
        val foregroundResource = when {
            selected && pressed -> "TextOnAccentFillColorSecondaryBrush"
            selected -> "TextOnAccentFillColorPrimaryBrush"
            pressed -> "TextFillColorSecondaryBrush"
            else -> "TextFillColorPrimaryBrush"
        }
        val borderResource = when {
            selected && pressed -> "AccentFillColorTertiaryBrush"
            selected && pointerOver -> "AccentFillColorSecondaryBrush"
            selected -> "AccentFillColorDefaultBrush"
            else -> "ControlStrokeColorDefaultBrush"
        }
        val foregroundBrush = GalleryTheme.brush(foregroundResource)
        view.background = GalleryTheme.brush(backgroundResource)
        view.borderBrush = GalleryTheme.brush(borderResource)
        icon.foreground = foregroundBrush
        textBlock.foreground = foregroundBrush
    }
}

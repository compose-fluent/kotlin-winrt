// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.UserControl
import microsoft.ui.xaml.media.*
import microsoft.ui.composition.systembackdrops.MicaKind
import windows.applicationmodel.datatransfer.*

internal class ColorTile : UserControl() {
    private var ready = false
    var ColorName: String
        get() = getValue(ColorNameProperty) as? String ?: ""
        set(value) { setValue(ColorNameProperty, value) }
    var ColorExplanation: String
        get() = getValue(ColorExplanationProperty) as? String ?: ""
        set(value) { setValue(ColorExplanationProperty, value) }
    var ColorBrushName: String
        get() = getValue(ColorBrushNameProperty) as? String ?: ""
        set(value) { setValue(ColorBrushNameProperty, value) }
    var ShowSeparator: Boolean
        get() = getValue(ShowSeparatorProperty) as Boolean
        set(value) { setValue(ShowSeparatorProperty, value) }
    var Comment: Any?
        get() = getValue(CommentProperty)
        set(value) { setValue(CommentProperty, value) }
    var Backdrop: ColorTileBackdropKind
        get() = getValue(BackdropProperty) as ColorTileBackdropKind
        set(value) { setValue(BackdropProperty, value) }
    override fun initializeComponent() {
        super.initializeComponent()
        ready = true
        UpdateComment()
        ApplyBackdrop()
    }
    private fun UpdateComment() {
        if (ready) CommentHost.visibility = if (Comment == null) Visibility.Collapsed else Visibility.Visible
    }
    private fun ApplyBackdrop() {
        if (!ready) return
        BackdropHost.systemBackdrop = when (Backdrop) {
            ColorTileBackdropKind.None -> null
            ColorTileBackdropKind.Acrylic -> DesktopAcrylicBackdrop()
            ColorTileBackdropKind.Mica -> MicaBackdrop().apply { kind = MicaKind.Base }
            ColorTileBackdropKind.MicaAlt -> MicaBackdrop().apply { kind = MicaKind.BaseAlt }
        }
        BackdropHost.visibility = if (Backdrop == ColorTileBackdropKind.None) Visibility.Collapsed else Visibility.Visible
    }
    private fun CopyBrushNameButton_Click(sender: Any?, args: RoutedEventArgs) {
        Clipboard.setContent(DataPackage().apply { setText(ColorBrushName) })
    }
    companion object {
        val ColorNameProperty: DependencyProperty = DependencyProperty.register("ColorName", String::class, ColorTile::class, PropertyMetadata(""))
        val ColorExplanationProperty: DependencyProperty = DependencyProperty.register("ColorExplanation", String::class, ColorTile::class, PropertyMetadata(""))
        val ColorBrushNameProperty: DependencyProperty = DependencyProperty.register("ColorBrushName", String::class, ColorTile::class, PropertyMetadata(""))
        val ShowSeparatorProperty: DependencyProperty = DependencyProperty.register("ShowSeparator", Boolean::class, ColorTile::class, PropertyMetadata(true))
        val CommentProperty: DependencyProperty = DependencyProperty.register("Comment", Any::class, ColorTile::class, PropertyMetadata(null, { owner, _ -> owner.asWinRT<ColorTile>().UpdateComment() }))
        val BackdropProperty: DependencyProperty = DependencyProperty.register("Backdrop", ColorTileBackdropKind::class, ColorTile::class, PropertyMetadata(ColorTileBackdropKind.None, { owner, _ -> owner.asWinRT<ColorTile>().ApplyBackdrop() }))
    }
}

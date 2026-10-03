// Ported from WinUI Gallery Models/Category.cs (MIT).
package io.github.composefluent.winrt.gallery.models

import microsoft.ui.xaml.controls.Symbol

internal open class CategoryBase
internal class Category(val Name: String = "", val Tooltip: String = "", val Glyph: Symbol = Symbol.Home) : CategoryBase()
internal class Separator : CategoryBase()
internal class Header(val Name: String = "") : CategoryBase()

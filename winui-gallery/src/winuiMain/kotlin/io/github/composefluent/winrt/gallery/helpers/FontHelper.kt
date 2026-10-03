package io.github.composefluent.winrt.gallery.helpers

import microsoft.ui.xaml.media.FontFamily

internal object FontHelper {
    val Fonts: List<FontItem> = listOf("Arial", "Comic Sans MS", "Courier New", "Segoe UI", "Times New Roman").map {
        FontItem(it, FontFamily(it))
    }
}

internal class FontItem(val Name: String, val Font: FontFamily)

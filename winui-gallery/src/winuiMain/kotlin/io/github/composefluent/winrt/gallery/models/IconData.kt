// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.models

import io.github.composefluent.winrt.gallery.GalleryIcon
import io.github.composefluent.winrt.gallery.GallerySymbols

internal class IconData(icon: GalleryIcon) {
    val Name: String = icon.name
    val Code: String = icon.code
    val Character: String = icon.character
    val Tags: List<String> = icon.tags
    val IsSegoeFluentOnly: Boolean = icon.fluentOnly
    val CodeGlyph: String = "\\u$Code"
    val TextGlyph: String = "&#x$Code;"
    val SymbolName: String? = Name.takeIf { GallerySymbols.find(it) != null }
}

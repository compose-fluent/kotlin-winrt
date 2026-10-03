package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.UIElement

/** Shared contract; each target receives its implementation from KSP. */
internal expect object GalleryCatalog {
    val groups: List<GalleryGroup>
    val pages: List<GalleryPageInfo>
    val home: GalleryPageInfo
}

internal expect object GalleryPageFactories {
    fun create(route: String): UIElement?
    val routes: Set<String>
}

internal expect object GallerySymbols {
    fun find(name: String): microsoft.ui.xaml.controls.Symbol?
}

internal expect object GalleryCodeCatalog {
    fun document(route: String, title: String, index: Int): io.github.composefluent.winrt.gallery.code.KotlinCodeDocument?
    fun xamlDocument(route: String, title: String = "", index: Int = 0): io.github.composefluent.winrt.gallery.code.KotlinCodeDocument?
    fun sampleDefinition(path: String): GallerySampleCode?
    fun sourceDocument(path: String): io.github.composefluent.winrt.gallery.code.KotlinCodeDocument?
}

internal data class GallerySampleCode(
    val header: String,
    val kotlin: io.github.composefluent.winrt.gallery.code.KotlinCodeDocument,
    val xaml: io.github.composefluent.winrt.gallery.code.KotlinCodeDocument?,
)

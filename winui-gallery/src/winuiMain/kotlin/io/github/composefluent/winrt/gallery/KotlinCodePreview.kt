package io.github.composefluent.winrt.gallery
import io.github.composefluent.winrt.gallery.code.KotlinCodeDocument
import io.github.composefluent.winrt.gallery.controls.KotlinSourcePreview
import microsoft.ui.xaml.UIElement

/** Selection, scrolling and copy use the shared XAML source presenter. */
internal fun kotlinCodePreview(document: KotlinCodeDocument,xamlDocument: KotlinCodeDocument? = null): UIElement =
    KotlinSourcePreview(document,xamlDocument)

package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.controls.Border
import microsoft.ui.xaml.controls.Expander
import microsoft.ui.xaml.controls.Grid

/** Behavior shared by XAML examples and the remaining imperative pages during migration. */
internal fun bindExampleSource(source: Expander, title: String) {
    val state = GalleryTheme.sampleBeingConstructed
    val route = state?.sourceRoute
    val index = state?.sourceExampleIndex ?: 0
    if (state != null) state.sourceExampleIndex++
    var initialized = false
    source.expanding.add { _, _ ->
        if (!initialized) {
            val document = checkNotNull(route?.let { GalleryCodeCatalog.document(it, title, index) }) {
                "No source registered for $route example $index"
            }
            source.content = kotlinCodePreview(document, route?.let { GalleryCodeCatalog.xamlDocument(it, title, index) })
            initialized = true
        }
    }
}

internal fun bindExampleOptions(root: FrameworkElement, options: Border) {
    root.sizeChanged.add { _, _ ->
        val narrow = (root.xamlRoot?.size?.width ?: root.actualWidth.toFloat()) < 740f
        Grid.setColumn(options, if (narrow) 0 else 2)
        Grid.setColumnSpan(options, if (narrow) 3 else 1)
        Grid.setRow(options, if (narrow) 1 else 0)
        options.maxWidth = if (narrow) Double.POSITIVE_INFINITY else 320.0
        options.borderThickness = if (narrow) Thickness(0.0, 1.0, 0.0, 0.0) else Thickness(1.0, 0.0, 0.0, 0.0)
        options.margin = Thickness(0.0, if (narrow) 24.0 else 0.0, 0.0, 0.0)
    }
}

internal fun bindXamlExample(
    title: String,
    root: FrameworkElement,
    body: Grid,
    source: Expander,
    options: Border? = null,
) {
    GalleryTheme.sampleBeingConstructed?.sampleBodies?.add(body)
    bindExampleSource(source, title)
    options?.let { bindExampleOptions(root, it) }
}

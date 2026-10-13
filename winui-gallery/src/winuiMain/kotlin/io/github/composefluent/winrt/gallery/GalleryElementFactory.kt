package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
// #if WINUI3
import microsoft.ui.xaml.IElementFactory
// #endif
import microsoft.ui.xaml.controls.*

/** Native IElementFactory implementation for code-defined item content. */
// #if WINUI3
internal class GalleryElementFactory(private val render: (Any?) -> UIElement) : IElementFactory {
    override fun getElement(args: ElementFactoryGetArgs?): UIElement = ItemContainer().apply { requireNotNull(args);
        dataContext = args.data
        child = render(args.data)
    }

    override fun recycleElement(args: ElementFactoryRecycleArgs?) = Unit
}
// #endif

/** ItemsRepeater owns focus and interaction in the returned control itself. */
internal class GalleryRepeaterFactory(
    private val create: () -> FrameworkElement,
    private val bind: (FrameworkElement, Any?) -> Unit,
// #if WINUI3
) : IElementFactory {
    override fun getElement(args: ElementFactoryGetArgs?): UIElement { requireNotNull(args);
// #else
) : ElementFactory() {
    override fun getElementCore(args: ElementFactoryGetArgs?): UIElement { requireNotNull(args);
// #endif
        val element = create()
        bind(element, args.data)
        element.dataContext = args.data
        return element
    }

// #if WINUI3
    override fun recycleElement(args: ElementFactoryRecycleArgs?) = Unit
// #else
    override fun recycleElementCore(args: ElementFactoryRecycleArgs?) = Unit
// #endif
}

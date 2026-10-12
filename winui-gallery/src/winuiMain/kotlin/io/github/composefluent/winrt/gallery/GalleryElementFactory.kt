package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.IElementFactory
import microsoft.ui.xaml.controls.*

/** Native IElementFactory implementation for code-defined item content. */
internal class GalleryElementFactory(private val render: (Any?) -> UIElement) : IElementFactory {
    override fun getElement(args: ElementFactoryGetArgs?): UIElement = ItemContainer().apply { requireNotNull(args);
        dataContext = args.data
        child = render(args.data)
    }

    override fun recycleElement(args: ElementFactoryRecycleArgs?) = Unit
}

/** ItemsRepeater owns focus and interaction in the returned control itself. */
internal class GalleryRepeaterFactory(
    private val create: () -> FrameworkElement,
    private val bind: (FrameworkElement, Any?) -> Unit,
) : IElementFactory {
    override fun getElement(args: ElementFactoryGetArgs?): UIElement { requireNotNull(args);
        val element = create()
        bind(element, args.data)
        element.dataContext = args.data
        return element
    }

    override fun recycleElement(args: ElementFactoryRecycleArgs?) = Unit
}
